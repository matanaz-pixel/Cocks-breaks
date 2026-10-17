"""Daily report: today's ranking, a rolling average across past days, and breakout videos."""
import json
from pathlib import Path

LOCK_AFTER_DAYS = 14
AVERAGE_WINDOW = 7


def load_history(reports_dir):
    days = []
    for path in sorted(Path(reports_dir).glob("????-??-??.json")):
        days.append(json.loads(path.read_text()))
    return days


def rolling_average(history, window=AVERAGE_WINDOW):
    recent = history[-window:]
    totals, counts = {}, {}
    for day in recent:
        for row in day["ranking"]:
            totals[row["id"]] = totals.get(row["id"], 0) + row["score"]
            counts[row["id"]] = counts.get(row["id"], 0) + 1
    return {k: round(totals[k] / counts[k], 1) for k in totals}


def render_markdown(day, history):
    avg = rolling_average(history)
    lines = [
        f"# Market report {day['date']}",
        "",
        f"Days of data: {len(history)}. Quota used today: {day['quota_used']} units.",
        "",
    ]
    if len(history) >= LOCK_AFTER_DAYS:
        leader = max(avg, key=avg.get)
        lines += [f"**Ready to lock a niche.** Leader by {AVERAGE_WINDOW}-day average: `{leader}` ({avg[leader]}).", ""]
    else:
        lines += [f"Collecting data: {LOCK_AFTER_DAYS - len(history)} more days before recommending a niche to lock.", ""]

    lines += [
        f"| # | Niche | Score today | {AVERAGE_WINDOW}-day avg | Median views/hour | Small-channel hit rate | Videos |",
        "|---|---|---|---|---|---|---|",
    ]
    for i, r in enumerate(day["ranking"], 1):
        lines.append(
            f"| {i} | {r['name']} | {r['score']} | {avg.get(r['id'], '')} | {r['median_views_per_hour']:,} "
            f"| {r['small_channel_hit_rate']:.0%} | {r['videos']} |"
        )
    if day["skipped"]:
        lines += ["", f"Skipped (quota): {', '.join(day['skipped'])}"]

    lines += ["", "## Breakout videos (views at least 10x the channel's subscribers)", "",
              "| Niche | Video | Channel | Views | Subs | Ratio |", "|---|---|---|---|---|---|"]
    for v in day["outliers"]:
        title = v["title"].replace("|", "/")
        lines.append(
            f"| {v['niche']} | [{title}](https://youtube.com/shorts/{v['id']}) | {v['channel_title']} "
            f"| {v['views']:,} | {v['channel_subs']:,} | {v['ratio']}x |"
        )
    return "\n".join(lines) + "\n"
