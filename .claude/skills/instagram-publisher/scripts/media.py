"""Image/video preparation: fit to Instagram ratios, gentle tone matching, text overlay, transcoding."""
from __future__ import annotations

import json
import re
import shutil
import subprocess
from pathlib import Path

from PIL import Image, ImageColor, ImageDraw, ImageEnhance, ImageFilter, ImageFont, ImageOps, features

from common import BRAND_DIR, die, load_config, read_json

IMAGE_EXT = {".jpg", ".jpeg", ".png", ".webp", ".heic", ".heif", ".bmp", ".tif", ".tiff", ".gif"}
VIDEO_EXT = {".mp4", ".mov", ".m4v", ".avi", ".mkv", ".webm"}
FONT_CANDIDATES = [
    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    "/System/Library/Fonts/Supplemental/Arial.ttf",
    "/Library/Fonts/Arial.ttf",
    "C:/Windows/Fonts/arial.ttf",
]
HEB = re.compile(r"[\u0590-\u05FF]")

try:  # HEIC from iPhones
    import pillow_heif  # type: ignore

    pillow_heif.register_heif_opener()
except Exception:
    pass


def kind_of(path: Path) -> str:
    ext = Path(path).suffix.lower()
    if ext in IMAGE_EXT:
        return "image"
    if ext in VIDEO_EXT:
        return "video"
    die(f"Unsupported file type: {path}")
    return ""


def parse_ratio(s: str) -> float:
    a, b = s.split(":")
    return float(a) / float(b)


# ------------------------------------------------------------------ fonts / text
def find_font(explicit: str | None = None) -> str | None:
    if explicit and Path(explicit).exists():
        return explicit
    fonts_dir = BRAND_DIR / "fonts"
    if fonts_dir.exists():
        found = sorted(list(fonts_dir.glob("*.ttf")) + list(fonts_dir.glob("*.otf")))
        if found:
            return str(found[0])
    for c in FONT_CANDIDATES:
        if Path(c).exists():
            return c
    return None


def load_font(size: int, explicit: str | None = None):
    path = find_font(explicit)
    if path:
        return ImageFont.truetype(path, size)
    return ImageFont.load_default(size)


def _ltr_tokens(text: str) -> list[str]:
    return re.findall(r"[A-Za-z0-9][A-Za-z0-9.,:%/@#_\-]*|\s+|.", text)


def shape(text: str) -> tuple[str, dict]:
    """Return (string, draw kwargs) that Pillow renders correctly for RTL text."""
    if not HEB.search(text):
        return text, {}
    if features.check("raqm"):
        return text, {"direction": "rtl", "language": "he"}
    try:
        from bidi.algorithm import get_display  # type: ignore

        return get_display(text), {}
    except ImportError:
        # Naive visual ordering: reverse tokens, keep Latin/number runs intact.
        return "".join(reversed(_ltr_tokens(text))), {}


def wrap(text: str, font, max_width: int) -> list[str]:
    """Greedy word wrap on logical text (measured with shaped width). Keeps explicit newlines."""
    probe = ImageDraw.Draw(Image.new("RGB", (1, 1)))
    lines: list[str] = []
    for para in text.split("\n"):
        cur = ""
        for word in para.split(" "):
            trial = f"{cur} {word}".strip()
            s, kw = shape(trial)
            if probe.textlength(s, font=font, **kw) <= max_width or not cur:
                cur = trial
            else:
                lines.append(cur)
                cur = word
        lines.append(cur)
    return lines


def draw_lines(draw, lines, font, x_left, x_right, y, fill, line_gap, align=None, stroke=0, stroke_fill=None):
    """Draw wrapped lines; RTL paragraphs are right aligned unless align says otherwise."""
    y_start = y
    for line in lines:
        s, kw = shape(line)
        w = draw.textlength(s, font=font, **kw)
        is_rtl = bool(HEB.search(line))
        a = align or ("right" if is_rtl else "left")
        x = x_right - w if a == "right" else (x_left + (x_right - x_left - w) / 2 if a == "center" else x_left)
        draw.text((x, y), s, font=font, fill=fill, stroke_width=stroke, stroke_fill=stroke_fill, **kw)
        y += font.size + line_gap
    return y - y_start


# ------------------------------------------------------------------ image stats / tone
def image_stats(img: Image.Image) -> dict:
    small = ImageOps.exif_transpose(img).convert("RGB")
    small.thumbnail((128, 128))
    gray = small.convert("L")
    hsv = small.convert("HSV")
    r, g, b = small.split()
    px = small.width * small.height
    mean = lambda im: sum(i * c for i, c in enumerate(im.histogram())) / px  # noqa: E731
    mean_l = mean(gray)
    var = sum(((i - mean_l) ** 2) * c for i, c in enumerate(gray.histogram())) / px
    return {
        "brightness": round(mean_l / 255, 4),
        "saturation": round(mean(hsv.split()[1]) / 255, 4),
        "contrast": round((var ** 0.5) / 255, 4),
        "warmth": round((mean(r) - mean(b)) / 255, 4),
    }


def match_tone(img: Image.Image, target: dict, strength: float) -> tuple[Image.Image, dict]:
    """Nudge brightness / saturation / warmth toward the feed average. Deliberately conservative."""
    cur = image_stats(img)
    applied = {}

    def factor(cur_v, tgt_v, lo=0.85, hi=1.2):
        if cur_v <= 0.01:
            return 1.0
        f = 1 + strength * (tgt_v / cur_v - 1)
        return max(lo, min(hi, f))

    fb = factor(cur["brightness"], target.get("brightness", cur["brightness"]))
    fs = factor(cur["saturation"], target.get("saturation", cur["saturation"]), 0.8, 1.25)
    fc = factor(cur["contrast"], target.get("contrast", cur["contrast"]), 0.9, 1.15)
    out = img
    if abs(fb - 1) > 0.01:
        out = ImageEnhance.Brightness(out).enhance(fb)
        applied["brightness"] = round(fb, 3)
    if abs(fs - 1) > 0.01:
        out = ImageEnhance.Color(out).enhance(fs)
        applied["saturation"] = round(fs, 3)
    if abs(fc - 1) > 0.01:
        out = ImageEnhance.Contrast(out).enhance(fc)
        applied["contrast"] = round(fc, 3)
    dw = (target.get("warmth", cur["warmth"]) - cur["warmth"]) * strength
    dw = max(-0.06, min(0.06, dw))
    if abs(dw) > 0.005:
        r, g, b = out.split()
        r = r.point(lambda v: max(0, min(255, int(v * (1 + dw)))))
        b = b.point(lambda v: max(0, min(255, int(v * (1 - dw)))))
        out = Image.merge("RGB", (r, g, b))
        applied["warmth_shift"] = round(dw, 3)
    return out, applied


# ------------------------------------------------------------------ image preparation
def fit_image(img: Image.Image, ratio: float, size_w: int, fit: str, focus: str, pad_color: str) -> Image.Image:
    target_w = size_w
    target_h = round(size_w / ratio)
    if fit == "cover":
        fx = {"left": 0.0, "right": 1.0}.get(focus.split("-")[-1], 0.5)
        fy = {"top": 0.0, "bottom": 1.0}.get(focus.split("-")[0], 0.5)
        return ImageOps.fit(img, (target_w, target_h), Image.LANCZOS, centering=(fx, fy))
    fg = ImageOps.contain(img, (target_w, target_h), Image.LANCZOS)
    if fit == "blur":
        bg = ImageOps.fit(img, (target_w, target_h), Image.LANCZOS).filter(ImageFilter.GaussianBlur(40))
        bg = ImageEnhance.Brightness(bg).enhance(0.7)
    else:
        bg = Image.new("RGB", (target_w, target_h), ImageColor.getrgb(pad_color))
    bg.paste(fg, ((target_w - fg.width) // 2, (target_h - fg.height) // 2))
    return bg


def overlay_text(img: Image.Image, text: str, position: str = "bottom", color: str = "#ffffff",
                 font_path: str | None = None, size_pct: float = 5.5, box: str | None = None,
                 margin_pct: float = 6.0, stroke: int = 0, align: str | None = None) -> Image.Image:
    """Draw text on the image. position: top | center | bottom. box = hex colour (optionally with alpha 'rrggbbaa')."""
    img = img.convert("RGBA")
    w, h = img.size
    font = load_font(max(12, int(w * size_pct / 100)), font_path)
    margin = int(w * margin_pct / 100)
    lines = wrap(text, font, w - 2 * margin)
    gap = int(font.size * 0.25)
    text_h = len(lines) * (font.size + gap) - gap
    y = {"top": margin, "center": (h - text_h) // 2}.get(position, h - margin - text_h)
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    if box:
        c = ImageColor.getrgb(box if box.startswith("#") else f"#{box}")
        if len(c) == 3:
            c = (*c, 170)
        pad = int(font.size * 0.5)
        d.rounded_rectangle([margin - pad, y - pad, w - margin + pad, y + text_h + pad], radius=pad, fill=c)
    draw_lines(d, lines, font, margin, w - margin, y, color, gap, align, stroke, "#000000" if stroke else None)
    return Image.alpha_composite(img, layer).convert("RGB")


def prepare_image(src: Path, out: Path, ratio: str | None = None, fit: str = "cover", focus: str = "center",
                  tone: bool = True, strength: float | None = None, width: int = 1080, pad_color: str = "#000000",
                  text: str | None = None, **text_opts) -> dict:
    cfg = load_config()
    ratio_s = ratio or cfg["default_ratio"]
    r = parse_ratio(ratio_s)
    if not 0.8 <= r <= 1.91:
        die(f"Ratio {ratio_s} is outside Instagram's feed range (4:5 .. 1.91:1). Use 4:5, 1:1 or 1.91:1.")
    img = ImageOps.exif_transpose(Image.open(src))
    if img.mode in ("RGBA", "LA", "P"):
        flat = Image.new("RGB", img.size, (255, 255, 255))
        rgba = img.convert("RGBA")
        flat.paste(rgba, mask=rgba.split()[-1])
        img = flat
    img = img.convert("RGB")
    img = fit_image(img, r, width, fit, focus, pad_color)
    report: dict = {"source": str(src), "ratio": ratio_s, "size": list(img.size)}
    data = read_json(BRAND_DIR / "style-data.json", {}) if tone else {}
    target = (data or {}).get("tone")
    if tone and target:
        img, applied = match_tone(img, target, cfg["tone_strength"] if strength is None else strength)
        report["tone_adjustments"] = applied
    if text:
        img = overlay_text(img, text, **text_opts)
        report["text_overlay"] = True
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out, "JPEG", quality=92, optimize=True, subsampling=0)
    report["output"] = str(out)
    return report


# ------------------------------------------------------------------ video
def _run(cmd: list[str]) -> subprocess.CompletedProcess:
    return subprocess.run(cmd, capture_output=True, text=True)


def need_ffmpeg() -> None:
    if not shutil.which("ffmpeg") or not shutil.which("ffprobe"):
        die("ffmpeg/ffprobe are required for video. Install ffmpeg (e.g. `brew install ffmpeg` / `apt install ffmpeg`).")


def probe_video(path: Path) -> dict:
    need_ffmpeg()
    p = _run(["ffprobe", "-v", "error", "-print_format", "json", "-show_format", "-show_streams", str(path)])
    if p.returncode:
        die(f"ffprobe failed for {path}: {p.stderr.strip()[:200]}")
    info = json.loads(p.stdout)
    v = next((s for s in info["streams"] if s["codec_type"] == "video"), None)
    a = next((s for s in info["streams"] if s["codec_type"] == "audio"), None)
    if not v:
        die(f"{path} has no video stream")
    rot = 0
    for sd in v.get("side_data_list", []):
        rot = int(sd.get("rotation", 0)) or rot
    w, h = int(v["width"]), int(v["height"])
    if abs(rot) in (90, 270):
        w, h = h, w
    return {
        "width": w, "height": h, "duration": float(info["format"].get("duration", 0)),
        "vcodec": v.get("codec_name"), "acodec": a.get("codec_name") if a else None,
        "size_mb": round(int(info["format"].get("size", 0)) / 1e6, 1),
        "container": info["format"].get("format_name", ""),
    }


def prepare_video(src: Path, out: Path, ratio: str = "9:16", fit: str = "cover", force: bool = False) -> dict:
    """Make a Reels-compatible MP4 (H.264 + AAC, yuv420p, faststart). Re-encodes only when needed."""
    info = probe_video(src)
    rw, rh = (int(x) for x in ratio.split(":"))
    target_ratio = rw / rh
    cur_ratio = info["width"] / info["height"]
    compliant = (
        not force
        and info["vcodec"] == "h264" and info["acodec"] in ("aac", None)
        and "mp4" in info["container"] and abs(cur_ratio - target_ratio) < 0.01
        and info["width"] <= 1920 and src.suffix.lower() == ".mp4"
    )
    out.parent.mkdir(parents=True, exist_ok=True)
    if compliant:
        shutil.copyfile(src, out)
        return {"source": str(src), "output": str(out), "transcoded": False, **info}
    tw = 1080 if target_ratio <= 1 else 1920
    th = round(tw / target_ratio / 2) * 2
    if fit == "cover":
        vf = f"scale={tw}:{th}:force_original_aspect_ratio=increase,crop={tw}:{th},setsar=1"
    else:
        vf = (f"scale={tw}:{th}:force_original_aspect_ratio=decrease,"
              f"pad={tw}:{th}:(ow-iw)/2:(oh-ih)/2:color=black,setsar=1")
    cmd = ["ffmpeg", "-y", "-i", str(src), "-vf", vf, "-r", "30", "-c:v", "libx264", "-preset", "medium",
           "-crf", "20", "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "128k", "-ar", "44100",
           "-movflags", "+faststart", str(out)]
    p = _run(cmd)
    if p.returncode:
        die(f"ffmpeg failed: {p.stderr.strip()[-400:]}")
    return {"source": str(src), "output": str(out), "transcoded": True, **probe_video(out)}


def video_frames_sheet(path: Path, out: Path, n: int = 8) -> dict:
    """Evenly spaced frames in one contact sheet so the video can be 'watched' as a single image."""
    info = probe_video(path)
    d = max(info["duration"], 0.1)
    tmp = out.parent / f".frames-{out.stem}"
    tmp.mkdir(parents=True, exist_ok=True)
    frames = []
    for i in range(n):
        t = d * (i + 0.5) / n
        f = tmp / f"f{i:02d}.jpg"
        p = _run(["ffmpeg", "-y", "-ss", f"{t:.2f}", "-i", str(path), "-frames:v", "1", "-vf", "scale=360:-2", str(f)])
        if p.returncode == 0 and f.exists():
            frames.append(Image.open(f).convert("RGB"))
    if not frames:
        die("Could not extract frames")
    cols = 4 if len(frames) > 4 else len(frames)
    rows = -(-len(frames) // cols)
    fh = max(f.height for f in frames)
    sheet = Image.new("RGB", (cols * 360, rows * fh), "#111111")
    for i, f in enumerate(frames):
        sheet.paste(f, ((i % cols) * 360, (i // cols) * fh))
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out, "JPEG", quality=85)
    shutil.rmtree(tmp, ignore_errors=True)
    return {"sheet": str(out), "frames": len(frames), **info}


def extract_cover(path: Path, out: Path, at_seconds: float) -> Path:
    p = _run(["ffmpeg", "-y", "-ss", f"{at_seconds:.2f}", "-i", str(path), "-frames:v", "1", "-q:v", "2", str(out)])
    if p.returncode:
        die(f"Could not extract cover: {p.stderr.strip()[-200:]}")
    return out


def inspect(path: Path) -> dict:
    k = kind_of(path)
    if k == "video":
        return {"file": str(path), "type": "video", **probe_video(path)}
    img = ImageOps.exif_transpose(Image.open(path))
    return {"file": str(path), "type": "image", "width": img.width, "height": img.height,
            "ratio": round(img.width / img.height, 3), "format": img.format, "stats": image_stats(img)}
