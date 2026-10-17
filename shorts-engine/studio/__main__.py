"""Shorts studio command line.

  python -m studio make [--count 1]    render the next stories and send them to Telegram for approval
  python -m studio poll                read Approve/Reject taps and upload approved videos to YouTube
  python -m studio ideas [--min 7]     top up the story bank with Gemini when it runs low (optional)
  python -m studio sample ID [ID ...]  render stories to out/ without sending anything
  python -m studio branding            write channel art to out/branding
  python -m studio auth CLIENT.json    one-time YouTube sign-in that prints a refresh token

Secrets come from the environment: TELEGRAM_BOT_TOKEN, TELEGRAM_CHAT_ID,
YT_CLIENT_ID, YT_CLIENT_SECRET, YT_REFRESH_TOKEN, GEMINI_API_KEY (optional).
"""
import argparse
import json
import os
import sys
import tempfile
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BANK = ROOT / "content" / "bank.json"
STATE = ROOT / "state" / "queue.json"
OUT = ROOT / "out"


def load_bank():
    return json.loads(BANK.read_text())


def load_state():
    if STATE.exists():
        return json.loads(STATE.read_text())
    return {"items": {}, "telegram_offset": None}


def save_state(state):
    STATE.parent.mkdir(parents=True, exist_ok=True)
    STATE.write_text(json.dumps(state, indent=1, ensure_ascii=False) + "\n")


def now():
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def telegram():
    token, chat = os.environ.get("TELEGRAM_BOT_TOKEN"), os.environ.get("TELEGRAM_CHAT_ID")
    if not (token and chat):
        return None
    from .telegram import Telegram
    return Telegram(token, chat)


def cmd_make(args):
    from .render import render_story
    from .tts import pick_voice

    bank, state = load_bank(), load_state()
    fresh = [s for s in bank["stories"] if s["id"] not in state["items"]]
    if not fresh:
        sys.exit("Story bank is empty: run `python -m studio ideas` or add stories to content/bank.json")
    tg = telegram()
    voice = pick_voice()
    OUT.mkdir(exist_ok=True)
    for story in fresh[: args.count]:
        episode = len(state["items"]) + 1
        out = OUT / f"{story['id']}.mp4"
        with tempfile.TemporaryDirectory() as work:
            info = render_story(story, voice, out, work, episode=episode, seed=episode)
        item = {"episode": episode, "status": "rendered", "made_at": now(), "seconds": info["seconds"],
                "voice": voice.name}
        print(f"Rendered #{episode} {story['id']} ({info['seconds']}s, {out.stat().st_size // 1024} KB)")
        if tg:
            caption = (f"#{episode} {story['title']}\n\n{story['description']}\n\nSources:\n"
                       + "\n".join(story.get("sources", []))
                       + ("\n\n⚠️ AI-written script: check the facts before approving." if story.get("needs_review") else ""))
            item.update(tg.send_for_approval(out, story["id"], caption), status="pending")
        state["items"][story["id"]] = item
        save_state(state)


def cmd_poll(args):
    from .youtube_upload import access_token, build_metadata, upload

    tg = telegram()
    if not tg:
        sys.exit("TELEGRAM_BOT_TOKEN and TELEGRAM_CHAT_ID are required")
    state = load_state()
    decisions, state["telegram_offset"] = tg.decisions(state.get("telegram_offset"))
    for item_id, verdict in decisions.items():
        item = state["items"].get(item_id)
        if item and item["status"] == "pending":
            item["status"], item["decided_at"] = verdict, now()
    save_state(state)

    approved = [k for k, v in state["items"].items() if v["status"] == "approved"]
    creds = [os.environ.get(k) for k in ("YT_CLIENT_ID", "YT_CLIENT_SECRET", "YT_REFRESH_TOKEN")]
    if approved and not all(creds):
        tg.say(f"{len(approved)} approved video(s) are waiting: YouTube sign-in is not set up yet.")
        return
    stories = {s["id"]: s for s in load_bank()["stories"]}
    # Unaudited Google Cloud projects can only upload private videos; set YT_PRIVACY=public after the audit.
    privacy = os.environ.get("YT_PRIVACY") or "private"
    for item_id in approved[: args.max_uploads]:
        item = state["items"][item_id]
        with tempfile.TemporaryDirectory() as work:
            path = tg.download(item["file_id"], Path(work) / f"{item_id}.mp4")
            video_id = upload(path, build_metadata(stories[item_id], privacy=privacy), access_token(*creds))
        item.update(status="uploaded", youtube_id=video_id, uploaded_at=now(), privacy=privacy)
        save_state(state)
        note = "" if privacy == "public" else \
            "\nIt is private until Google approves the API audit: open YouTube Studio and set it to Public."
        tg.say(f"Uploaded #{item['episode']}: https://youtube.com/shorts/{video_id}{note}")
        print(f"Uploaded {item_id} -> {video_id}")


def cmd_ideas(args):
    from .ideas import generate, trend_titles

    bank, state = load_bank(), load_state()
    unused = [s for s in bank["stories"] if s["id"] not in state["items"]]
    if len(unused) >= args.min:
        print(f"{len(unused)} unused stories in the bank; nothing to do")
        return
    if not os.environ.get("GEMINI_API_KEY"):
        print("Bank is running low and GEMINI_API_KEY is not set; add stories by hand or set the key")
        return
    reports = sorted((ROOT / "reports").glob("????-??-??.json"))
    trends = trend_titles(reports[-1]) if reports else []
    new = generate(args.min - len(unused) + 2, [s["id"] for s in bank["stories"]], trends)
    bank["stories"].extend(new)
    BANK.write_text(json.dumps(bank, indent=2, ensure_ascii=False) + "\n")
    print(f"Added {len(new)} stories")


def cmd_sample(args):
    from .render import render_story
    from .tts import pick_voice

    stories = {s["id"]: s for s in load_bank()["stories"]}
    voice = pick_voice()
    OUT.mkdir(exist_ok=True)
    for i, sid in enumerate(args.ids, 1):
        with tempfile.TemporaryDirectory() as work:
            info = render_story(stories[sid], voice, OUT / f"{sid}.mp4", work, episode=i, seed=i)
        print(f"{sid}: {info['seconds']}s, voice={voice.name}")


def cmd_branding(args):
    from .branding import build_all
    for p in build_all(OUT / "branding"):
        print(p)


def cmd_auth(args):
    from .oauth import authorize
    authorize(args.client_secrets)


def main(argv=None):
    p = argparse.ArgumentParser(prog="studio")
    sub = p.add_subparsers(dest="cmd", required=True)
    m = sub.add_parser("make"); m.add_argument("--count", type=int, default=1); m.set_defaults(fn=cmd_make)
    q = sub.add_parser("poll"); q.add_argument("--max-uploads", type=int, default=3); q.set_defaults(fn=cmd_poll)
    i = sub.add_parser("ideas"); i.add_argument("--min", type=int, default=7); i.set_defaults(fn=cmd_ideas)
    s = sub.add_parser("sample"); s.add_argument("ids", nargs="+"); s.set_defaults(fn=cmd_sample)
    sub.add_parser("branding").set_defaults(fn=cmd_branding)
    a = sub.add_parser("auth"); a.add_argument("client_secrets"); a.set_defaults(fn=cmd_auth)
    args = p.parse_args(argv)
    args.fn(args)


if __name__ == "__main__":
    main()
