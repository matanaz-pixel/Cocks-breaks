"""Drafts (prepare → preview → approve) and the publishing state machine.

Safety model: nothing reaches Instagram unless the draft carries an explicit approval written by
`ig.py approve <id>`. Every API step is persisted in draft.json, so a re-run resumes instead of double-posting.
"""
from __future__ import annotations

import json
import re
import shutil
import time
from datetime import datetime, timezone
from pathlib import Path

from PIL import Image, ImageDraw, ImageOps

import hosting
from common import DRAFTS_DIR, Graph, GraphError, die, load_config, load_env, read_json, write_json, BRAND_DIR
from media import (HEB, draw_lines, extract_cover, kind_of, load_font, probe_video, shape, wrap)

HASHTAG = re.compile(r"#[\w֐-׿]+")
MENTION = re.compile(r"@[\w.]+")
KINDS = ("IMAGE", "CAROUSEL", "REELS", "STORIES")


def draft_dir(draft_id: str) -> Path:
    d = DRAFTS_DIR / draft_id
    if not d.exists():
        die(f"No such draft: {draft_id}  (list drafts with: ig.py drafts)")
    return d


def load_draft(draft_id: str) -> dict:
    return json.loads((draft_dir(draft_id) / "draft.json").read_text(encoding="utf-8"))


def save_draft(d: dict) -> None:
    write_json(DRAFTS_DIR / d["id"] / "draft.json", d)


# ------------------------------------------------------------------ create
def infer_kind(files: list[Path], story: bool) -> str:
    kinds = [kind_of(f) for f in files]
    if story:
        if len(files) != 1:
            die("A story draft takes exactly one image or video.")
        return "STORIES"
    if len(files) == 1:
        return "REELS" if kinds[0] == "video" else "IMAGE"
    if len(files) > 10:
        die("Instagram carousels hold at most 10 items.")
    return "CAROUSEL"


def create_draft(files: list[Path], caption: str, kind: str, first_comment: str | None = None,
                 alt: list[str] | None = None, cover_seconds: float | None = None, share_to_feed: bool = True,
                 location_id: str | None = None, user_tags: list[dict] | None = None) -> dict:
    now = datetime.now()
    draft_id = now.strftime("%Y%m%d-%H%M%S")
    d = DRAFTS_DIR / draft_id
    d.mkdir(parents=True, exist_ok=False)
    media = []
    for i, f in enumerate(files):
        dest = d / f"{i + 1:02d}{f.suffix.lower()}"
        shutil.copyfile(f, dest)
        media.append({"file": dest.name, "type": kind_of(f), "alt": (alt[i] if alt and i < len(alt) else None)})
    draft = {
        "id": draft_id, "kind": kind, "caption": caption, "first_comment": first_comment, "media": media,
        "cover_offset_ms": int(cover_seconds * 1000) if cover_seconds is not None else None,
        "share_to_feed": share_to_feed, "location_id": location_id, "user_tags": user_tags,
        "status": "draft", "approved_at": None, "created_at": now.isoformat(timespec="seconds"),
    }
    save_draft(draft)
    return draft


def validate(draft: dict, strict_files: bool = True) -> tuple[list[str], list[str]]:
    cfg = load_config()
    errors, warns = [], []
    cap = draft.get("caption") or ""
    if len(cap) > cfg["max_caption_chars"]:
        errors.append(f"Caption is {len(cap)} chars (max {cfg['max_caption_chars']}).")
    if len(HASHTAG.findall(cap)) > cfg["max_hashtags"]:
        errors.append(f"More than {cfg['max_hashtags']} hashtags.")
    if len(MENTION.findall(cap)) > 20:
        errors.append("More than 20 @mentions.")
    kind, media = draft["kind"], draft["media"]
    if kind not in KINDS:
        errors.append(f"Unknown kind {kind}")
    if kind == "CAROUSEL" and not 2 <= len(media) <= 10:
        errors.append("A carousel needs 2–10 items.")
    if kind in ("IMAGE", "REELS", "STORIES") and len(media) != 1:
        errors.append(f"{kind} takes exactly one media item.")
    if kind == "STORIES" and cap:
        warns.append("Stories published through the API ignore captions.")
    ratios = set()
    for m in media:
        p = DRAFTS_DIR / draft["id"] / m["file"]
        if strict_files and not p.exists():
            errors.append(f"Missing file {m['file']}")
            continue
        if not p.exists():
            continue
        if m["type"] == "image":
            if p.suffix.lower() not in (".jpg", ".jpeg"):
                errors.append(f"{m['file']}: the API only accepts JPEG images — run `ig.py prepare` first.")
            else:
                with Image.open(p) as im:
                    r = im.width / im.height
                    ratios.add(round(r, 2))
                    if kind != "STORIES" and not 0.79 <= r <= 1.92:
                        errors.append(f"{m['file']}: aspect {r:.2f} outside 4:5 … 1.91:1.")
                    if im.width < 320:
                        errors.append(f"{m['file']}: width {im.width}px < 320px.")
        else:
            info = probe_video(p)
            if info["vcodec"] != "h264" or info["acodec"] not in ("aac", None) or "mp4" not in info["container"]:
                errors.append(f"{m['file']}: needs MP4 H.264/AAC — run `ig.py prepare` first.")
            if info["duration"] < 3:
                errors.append(f"{m['file']}: video shorter than 3 seconds.")
            if info["size_mb"] > 300:
                warns.append(f"{m['file']}: {info['size_mb']} MB — very large, upload may fail.")
            if kind == "REELS" and info["width"] / info["height"] > 0.7:
                warns.append(f"{m['file']}: not 9:16 — Reels look best vertical.")
    if kind == "CAROUSEL" and len(ratios) > 1:
        warns.append("Carousel images have different aspect ratios; Instagram crops all slides to the first slide's ratio.")
    return errors, warns


# ------------------------------------------------------------------ preview
def render_preview(draft: dict, username: str) -> list[Path]:
    d = DRAFTS_DIR / draft["id"]
    W = 720
    first = draft["media"][0]
    src = d / first["file"]
    if first["type"] == "video":
        cover = d / "cover.jpg"
        extract_cover(src, cover, (draft.get("cover_offset_ms") or 1000) / 1000)
        src = cover
    img = ImageOps.exif_transpose(Image.open(src)).convert("RGB")
    h = min(int(W * img.height / img.width), 900)
    img = ImageOps.fit(img, (W, h)) if abs(img.height / img.width - h / W) > 0.01 else img.resize((W, h))
    font, bold = load_font(26, use_brand=False), load_font(26, use_brand=False)  # legible UI font, not the brand script
    caption = (draft.get("caption") or "").strip()
    lines = wrap(f"{username}  {caption}" if not HEB.search(caption) else caption, font, W - 40)
    cap_h = len(lines) * 36 + 30
    fc = draft.get("first_comment")
    fc_lines = wrap(fc, font, W - 40) if fc else []
    total = 70 + h + 60 + cap_h + (len(fc_lines) * 36 + 50 if fc_lines else 0)
    card = Image.new("RGB", (W, total), "#ffffff")
    dr = ImageDraw.Draw(card)
    dr.ellipse([16, 12, 58, 54], fill="#d0d0d0")
    dr.text((72, 22), username, fill="#111111", font=bold)
    card.paste(img, (0, 70))
    badge = {"CAROUSEL": f"1/{len(draft['media'])}", "REELS": "REEL ▶", "STORIES": "STORY"}.get(draft["kind"])
    if badge:
        dr.rounded_rectangle([W - 120, 84, W - 14, 122], radius=18, fill="#000000")
        dr.text((W - 108, 90), badge, fill="#ffffff", font=load_font(22, use_brand=False))
    y = 70 + h
    dr.text((16, y + 12), "♡    ◯    ➤", fill="#111111", font=load_font(30, use_brand=False))
    y += 60
    draw_lines(dr, lines, font, 20, W - 20, y + 10, "#111111", 10)
    if fc_lines:
        y += cap_h
        dr.text((20, y), "FIRST COMMENT", fill="#8e8e8e", font=load_font(18, use_brand=False))
        draw_lines(dr, fc_lines, font, 20, W - 20, y + 26, "#262626", 10)
    out = [d / "preview.png"]
    card.save(out[0])
    if draft["kind"] == "CAROUSEL":
        thumbs = []
        for m in draft["media"]:
            if m["type"] == "image":
                thumbs.append(ImageOps.fit(Image.open(d / m["file"]).convert("RGB"), (240, 300)))
        if thumbs:
            strip = Image.new("RGB", (len(thumbs) * 244, 300), "#222222")
            for i, t in enumerate(thumbs):
                strip.paste(t, (i * 244, 0))
            out.append(d / "slides.jpg")
            strip.save(out[1], quality=88)
    return out


def approve(draft_id: str) -> dict:
    draft = load_draft(draft_id)
    errors, _ = validate(draft)
    if errors:
        die("Cannot approve — fix first:\n  - " + "\n  - ".join(errors))
    if draft["status"] == "published":
        die("Draft is already published.")
    draft["approved_at"] = datetime.now(timezone.utc).isoformat(timespec="seconds")
    draft["status"] = "approved"
    save_draft(draft)
    return draft


# ------------------------------------------------------------------ publish
def wait_ready(graph: Graph, container_id: str, kind: str, log=print) -> None:
    timeout = 600 if kind in ("video", "REELS") else 90
    deadline = time.time() + timeout
    while time.time() < deadline:
        r = graph.get(container_id, fields="status_code,status")
        code = r.get("status_code")
        if code == "FINISHED":
            return
        if code in ("ERROR", "EXPIRED"):
            die(f"Instagram rejected the media ({code}): {r.get('status', '')}")
        log(f"  … processing ({code})")
        time.sleep(4)
    die(f"Timed out waiting for container {container_id}. Re-run `ig.py publish` — it will resume.")


def _container_params(m: dict, kind: str, carousel_item: bool, draft: dict) -> dict:
    p: dict = {}
    if m["type"] == "image":
        p["image_url"] = m["url"]
        if m.get("alt"):
            p["alt_text"] = m["alt"]
    else:
        p["video_url"] = m["url"]
        p["media_type"] = "VIDEO" if carousel_item else kind
    if carousel_item:
        p["is_carousel_item"] = "true"
    return p


def publish(draft_id: str, dry_run: bool = False, log=print) -> dict:
    env = load_env()
    draft = load_draft(draft_id)
    if draft["status"] == "published":
        die(f"Already published: {draft.get('permalink')}")
    if draft["status"] == "publish_unknown":
        die("A previous publish attempt ended without a confirmed result. Check `ig.py recent` on Instagram before retrying, "
            "then reset with `ig.py publish --force-retry`.")
    if not draft.get("approved_at"):
        die("Draft is not approved. The user must approve it first (ig.py approve <id>).")
    errors, warns = validate(draft)
    for w in warns:
        log(f"WARN: {w}")
    if errors:
        die("Validation failed:\n  - " + "\n  - ".join(errors))
    if dry_run:
        log(f"DRY RUN OK — {draft['kind']} with {len(draft['media'])} item(s) would be published. Nothing was sent.")
        return draft
    if not env.get("IG_ACCESS_TOKEN") or not env.get("IG_USER_ID"):
        die("Instagram is not connected. Run: python3 scripts/ig.py auth-setup")
    graph = Graph(env=env)
    ig = graph.ig_user_id
    kind = draft["kind"]
    try:
        q = graph.get(f"{ig}/content_publishing_limit", fields="quota_usage,config").get("data", [{}])[0]
        used, total = q.get("quota_usage", 0), q.get("config", {}).get("quota_total", 100)
        log(f"Publishing quota: {used}/{total} used in the last 24h")
        if used >= total:
            die("Instagram's 24h API publishing limit is exhausted. Try again later.")
    except GraphError as e:
        log(f"(quota check skipped: {e})")

    ddir = DRAFTS_DIR / draft["id"]
    draft["status"] = "publishing"
    # 1) host media -------------------------------------------------------
    for m in draft["media"]:
        if not m.get("url"):
            log(f"Uploading {m['file']} …")
            m["url"] = hosting.upload(ddir / m["file"], m["type"], env)
            save_draft(draft)
    try:
        # 2) containers ------------------------------------------------------
        if not draft.get("container_id"):
            if kind == "CAROUSEL":
                child_ids = []
                for m in draft["media"]:
                    if not m.get("container_id"):
                        m["container_id"] = graph.post(f"{ig}/media", **_container_params(m, kind, True, draft))["id"]
                        save_draft(draft)
                    child_ids.append(m["container_id"])
                for m in draft["media"]:
                    wait_ready(graph, m["container_id"], m["type"], log)
                params = {"media_type": "CAROUSEL", "children": ",".join(child_ids), "caption": draft["caption"],
                          "location_id": draft.get("location_id")}
            else:
                m = draft["media"][0]
                params = _container_params(m, kind, False, draft)
                if kind == "IMAGE":
                    params["caption"] = draft["caption"]
                    if draft.get("user_tags"):
                        params["user_tags"] = json.dumps(draft["user_tags"])
                elif kind == "STORIES":
                    params["media_type"] = "STORIES"
                else:  # REELS
                    params["caption"] = draft["caption"]
                    params["share_to_feed"] = "true" if draft.get("share_to_feed", True) else "false"
                    if draft.get("cover_offset_ms") is not None:
                        params["thumb_offset"] = draft["cover_offset_ms"]
                params["location_id"] = draft.get("location_id")
            draft["container_id"] = graph.post(f"{ig}/media", **params)["id"]
            save_draft(draft)
        log(f"Container {draft['container_id']} created — waiting for Instagram to process …")
        wait_ready(graph, draft["container_id"], kind, log)
    except GraphError as e:
        draft["status"], draft["error"] = "failed", str(e)
        save_draft(draft)
        die(f"{e}  {e.hint()}")

    # 3) publish (never auto-retried: a blind retry could double-post) -----------
    try:
        media_id = graph.post(f"{ig}/media_publish", creation_id=draft["container_id"])["id"]
    except GraphError as e:
        if e.http_status is None:
            draft["status"], draft["error"] = "publish_unknown", str(e)
            save_draft(draft)
            die(f"Network problem during the final publish call — outcome unknown. Check Instagram before retrying. ({e})")
        draft["status"], draft["error"] = "failed", str(e)
        save_draft(draft)
        die(f"{e}  {e.hint()}")
    permalink = None
    try:
        permalink = graph.get(media_id, fields="permalink").get("permalink")
    except GraphError:
        pass
    draft.update(status="published", media_id=media_id, permalink=permalink,
                 published_at=datetime.now(timezone.utc).isoformat(timespec="seconds"))
    draft.pop("error", None)
    save_draft(draft)
    if draft.get("first_comment"):
        try:
            graph.post(f"{media_id}/comments", message=draft["first_comment"])
        except GraphError as e:
            log(f"WARN: post is live but the first comment failed ({e}). Needs instagram_manage_comments permission.")
    BRAND_DIR.mkdir(exist_ok=True)
    with (BRAND_DIR / "published.jsonl").open("a", encoding="utf-8") as fh:
        fh.write(json.dumps({"draft": draft["id"], "media_id": media_id, "permalink": permalink,
                             "kind": kind, "at": draft["published_at"]}, ensure_ascii=False) + "\n")
    return draft


def list_drafts() -> list[dict]:
    if not DRAFTS_DIR.exists():
        return []
    out = []
    for p in sorted(DRAFTS_DIR.glob("*/draft.json"), reverse=True):
        d = json.loads(p.read_text(encoding="utf-8"))
        out.append({"id": d["id"], "kind": d["kind"], "status": d["status"], "items": len(d["media"]),
                    "caption": (d.get("caption") or "")[:60], "permalink": d.get("permalink")})
    return out
