#!/usr/bin/env python3
"""instagram-publisher CLI.

  auth-setup | auth-check
  learn      [--limit N] [--from-dir DIR]          learn the feed's visual + verbal language
  inspect    FILES…                                dimensions / duration / tone stats
  frames     VIDEO [--n 8]                         contact sheet to "watch" a video
  prepare    FILES… --out DIR [options]            fit to IG specs, tone-match, text overlay, transcode
  draft      FILES… --caption-file F [options]     create a draft + preview image
  approve    ID                                    mark a draft as approved by the user
  publish    ID [--dry-run]                        upload + publish an approved draft
  drafts | recent | quota
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import drafts  # noqa: E402
import media  # noqa: E402
import style  # noqa: E402
from common import (BRAND_DIR, DRAFTS_DIR, Graph, GraphError, die, load_config, load_env, read_state, require_ig, write_env)  # noqa: E402


def out(obj) -> None:
    print(json.dumps(obj, ensure_ascii=False, indent=2))


# ------------------------------------------------------------------ auth
def cmd_auth_setup(a) -> None:
    env = load_env()
    app_id = a.app_id or env.get("FB_APP_ID")
    secret = a.app_secret or env.get("FB_APP_SECRET")
    token = a.user_token or env.get("FB_USER_TOKEN")
    if not (app_id and secret and token):
        die("Need --app-id, --app-secret and --user-token (short-lived token from Graph API Explorer). See references/setup-he.md")
    g = Graph(token="x", env=env)
    g.token = token
    try:
        long = g.get("oauth/access_token", grant_type="fb_exchange_token", client_id=app_id,
                     client_secret=secret, fb_exchange_token=token)["access_token"]
        g.token = long
        pages = g.get("me/accounts", fields="id,name,access_token,instagram_business_account{id,username}", limit=100)
    except GraphError as e:
        die(f"{e}  {e.hint()}")
    cands = [p for p in pages.get("data", []) if p.get("instagram_business_account")]
    if not cands:
        die("No Facebook Page with a linked Instagram Business/Creator account was found for this token. "
            "Link the accounts in Instagram → Settings → Account type & tools → Connect a Facebook Page, "
            "and make sure the token includes pages_show_list + instagram_basic.")
    if a.ig_username:
        cands = [p for p in cands if p["instagram_business_account"].get("username", "").lower() == a.ig_username.lower()]
    if len(cands) > 1:
        print("Multiple Instagram accounts found — re-run with --ig-username:")
        for p in cands:
            print(f"  @{p['instagram_business_account'].get('username')}  (page: {p['name']})")
        sys.exit(2)
    if not cands:
        die("That username is not linked to any of your pages.")
    page = cands[0]
    ig = page["instagram_business_account"]
    write_env({"IG_USER_ID": ig["id"], "IG_USERNAME": ig.get("username", ""), "IG_PAGE_ID": page["id"],
               "IG_ACCESS_TOKEN": page["access_token"], "FB_APP_ID": app_id})
    print(f"Connected to @{ig.get('username')} (IG id {ig['id']}) via page “{page['name']}”. Token saved to .env (chmod 600, git-ignored).")
    print("A page token derived from a long-lived user token does not expire unless you change password / revoke the app.")


def cmd_auth_check(a) -> None:
    g = require_ig()
    try:
        me = g.get(g.ig_user_id, fields="username,name,followers_count,media_count,account_type")
    except GraphError as e:
        die(f"{e}  {e.hint()}")
    out(me)
    env = load_env()
    print("Media host:", env.get("IG_MEDIA_HOST") or "NOT CONFIGURED (needed to publish)")


# ------------------------------------------------------------------ learn
def cmd_learn(a) -> None:
    graph = None if a.from_dir else require_ig()
    data = style.run_all(graph, a.limit, Path(a.from_dir) if a.from_dir else None)
    print(f"Analyzed {data['posts_analyzed']} posts.")
    print("Measured data   :", BRAND_DIR / "style-data.json")
    for s in data["contact_sheets"]:
        print(f"Contact sheet   : {s['file']}   ({s['label']})")
    print("\nNEXT: look at the contact sheets, then write brand/STYLE_GUIDE.md (see references/style-guide-template.md).")


# ------------------------------------------------------------------ media
def cmd_inspect(a) -> None:
    out([media.inspect(Path(f)) for f in a.files])


def cmd_frames(a) -> None:
    p = Path(a.video)
    res = media.video_frames_sheet(p, Path(a.out) if a.out else p.with_suffix(".frames.jpg"), a.n)
    out(res)


def resolve_panel_side(a) -> str | None:
    """--panel-side wins; else brand/config.json split.panel_side. 'alternate' flips the side of the last published post."""
    if a.layout != "split":
        return None
    side = a.panel_side or load_config().get("split", {}).get("panel_side", "left")
    if side in ("alternate", "auto"):
        side = "right" if read_state().get("last_panel_side") == "left" else "left"
    a.panel_side = side
    return side


def _split_opts(a) -> dict:
    m = {"panel_pct": a.panel_pct, "panel_side": a.panel_side, "panel_color": a.panel_color,
         "ink": a.ink, "bar_color": a.bar_color, "band_color": a.band_color}
    return {k: v for k, v in m.items() if v is not None}


def _prepare_files(files, outdir: Path, a) -> tuple[list[Path], list[dict]]:
    outs, reports = [], []
    for i, f in enumerate(files):
        f = Path(f)
        if media.kind_of(f) == "image":
            dest = outdir / f"{f.stem}.jpg"
            ratio = a.ratio or (f"{4 * a.tiles}:5" if a.tiles > 1 else None)
            rep = media.prepare_image(
                f, dest, ratio=ratio, fit=a.fit, focus=a.focus, tone=not a.no_tone, strength=a.tone_strength,
                pad_color=a.pad_color, layout=a.layout, split=_split_opts(a), zoom=a.zoom, tiles=a.tiles, grade=False if a.no_grade else None, text=a.text if (a.text and (a.text_on is None or a.text_on == i)) else None,
                position=a.text_pos, color=a.text_color, font_path=a.font, size_pct=a.text_size, box=a.text_box,
                stroke=a.text_stroke)
        else:
            dest = outdir / f"{f.stem}.mp4"
            rep = media.prepare_video(f, dest, ratio=a.video_ratio, fit="cover" if a.fit == "cover" else "contain")
        if a.tiles > 1 and media.kind_of(f) == "image":
            tiles = media.slice_tiles(dest, a.tiles)
            rep["tiles"] = [str(t) for t in tiles]
            outs.extend(tiles)
        else:
            outs.append(dest)
        reports.append(rep)
    return outs, reports


def cmd_prepare(a) -> None:
    resolve_panel_side(a)
    _, reports = _prepare_files(a.files, Path(a.out), a)
    out(reports)


# ------------------------------------------------------------------ drafts
def cmd_draft(a) -> None:
    files = [Path(f) for f in a.files]
    for f in files:
        if not f.exists():
            die(f"File not found: {f}")
    caption = Path(a.caption_file).read_text(encoding="utf-8").strip() if a.caption_file else (a.caption or "")
    first_comment = Path(a.first_comment_file).read_text(encoding="utf-8").strip() if a.first_comment_file else None
    side = resolve_panel_side(a)
    kind = a.kind if a.kind != "auto" else drafts.infer_kind(files, a.story)
    reports: list[dict] = []
    if not a.no_prepare:
        tmp = Path(a.out_tmp or (BRAND_DIR.parent / "cache" / "prepared"))
        tmp.mkdir(parents=True, exist_ok=True)
        files, reports = _prepare_files(files, tmp, a)
    d = drafts.create_draft(files, caption, kind, first_comment, a.alt, a.cover_seconds, not a.reel_only,
                            a.location_id)
    if side:
        d["panel_side"] = side
        drafts.save_draft(d)
    errors, warns = drafts.validate(d)
    username = load_env().get("IG_USERNAME") or load_config().get("account", "your_account")
    previews = drafts.render_preview(d, username)
    out({"draft_id": d["id"], "kind": kind, "items": len(d["media"]), "preview": [str(p) for p in previews],
         "errors": errors, "warnings": warns, "status": d["status"], "prepared": reports})
    if errors:
        sys.exit(3)


def cmd_approve(a) -> None:
    d = drafts.approve(a.id)
    print(f"Draft {d['id']} approved at {d['approved_at']}. Publish with: ig.py publish {d['id']}")


def cmd_publish(a) -> None:
    if a.force_retry:
        d = drafts.load_draft(a.id)
        if d["status"] == "publish_unknown":
            d["status"] = "approved"
            drafts.save_draft(d)
    d = drafts.publish(a.id, dry_run=a.dry_run)
    if not a.dry_run:
        print(f"PUBLISHED ✅  {d.get('permalink') or d.get('media_id')}")


def cmd_drafts(a) -> None:
    out(drafts.list_drafts())


def cmd_recent(a) -> None:
    g = require_ig()
    try:
        items = list(g.paginate(f"{g.ig_user_id}/media", a.n, fields="id,media_type,permalink,timestamp,caption", limit=a.n))
    except GraphError as e:
        die(f"{e} {e.hint()}")
    out([{**i, "caption": (i.get("caption") or "")[:80]} for i in items])


def cmd_quota(a) -> None:
    g = require_ig()
    out(g.get(f"{g.ig_user_id}/content_publishing_limit", fields="quota_usage,config"))


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(prog="ig.py", description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest="cmd", required=True)

    s = sub.add_parser("auth-setup")
    s.add_argument("--app-id"); s.add_argument("--app-secret"); s.add_argument("--user-token")
    s.add_argument("--ig-username"); s.set_defaults(fn=cmd_auth_setup)
    sub.add_parser("auth-check").set_defaults(fn=cmd_auth_check)

    s = sub.add_parser("learn")
    s.add_argument("--limit", type=int, default=60)
    s.add_argument("--from-dir", help="learn from a local folder of images (+ same-name .txt captions) instead of the API")
    s.set_defaults(fn=cmd_learn)

    s = sub.add_parser("inspect"); s.add_argument("files", nargs="+"); s.set_defaults(fn=cmd_inspect)
    s = sub.add_parser("frames"); s.add_argument("video"); s.add_argument("--n", type=int, default=8)
    s.add_argument("--out"); s.set_defaults(fn=cmd_frames)

    def media_opts(sp):
        sp.add_argument("--ratio", help="image ratio, default from brand/config.json (4:5)")
        sp.add_argument("--video-ratio", default="9:16")
        sp.add_argument("--fit", choices=["cover", "contain", "blur"], default="cover")
        sp.add_argument("--focus", default="center", help="crop focus: center|top|bottom|left|right|top-left…")
        sp.add_argument("--pad-color", default="#000000")
        sp.add_argument("--no-tone", action="store_true", help="skip colour/tone matching to the feed")
        sp.add_argument("--tone-strength", type=float)
        sp.add_argument("--text", help="text to overlay on the image")
        sp.add_argument("--text-on", type=int, help="apply --text only to the Nth file (0-based)")
        sp.add_argument("--text-pos", default="bottom", choices=["top", "center", "bottom"])
        sp.add_argument("--text-color", default="#ffffff")
        sp.add_argument("--text-box", help="background box colour for text, e.g. '#00000099'")
        sp.add_argument("--text-size", type=float, default=5.5, help="font size as %% of image width")
        sp.add_argument("--text-stroke", type=int, default=0)
        sp.add_argument("--layout", choices=["plain", "split"], default="plain",
                        help="split = text panel + photo + colour bar (the account's signature layout; defaults in brand/config.json)")
        sp.add_argument("--zoom", type=float, default=1.0, help="split layout: zoom N× into the --focus point (x,y)")
        sp.add_argument("--no-grade", action="store_true", help="skip the feed colour grade")
        sp.add_argument("--tiles", type=int, default=1, help="render one wide image and cut it into N continuous tiles")
        sp.add_argument("--panel-pct", type=float); sp.add_argument("--panel-side", choices=["left", "right", "alternate"], help="left | right | alternate (flip vs the last published post)")
        sp.add_argument("--panel-color"); sp.add_argument("--ink"); sp.add_argument("--bar-color"); sp.add_argument("--band-color")
        sp.add_argument("--font", help="path to .ttf/.otf (defaults to brand/fonts/*)")

    s = sub.add_parser("prepare"); s.add_argument("files", nargs="+"); s.add_argument("--out", required=True)
    media_opts(s); s.set_defaults(fn=cmd_prepare)

    s = sub.add_parser("draft"); s.add_argument("files", nargs="+")
    s.add_argument("--caption"); s.add_argument("--caption-file"); s.add_argument("--first-comment-file")
    s.add_argument("--kind", choices=["auto", "IMAGE", "CAROUSEL", "REELS", "STORIES"], default="auto")
    s.add_argument("--story", action="store_true"); s.add_argument("--no-prepare", action="store_true")
    s.add_argument("--alt", action="append", help="alt text per media item (repeat)")
    s.add_argument("--cover-seconds", type=float, help="reel cover frame time")
    s.add_argument("--reel-only", action="store_true", help="do not also share the reel to the main grid")
    s.add_argument("--location-id"); s.add_argument("--out-tmp")
    media_opts(s); s.set_defaults(fn=cmd_draft)

    s = sub.add_parser("approve"); s.add_argument("id"); s.set_defaults(fn=cmd_approve)
    s = sub.add_parser("publish"); s.add_argument("id"); s.add_argument("--dry-run", action="store_true")
    s.add_argument("--force-retry", action="store_true"); s.set_defaults(fn=cmd_publish)
    sub.add_parser("drafts").set_defaults(fn=cmd_drafts)
    s = sub.add_parser("recent"); s.add_argument("--n", type=int, default=10); s.set_defaults(fn=cmd_recent)
    sub.add_parser("quota").set_defaults(fn=cmd_quota)
    return p


def main() -> None:
    args = build_parser().parse_args()
    args.fn(args)


if __name__ == "__main__":
    main()
