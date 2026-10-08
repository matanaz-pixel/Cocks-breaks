"""Learn the account's visual + verbal language: fetch posts, measure them, build contact sheets."""
from __future__ import annotations

import collections
import re
import statistics
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

from PIL import Image, ImageDraw, ImageOps

from common import BRAND_DIR, CACHE_DIR, Graph, GraphError, load_config, read_json, write_json, die
from media import image_stats, load_font

POSTS_DIR = CACHE_DIR / "posts"
FIELDS = ("id,caption,media_type,media_product_type,media_url,thumbnail_url,permalink,timestamp,"
          "like_count,comments_count,children{media_type,media_url,thumbnail_url}")
EMOJI = re.compile("[\U0001F300-\U0001FAFF☀-➿⭐⬆⬅⬇❤✅❌✨]")
HASHTAG = re.compile(r"#[\w֐-׿]+")
MENTION = re.compile(r"@[\w.]+")
CTA_WORDS = ["לינק בביו", "קישור בביו", "link in bio", "שלחו", "תייגו", "שמרו", "save", "share", "comment", "הזמינו",
             "להזמנות", "הירשמו", "קנו", "הגיבו", "ספרו לנו", "book", "dm"]


def _download(url: str, dest: Path) -> bool:
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=60) as r:
            dest.write_bytes(r.read())
        return True
    except Exception:
        return False


# ------------------------------------------------------------------ fetch
def fetch(graph: Graph, limit: int = 60) -> list[dict]:
    """Pull recent posts + their cover images into cache/posts/. Media URLs expire, so download immediately."""
    POSTS_DIR.mkdir(parents=True, exist_ok=True)
    posts = []
    try:
        for item in graph.paginate(f"{graph.ig_user_id}/media", limit, fields=FIELDS, limit=min(limit, 50)):
            url = item.get("thumbnail_url") if item.get("media_type") == "VIDEO" else item.get("media_url")
            local = POSTS_DIR / f"{item['id']}.jpg"
            if url and (local.exists() or _download(url, local)):
                item["local"] = str(local)
            posts.append(item)
    except GraphError as e:
        die(f"{e} {e.hint()}")
    write_json(CACHE_DIR / "posts.json", posts)
    return posts


def posts_from_dir(folder: Path) -> list[dict]:
    """Fallback when the API is not connected: a folder of images (+ optional same-name .txt captions)."""
    POSTS_DIR.mkdir(parents=True, exist_ok=True)
    posts = []
    for i, f in enumerate(sorted(p for p in folder.iterdir() if p.suffix.lower() in {".jpg", ".jpeg", ".png", ".webp"})):
        cap = f.with_suffix(".txt")
        local = POSTS_DIR / f"manual-{i}.jpg"
        ImageOps.exif_transpose(Image.open(f)).convert("RGB").save(local, "JPEG", quality=90)
        posts.append({"id": f"manual-{i}", "media_type": "IMAGE", "local": str(local), "permalink": str(f),
                      "caption": cap.read_text(encoding="utf-8") if cap.exists() else "",
                      "timestamp": datetime.fromtimestamp(f.stat().st_mtime, timezone.utc).strftime("%Y-%m-%dT%H:%M:%S+0000")})
    write_json(CACHE_DIR / "posts.json", posts)
    return posts


# ------------------------------------------------------------------ analysis
def _palette(paths: list[str], n: int = 8) -> list[dict]:
    if not paths:
        return []
    tile, cols = 48, 10
    rows = -(-len(paths) // cols)
    mosaic = Image.new("RGB", (cols * tile, rows * tile))
    for i, p in enumerate(paths):
        im = ImageOps.fit(Image.open(p).convert("RGB"), (tile, tile))
        mosaic.paste(im, ((i % cols) * tile, (i // cols) * tile))
    q = mosaic.quantize(colors=n, method=Image.MEDIANCUT)
    pal = q.getpalette()
    total = mosaic.width * mosaic.height
    out = []
    for count, idx in sorted(q.getcolors(), reverse=True):
        r, g, b = pal[idx * 3: idx * 3 + 3]
        out.append({"hex": f"#{r:02x}{g:02x}{b:02x}", "share": round(count / total, 3)})
    return out


def _tz(name: str):
    try:
        from zoneinfo import ZoneInfo
        return ZoneInfo(name)
    except Exception:
        return timezone.utc


def _eng(p: dict) -> int:
    return int(p.get("like_count") or 0) + int(p.get("comments_count") or 0)


def analyze(posts: list[dict]) -> dict:
    cfg = load_config()
    tz = _tz(cfg["timezone"])
    with_img = [p for p in posts if p.get("local") and Path(p["local"]).exists()]
    top_n = max(3, len(with_img) // 4)
    top = sorted(with_img, key=_eng, reverse=True)[:top_n] if any(_eng(p) for p in with_img) else []

    stats = [image_stats(Image.open(p["local"])) for p in with_img]
    tone = {k: round(statistics.mean(s[k] for s in stats), 4) for k in ("brightness", "saturation", "contrast", "warmth")} if stats else {}

    ratios = collections.Counter()
    for p in with_img:
        w, h = Image.open(p["local"]).size
        r = w / h
        ratios["4:5 portrait" if r < 0.9 else "1:1 square" if r < 1.1 else "landscape" if r < 1.7 else "wide 1.91:1"] += 1

    captions = [p.get("caption") or "" for p in posts]
    nonempty = [c for c in captions if c.strip()]
    tags = collections.Counter(t.lower() for c in nonempty for t in HASHTAG.findall(c))
    emojis = collections.Counter(e for c in nonempty for e in EMOJI.findall(c))
    heb = sum(len(re.findall(r"[֐-׿]", c)) for c in nonempty)
    lat = sum(len(re.findall(r"[A-Za-z]", c)) for c in nonempty)
    last_lines = collections.Counter(c.strip().splitlines()[-1].strip() for c in nonempty if c.strip().splitlines())
    first_lines = [c.strip().splitlines()[0] for c in nonempty if c.strip().splitlines()]
    body_wo_tags = [HASHTAG.sub("", c).strip() for c in nonempty]
    lc = lambda c: c.lower()  # noqa: E731
    cta = collections.Counter(w for c in nonempty for w in CTA_WORDS if w in lc(c))

    hours, days = collections.Counter(), collections.Counter()
    for p in posts:
        try:
            dt = datetime.strptime(p["timestamp"], "%Y-%m-%dT%H:%M:%S%z").astimezone(tz)
            hours[dt.hour] += 1
            days[dt.strftime("%a")] += 1
        except (KeyError, ValueError):
            pass

    types = collections.Counter(
        "REEL" if p.get("media_product_type") == "REELS" else p.get("media_type", "?") for p in posts)
    slides = [len(p.get("children", {}).get("data", [])) for p in posts if p.get("media_type") == "CAROUSEL_ALBUM"]

    def med(xs):
        return round(statistics.median(xs), 1) if xs else 0

    def top_row(p):
        return {"id": p["id"], "permalink": p.get("permalink"), "engagement": _eng(p), "type": p.get("media_type"),
                "caption_preview": (p.get("caption") or "")[:140]}

    return {
        "generated_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "posts_analyzed": len(posts), "images_analyzed": len(with_img),
        "format_mix": dict(types), "avg_carousel_slides": med(slides),
        "aspect_ratios": dict(ratios),
        "tone": tone,
        "palette_overall": _palette([p["local"] for p in with_img]),
        "palette_top_performers": _palette([p["local"] for p in top], 6),
        "top_performers": [top_row(p) for p in top],
        "caption": {
            "count": len(nonempty),
            "median_chars_without_hashtags": med([len(c) for c in body_wo_tags]),
            "median_words": med([len(c.split()) for c in body_wo_tags]),
            "median_lines": med([len([l for l in c.splitlines() if l.strip()]) for c in nonempty]),
            "median_first_line_chars": med([len(l) for l in first_lines]),
            "emojis_per_post_median": med([len(EMOJI.findall(c)) for c in nonempty]),
            "top_emojis": emojis.most_common(10),
            "hashtags_per_post_median": med([len(HASHTAG.findall(c)) for c in nonempty]),
            "top_hashtags": tags.most_common(15),
            "mentions_per_post_median": med([len(MENTION.findall(c)) for c in nonempty]),
            "question_share": round(sum("?" in c for c in nonempty) / max(1, len(nonempty)), 2),
            "hebrew_letter_share": round(heb / max(1, heb + lat), 2),
            "recurring_last_lines": [l for l, n in last_lines.most_common(3) if n > 1],
            "cta_phrases": cta.most_common(6),
            "sample_first_lines": first_lines[:8],
        },
        "posting": {"timezone": cfg["timezone"], "hours": dict(sorted(hours.items())),
                    "weekdays": dict(days), "posts_per_week_est": round(len(posts) / max(1, _span_weeks(posts)), 1)},
    }


def _span_weeks(posts: list[dict]) -> float:
    ts = []
    for p in posts:
        try:
            ts.append(datetime.strptime(p["timestamp"], "%Y-%m-%dT%H:%M:%S%z"))
        except (KeyError, ValueError):
            pass
    return max(1.0, (max(ts) - min(ts)).days / 7) if len(ts) > 1 else 1.0


# ------------------------------------------------------------------ contact sheets
def contact_sheets(posts: list[dict], per_sheet: int = 20, cols: int = 5, cell_w: int = 300, cell_h: int = 375) -> list[dict]:
    """Numbered grids Claude can look at to learn typography, composition and grading. Index map saved to style-data."""
    with_img = [p for p in posts if p.get("local") and Path(p["local"]).exists()]
    by_eng = sorted(with_img, key=_eng, reverse=True)[:per_sheet] if any(_eng(p) for p in with_img) else []
    recent = with_img[:per_sheet]
    groups = [("top-performing", by_eng), ("most-recent", recent)] if by_eng else [("most-recent", recent)]
    BRAND_DIR.mkdir(parents=True, exist_ok=True)
    font = load_font(20)
    out = []
    for n, (label, group) in enumerate(groups, 1):
        rows = -(-len(group) // cols)
        sheet = Image.new("RGB", (cols * cell_w, rows * cell_h), "#1a1a1a")
        d = ImageDraw.Draw(sheet)
        index = []
        for i, p in enumerate(group):
            im = ImageOps.contain(Image.open(p["local"]).convert("RGB"), (cell_w - 6, cell_h - 6))
            x, y = (i % cols) * cell_w, (i // cols) * cell_h
            sheet.paste(im, (x + (cell_w - im.width) // 2, y + (cell_h - im.height) // 2))
            d.rectangle([x, y, x + 34, y + 28], fill="#000000")
            d.text((x + 6, y + 2), str(i + 1), fill="#ffffff", font=font)
            index.append({"n": i + 1, "id": p["id"], "type": p.get("media_type"), "engagement": _eng(p),
                          "permalink": p.get("permalink")})
        path = BRAND_DIR / f"contact-sheet-{n}-{label}.jpg"
        sheet.save(path, "JPEG", quality=88)
        out.append({"label": label, "file": str(path), "index": index})
    return out


def run_all(graph: Graph | None, limit: int, from_dir: Path | None) -> dict:
    posts = posts_from_dir(from_dir) if from_dir else fetch(graph, limit)  # type: ignore[arg-type]
    if not posts:
        die("No posts found to learn from.")
    data = analyze(posts)
    data["contact_sheets"] = contact_sheets(posts)
    write_json(BRAND_DIR / "style-data.json", data)
    cfg_path = BRAND_DIR / "config.json"
    if not cfg_path.exists():
        write_json(cfg_path, load_config())
    return data


def load_style() -> dict | None:
    return read_json(BRAND_DIR / "style-data.json")
