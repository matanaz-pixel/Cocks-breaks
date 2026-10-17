"""Run the daily market scan: python -m market --out reports

Needs YOUTUBE_API_KEY in the environment.
"""
import argparse
import json
import os
import sys
from datetime import datetime, timezone
from pathlib import Path

from .collect import collect
from .report import load_history, render_markdown
from .score import outliers, rank
from .youtube import YouTubeClient

HERE = Path(__file__).parent


def main(argv=None):
    p = argparse.ArgumentParser(description="Daily YouTube Shorts niche scan")
    p.add_argument("--out", default="reports", help="folder for daily JSON and latest.md")
    p.add_argument("--quota", type=int, default=5000, help="max API units to spend (half the daily 10,000)")
    p.add_argument("--raw", help="also write the full raw snapshot to this file")
    args = p.parse_args(argv)

    key = os.environ.get("YOUTUBE_API_KEY")
    if not key:
        sys.exit("YOUTUBE_API_KEY is not set")

    niches = json.loads((HERE / "niches.json").read_text())["niches"]
    client = YouTubeClient(key, quota_budget=args.quota)
    snapshot = collect(client, niches)
    if args.raw:
        Path(args.raw).write_text(json.dumps(snapshot))

    now = datetime.now(timezone.utc)
    day = {
        "date": now.strftime("%Y-%m-%d"),
        "quota_used": snapshot["quota_used"],
        "skipped": snapshot["skipped"],
        "ranking": rank(snapshot, niches),
        "outliers": outliers(snapshot),
    }
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    (out / f"{day['date']}.json").write_text(json.dumps(day, indent=1, ensure_ascii=False))
    history = load_history(out)
    (out / "latest.md").write_text(render_markdown(day, history))
    print(f"Ranked {len(day['ranking'])} niches, {len(day['outliers'])} breakouts, {day['quota_used']} units used")


if __name__ == "__main__":
    main()
