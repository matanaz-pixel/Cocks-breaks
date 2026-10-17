"""Render a story (see content/bank.json) into a vertical 1080x1920 MP4.

Each beat is voiced separately, so its graphic lines up exactly with its narration.
Captions are word-level and burned in, with the spoken word highlighted.
"""
import math
import re
import subprocess
import wave
from functools import lru_cache
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

from . import brand as B
from .music import SR, bed

GAP = 0.18          # silence between beats, seconds
TAIL = 0.8          # hold after the last beat
CENTER_Y = 760      # vertical centre of the beat graphic
CAPTION_Y = 1330    # vertical centre of the caption line (clear of the Shorts UI at the bottom)
MUSIC_GAIN = 0.07


# ---------- text helpers ----------

@lru_cache(maxsize=None)
def font(weight, size):
    return ImageFont.truetype(B.font_path(weight), size)


def text_w(draw, s, f):
    return draw.textlength(s, font=f)


def wrap(draw, text, f, max_w):
    lines, cur = [], ""
    for word in text.split():
        trial = f"{cur} {word}".strip()
        if text_w(draw, trial, f) <= max_w or not cur:
            cur = trial
        else:
            lines.append(cur)
            cur = word
    if cur:
        lines.append(cur)
    return lines


def fit_font(draw, text, weight, max_w, max_lines, start, floor=56):
    size = start
    while size > floor:
        f = font(weight, size)
        if len(wrap(draw, text, f, max_w)) <= max_lines and all(
                text_w(draw, w, f) <= max_w for w in text.split()):
            return f
        size -= 6
    return font(weight, floor)


def draw_rich_line(draw, cx, y, line, f, base=B.PAPER, stroke=0):
    """Centered line; fully upper-case words (2+ letters) or numbers with $ are drawn in gold."""
    x = cx - text_w(draw, line, f) / 2
    for i, word in enumerate(line.split(" ")):
        token = word + (" " if i < len(line.split(" ")) - 1 else "")
        letters = re.sub(r"[^A-Za-z]", "", word)
        hot = (len(letters) >= 2 and letters.isupper()) or "$" in word
        draw.text((x, y), token, font=f, fill=B.GOLD if hot else base,
                  stroke_width=stroke, stroke_fill=B.INK)
        x += text_w(draw, token, f)


# ---------- easing ----------

def ease_out(t):
    t = min(max(t, 0.0), 1.0)
    return 1 - (1 - t) ** 3


def ease_out_back(t):
    t = min(max(t, 0.0), 1.0)
    c = 1.70158
    return 1 + (c + 1) * (t - 1) ** 3 + c * (t - 1) ** 2


# ---------- background ----------

class Background:
    def __init__(self, seed=0):
        rng = np.random.default_rng(seed)
        y = np.linspace(0, 1, B.H)[:, None]
        x = np.linspace(0, 1, B.W)[None, :]
        glow = np.exp(-(((x - 0.5) ** 2) / 0.18 + ((y - 0.40) ** 2) / 0.10))
        base = np.array(B.INK, dtype=np.float32)
        tint = np.array([28, 44, 34], dtype=np.float32)
        img = base + glow[..., None] * tint
        vignette = 1 - 0.55 * (((x - 0.5) ** 2 + (y - 0.5) ** 2) / 0.5)
        img *= vignette[..., None]
        self.base = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGB")
        self.grain = [Image.fromarray(rng.integers(0, 18, (B.H // 2, B.W // 2), dtype=np.uint8), "L")
                      .resize((B.W, B.H)) for _ in range(4)]
        # Faint drifting grid, drawn once on a taller canvas and scrolled.
        grid = Image.new("L", (B.W, B.H + 120), 0)
        g = ImageDraw.Draw(grid)
        for gx in range(0, B.W, 120):
            g.line([(gx, 0), (gx, B.H + 120)], fill=16, width=2)
        for gy in range(0, B.H + 120, 120):
            g.line([(0, gy), (B.W, gy)], fill=16, width=2)
        self.grid = grid

    def frame(self, t, pulse=0.0):
        img = self.base.copy()
        off = int((t * 20) % 120)
        layer = self.grid.crop((0, off, B.W, off + B.H))
        img.paste(B.GOLD, (0, 0), layer)
        img.paste((255, 255, 255), (0, 0), self.grain[int(t * 12) % 4].point(lambda v: v // 3))
        if pulse > 0:
            img = Image.blend(img, Image.new("RGB", img.size, (40, 52, 44)), min(pulse, 1) * 0.5)
        return img


# ---------- beat graphics ----------

def fmt_number(value, show):
    decimals = 0 if float(show["value"]).is_integer() else len(str(show["value"]).split(".")[1])
    num = f"{value:,.{decimals}f}"
    return f"{show.get('prefix', '')}{num}{show.get('suffix', '')}"


def draw_scene(show, t, dur):
    """Returns an RGBA layer (W x 900) for the beat at time t into it."""
    layer = Image.new("RGBA", (B.W, 900), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    cx, cy = B.W // 2, 450
    kind = show["type"]
    appear = ease_out_back(t / 0.35)

    if kind == "title":
        f = fit_font(d, show["text"], "display", 900, 3, 150)
        lines = wrap(d, show["text"], f, 900)
        lh = f.size * 1.08
        y0 = cy - lh * len(lines) / 2
        for i, line in enumerate(lines):
            draw_rich_line(d, cx, y0 + i * lh, line, f)
    elif kind == "year":
        f = font("display", 300)
        s = str(show["value"])
        start = show["value"] - 40
        shown = int(start + (show["value"] - start) * ease_out(t / 0.7))
        s = str(shown)
        d.text((cx - text_w(d, s, f) / 2, cy - 230), s, font=f, fill=B.GOLD)
        lf = fit_font(d, show["label"], "bold", 900, 2, 72)
        for i, line in enumerate(wrap(d, show["label"], lf, 900)):
            d.text((cx - text_w(d, line, lf) / 2, cy + 120 + i * lf.size * 1.15), line, font=lf, fill=B.PAPER)
    elif kind == "number":
        val = show["value"] * ease_out(t / 0.9)
        s = fmt_number(val, show)
        final = fmt_number(show["value"], show)
        f = fit_font(d, final, "display", 960, 1, 280, floor=120)
        d.text((cx - text_w(d, s, f) / 2, cy - f.size * 0.8), s, font=f, fill=B.GOLD)
        lf = fit_font(d, show["label"], "bold", 900, 2, 72)
        for i, line in enumerate(wrap(d, show["label"], lf, 900)):
            d.text((cx - text_w(d, line, lf) / 2, cy + 110 + i * lf.size * 1.15), line, font=lf, fill=B.PAPER)
    elif kind == "text":
        f = fit_font(d, show["text"], "extrabold", 900, 3, 128)
        lines = wrap(d, show["text"], f, 900)
        lh = f.size * 1.12
        y0 = cy - lh * len(lines) / 2
        for i, line in enumerate(lines):
            draw_rich_line(d, cx, y0 + i * lh, line, f)
    elif kind == "versus":
        _card(d, cx, cy - 190, show["left"], show["left_label"], B.LOSS, ease_out(t / 0.4))
        second = ease_out((t - dur * 0.35) / 0.4)
        if second > 0:
            vs = font("black", 64)
            d.text((cx - text_w(d, "VS", vs) / 2, cy - 30), "VS", font=vs, fill=B.MUTED)
            _card(d, cx, cy + 230, show["right"], show["right_label"], B.GAIN, second)
    elif kind == "end":
        f = fit_font(d, show["text"], "display", 900, 2, 140)
        lines = wrap(d, show["text"], f, 900)
        lh = f.size * 1.08
        y0 = cy - 120 - lh * len(lines) / 2
        for i, line in enumerate(lines):
            draw_rich_line(d, cx, y0 + i * lh, line, f)
        pulse = 1 + 0.04 * math.sin(t * 6)
        bw, bh = 520 * pulse, 130 * pulse
        d.rounded_rectangle([cx - bw / 2, cy + 140, cx + bw / 2, cy + 140 + bh], radius=bh / 2, fill=B.GOLD)
        bf = font("black", 64)
        d.text((cx - text_w(d, "FOLLOW", bf) / 2, cy + 140 + bh / 2 - 40), "FOLLOW", font=bf, fill=B.INK)
    else:
        raise ValueError(f"unknown show type {kind}")

    if appear < 1:
        scale = 0.82 + 0.18 * appear
        w, h = layer.size
        small = layer.resize((max(1, int(w * scale)), max(1, int(h * scale))), Image.BILINEAR)
        out = Image.new("RGBA", layer.size, (0, 0, 0, 0))
        out.paste(small, ((w - small.width) // 2, (h - small.height) // 2))
        alpha = out.getchannel("A").point(lambda a: int(a * min(1, max(0, appear))))
        out.putalpha(alpha)
        layer = out
    return layer


def _card(d, cx, cy, value, label, color, k):
    if k <= 0:
        return
    w, h = 820, 300
    x0, y0 = cx - w / 2, cy - h / 2 + (1 - k) * 60
    d.rounded_rectangle([x0, y0, x0 + w, y0 + h], radius=36, fill=B.INK_2 + (235,), outline=color, width=5)
    vf = fit_font(d, value, "display", w - 80, 1, 170, floor=90)
    d.text((cx - text_w(d, value, vf) / 2, y0 + 30), value, font=vf, fill=color)
    lf = font("bold", 54)
    d.text((cx - text_w(d, label, lf) / 2, y0 + h - 90), label, font=lf, fill=B.PAPER)


# ---------- captions ----------

def caption_chunks(words, max_words=3):
    chunks, cur = [], []
    for w in words:
        cur.append(w)
        if len(cur) >= max_words or re.search(r"[.,!?;:]$", w[0]):
            chunks.append(cur)
            cur = []
    if cur:
        chunks.append(cur)
    return chunks


def draw_caption(img, chunk, t):
    d = ImageDraw.Draw(img)
    f = font("black", 86)
    words = [re.sub(r"[\"“”]", "", w[0]).upper() for w in chunk]
    line = " ".join(words)
    while text_w(d, line, f) > 960 and f.size > 50:
        f = font("black", f.size - 6)
    x = B.W / 2 - text_w(d, line, f) / 2
    for (word, start, end), label in zip(chunk, words):
        token = label + " "
        color = B.GOLD if start <= t < end + 0.05 else B.PAPER
        d.text((x, CAPTION_Y - f.size / 2), token, font=f, fill=color, stroke_width=9, stroke_fill=(0, 0, 0))
        x += text_w(d, token, f)


# ---------- header ----------

def draw_header(img, t, total, episode):
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, B.W, 12], fill=(40, 46, 42))
    d.rectangle([0, 0, int(B.W * min(t / total, 1)), 12], fill=B.GOLD)
    f = font("extrabold", 40)
    label = B.SERIES_LABEL
    if episode:
        label += f"  #{episode}"
    d.text((B.W / 2 - text_w(d, label, f) / 2, 150), label, font=f, fill=B.MUTED)
    d.line([(B.W / 2 - 60, 214), (B.W / 2 + 60, 214)], fill=B.GOLD, width=5)


# ---------- assembly ----------

def read_wav(path):
    with wave.open(str(path)) as w:
        data = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(np.float32) / 32768
        if w.getnchannels() == 2:
            data = data.reshape(-1, 2).mean(axis=1)
        assert w.getframerate() == SR, "voice must be 44.1 kHz"
    return data


def write_wav(path, data):
    pcm = (np.clip(data, -1, 1) * 32767).astype(np.int16)
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


def voice_story(story, voice, work):
    """Synthesize each beat; returns a timeline of beats with absolute times."""
    timeline, t = [], 0.25
    clips = []
    for i, beat in enumerate(story["beats"]):
        wav, dur, words = voice.speak(beat["say"], Path(work) / f"beat{i:02d}.wav")
        clips.append((t, read_wav(wav)))
        timeline.append({
            "start": t, "end": t + dur, "show": beat["show"],
            "words": [(w, t + s, t + e) for w, s, e in words],
        })
        t += dur + GAP
    total = t - GAP + TAIL
    audio = np.zeros(int(total * SR) + SR)
    for start, clip in clips:
        i = int(start * SR)
        audio[i:i + len(clip)] += clip
    audio = audio[: int(total * SR)]
    return timeline, total, audio


def render_story(story, voice, out_path, work, episode=None, seed=0):
    work = Path(work)
    work.mkdir(parents=True, exist_ok=True)
    timeline, total, speech = voice_story(story, voice, work)
    music = bed(total, seed=seed)[: len(speech)]
    mix = 0.95 * speech + MUSIC_GAIN * music
    mix_path = work / "mix.wav"
    write_wav(mix_path, mix / max(1.0, np.max(np.abs(mix))))

    chunks = [c for beat in timeline for c in caption_chunks(beat["words"])]
    bg = Background(seed)
    n_frames = int(total * B.FPS)
    cmd = ["ffmpeg", "-y", "-loglevel", "error",
           "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{B.W}x{B.H}", "-r", str(B.FPS), "-i", "-",
           "-i", str(mix_path),
           "-c:v", "libx264", "-preset", "medium", "-crf", "21", "-pix_fmt", "yuv420p",
           "-c:a", "aac", "-b:a", "160k", "-shortest", "-movflags", "+faststart", str(out_path)]
    proc = subprocess.Popen(cmd, stdin=subprocess.PIPE)
    beat_i = 0
    for n in range(n_frames):
        t = n / B.FPS
        while beat_i + 1 < len(timeline) and t >= timeline[beat_i + 1]["start"] - GAP / 2:
            beat_i += 1
        beat = timeline[beat_i]
        local = max(0.0, t - beat["start"])
        frame = bg.frame(t, pulse=max(0.0, 1 - local / 0.15) if beat_i > 0 else 0)
        draw_header(frame, t, total, episode)
        scene = draw_scene(beat["show"], local, beat["end"] - beat["start"])
        frame.paste(scene, (0, CENTER_Y - 450), scene)
        chunk = next((c for c in chunks if c[0][1] - 0.05 <= t < c[-1][2] + 0.12), None)
        if chunk:
            draw_caption(frame, chunk, t)
        proc.stdin.write(frame.tobytes())
    proc.stdin.close()
    if proc.wait() != 0:
        raise RuntimeError("ffmpeg failed")
    return {"path": str(out_path), "seconds": round(total, 1), "frames": n_frames}
