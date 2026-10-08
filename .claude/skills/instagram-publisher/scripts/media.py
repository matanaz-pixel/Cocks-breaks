"""Image/video preparation: fit to Instagram ratios, gentle tone matching, text overlay, transcoding."""
from __future__ import annotations

import json
import re
import shutil
import subprocess
from pathlib import Path

from PIL import Image, ImageChops, ImageColor, ImageDraw, ImageEnhance, ImageFilter, ImageFont, ImageOps, features

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
def find_font(explicit: str | None = None, use_brand: bool = True) -> str | None:
    if explicit and Path(explicit).exists():
        return explicit
    fonts_dir = BRAND_DIR / "fonts"
    if use_brand and fonts_dir.exists():
        found = sorted(list(fonts_dir.glob("*.ttf")) + list(fonts_dir.glob("*.otf")))
        if found:
            return str(found[0])
    for c in FONT_CANDIDATES:
        if Path(c).exists():
            return c
    return None


def load_font(size: int, explicit: str | None = None, use_brand: bool = True):
    path = find_font(explicit, use_brand)
    if path:
        font = ImageFont.truetype(path, size)
        weight = load_config().get("font_weight")
        if weight and use_brand:  # variable fonts: pick the stroke weight that matches the feed
            try:
                font.set_variation_by_axes([weight])
            except Exception:
                pass
        return font
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
def parse_focus(focus: str) -> tuple[float, float]:
    """'center' | 'top' | 'bottom-left' … or numeric 'x,y' in 0..1 (0,0 = top-left of the photo)."""
    if "," in focus:
        x, y = (max(0.0, min(1.0, float(v))) for v in focus.split(","))
        return x, y
    fx = {"left": 0.0, "right": 1.0}.get(focus.split("-")[-1], 0.5)
    fy = {"top": 0.0, "bottom": 1.0}.get(focus.split("-")[0], 0.5)
    return fx, fy


def fit_image(img: Image.Image, ratio: float, size_w: int, fit: str, focus: str, pad_color: str) -> Image.Image:
    target_w = size_w
    target_h = round(size_w / ratio)
    if fit == "cover":
        return ImageOps.fit(img, (target_w, target_h), Image.LANCZOS, centering=parse_focus(focus))
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


DEFAULT_GRADE = {"gamma": 0.93, "black_lift": 10, "white_cap": 250, "warm_r": 1.025, "warm_g": 0.99,
                 "warm_b": 0.955, "saturation": 0.96, "contrast": 0.97, "glow": 0.14}


def brand_grade(img: Image.Image, grade: dict | None = None, strength: float = 1.0) -> Image.Image:
    """Soft, warm 'cream & blush' grade: open the mid-tones (faces), lift blacks slightly, warm the whites,
    calm saturation, add a faint glow. Subtle by design — the subject's real colours stay."""
    g = {**DEFAULT_GRADE, **(grade or {})}
    src = img.convert("RGB")
    lut = [round(255 * ((i / 255) ** g["gamma"])) for i in range(256)]
    lut = [round(g["black_lift"] + v * (g["white_cap"] - g["black_lift"]) / 255) for v in lut]
    out = src.point(lut * 3)
    r, gr, b = out.split()
    r = r.point(lambda v: min(255, round(v * g["warm_r"])))
    gr = gr.point(lambda v: min(255, round(v * g["warm_g"])))
    b = b.point(lambda v: min(255, round(v * g["warm_b"])))
    out = Image.merge("RGB", (r, gr, b))
    out = ImageEnhance.Color(out).enhance(g["saturation"])
    out = ImageEnhance.Contrast(out).enhance(g["contrast"])
    if g["glow"] > 0:
        blur = out.filter(ImageFilter.GaussianBlur(radius=max(out.size) * 0.012))
        out = Image.blend(out, ImageChops.screen(out, blur), g["glow"])
    return src if strength <= 0 else Image.blend(src, out, min(1.0, strength))


def crop_poi(photo: Image.Image, win_w: int, win_h: int, zoom: float, focus: str) -> Image.Image:
    """Zoom `zoom`x into the point of interest `focus` (x,y in 0..1 of the photo), filling a win_w x win_h window."""
    fx, fy = parse_focus(focus)
    scale = max(win_w / photo.width, win_h / photo.height)
    cw, ch = win_w / scale / zoom, win_h / scale / zoom
    x0 = min(max(fx * photo.width - cw / 2, 0), photo.width - cw)
    y0 = min(max(fy * photo.height - ch / 2, 0), photo.height - ch)
    return photo.crop((round(x0), round(y0), round(x0 + cw), round(y0 + ch))).resize((win_w, win_h), Image.LANCZOS)


def slice_tiles(path: Path, n: int) -> list[Path]:
    """Cut a wide image into n equal tiles (continuous grid series). Returns the tile paths."""
    img = Image.open(path)
    tw = img.width // n
    out = []
    for i in range(n):
        t = path.with_name(f"{path.stem}-t{i + 1}.jpg")
        img.crop((i * tw, 0, (i + 1) * tw, img.height)).save(t, "JPEG", quality=92, subsampling=0)
        out.append(t)
    return out


def compose_split(photo: Image.Image, ratio: float, width: int, text: str, panel_pct: float = 42,
                  panel_side: str = "left", panel_color: str = "#F7F0EC", ink: str = "#111111",
                  bar_color: str | None = "#C8A07A", bar_pct: float = 2.2, band_color: str | None = None,
                  band_pct: float = 0.8, focus: str = "center", font_path: str | None = None,
                  zoom: float = 1.0, align: str = "center", ink_thin: int = 0) -> Image.Image:
    """Cream text panel + full-height photo + thin colour bar — the feed's signature layout."""
    H = round(width / ratio)
    bar_h = round(H * bar_pct / 100) if bar_color else 0
    band_h = round(H * band_pct / 100) if band_color else 0
    pw = round(width * panel_pct / 100)
    area_h = H - bar_h - band_h
    canvas = Image.new("RGB", (width, H), ImageColor.getrgb(panel_color))
    if zoom > 1:
        ph = crop_poi(photo, width - pw, area_h, zoom, focus)
    else:
        ph = ImageOps.fit(photo, (width - pw, area_h), Image.LANCZOS, centering=parse_focus(focus))
    px = width - ph.width if panel_side == "left" else 0
    canvas.paste(ph, (px, band_h))
    d = ImageDraw.Draw(canvas)
    if bar_color:
        d.rectangle([0, H - bar_h, width, H], fill=ImageColor.getrgb(bar_color))
    if band_color:
        d.rectangle([0, 0, width, band_h], fill=ImageColor.getrgb(band_color))
    # text: explicit line breaks are deliberate design — keep them; otherwise wrap. Auto-fit to the panel.
    max_w, max_h = pw * 0.84, area_h * 0.72
    paras = text.split("\n")
    size = int(pw * 0.34)
    probe = ImageDraw.Draw(Image.new("RGB", (1, 1)))
    while size > 24:
        font = load_font(size, font_path)
        lines = [l for para in paras for l in wrap(para, font, int(max_w))]
        gap = int(size * 0.38)
        h = len(lines) * (size + gap) - gap
        wmax = max(probe.textlength(shape(l)[0], font=font, **shape(l)[1]) for l in lines)
        if wmax <= max_w and h <= max_h:
            break
        size -= 4
    # draw at 2x on a mask so the stroke can be thinned (marker fonts -> fine pen) and downsampled smoothly
    S = 2
    mask = Image.new("L", (pw * S, area_h * S), 0)
    font2 = load_font(size * S, font_path)
    draw_lines(ImageDraw.Draw(mask), lines, font2, 0, pw * S, (area_h * S - h * S) // 2, 255, gap * S, align=align)
    thin = round(ink_thin * size / 110)  # ink_thin = erosion in px (at 2x) for a 110px font; scales with the font
    if thin > 0:
        mask = mask.filter(ImageFilter.MinFilter(2 * thin + 1))
    mask = mask.resize((pw, area_h), Image.LANCZOS)
    x0 = 0 if panel_side == "left" else width - pw
    canvas.paste(Image.new("RGB", mask.size, ImageColor.getrgb(ink)), (x0, band_h), mask)
    return canvas


def prepare_image(src: Path, out: Path, ratio: str | None = None, fit: str = "cover", focus: str = "center",
                  tone: bool = True, strength: float | None = None, width: int = 1080, pad_color: str = "#000000",
                  text: str | None = None, layout: str = "plain", split: dict | None = None, zoom: float = 1.0,
                  grade: bool | None = None, tiles: int = 1, **text_opts) -> dict:
    cfg = load_config()
    ratio_s = ratio or cfg["default_ratio"]
    r = parse_ratio(ratio_s)
    if not 0.5 <= r <= 1.91 * tiles:
        die(f"Ratio {ratio_s} is unsupported. Use 4:5, 1:1, 1.91:1 (feed) or 9:16 (story/reel cover).")
    img = ImageOps.exif_transpose(Image.open(src))
    if img.mode in ("RGBA", "LA", "P"):
        flat = Image.new("RGB", img.size, (255, 255, 255))
        rgba = img.convert("RGBA")
        flat.paste(rgba, mask=rgba.split()[-1])
        img = flat
    img = img.convert("RGB")
    do_grade = ("grade" in cfg) if grade is None else grade
    if do_grade:  # colour-grade the photo itself (never the cream panel) to sit in the feed's palette
        img = brand_grade(img, cfg.get("grade") or None)
    data = read_json(BRAND_DIR / "style-data.json", {}) if tone else {}
    target = (data or {}).get("tone")
    report: dict = {"source": str(src), "ratio": ratio_s, "layout": layout, "graded": do_grade}
    if layout == "split":
        if not text:
            die("--layout split needs --text (the panel title)")
        if tone and target:  # tone-match the photo itself, never the cream panel
            img, applied = match_tone(img, target, cfg["tone_strength"] if strength is None else strength)
            report["tone_adjustments"] = applied
        opts = {**cfg.get("split", {}), **(split or {})}
        img = compose_split(img, r, width, text, focus=focus, font_path=text_opts.get("font_path"), zoom=zoom, **opts)
        text = None
        report["size"] = list(img.size)
    else:
        img = fit_image(img, r, width, fit, focus, pad_color)
        report["size"] = list(img.size)
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
