"""Daily collection: recent top Shorts per candidate niche, with channel sizes."""
from datetime import datetime, timedelta, timezone

from .youtube import QuotaExceeded, duration_seconds

MAX_SHORT_SECONDS = 180


def collect(client, niches, now=None, lookback_days=7):
    now = now or datetime.now(timezone.utc)
    since = (now - timedelta(days=lookback_days)).strftime("%Y-%m-%dT%H:%M:%SZ")
    day = now.timetuple().tm_yday
    snapshot = {"collected_at": now.isoformat(), "lookback_days": lookback_days, "niches": {}, "skipped": []}

    for niche in niches:
        # One search per niche per day keeps the run near 3,100 units; queries rotate daily.
        query = niche["queries"][day % len(niche["queries"])]
        try:
            ids = client.search_shorts(query, since)
            videos = [v for v in client.videos(ids) if _is_short(v)]
            subs = _subscriber_counts(client, {v["snippet"]["channelId"] for v in videos})
        except QuotaExceeded:
            snapshot["skipped"].append(niche["id"])
            continue
        snapshot["niches"][niche["id"]] = {
            "query": query,
            "videos": [_flatten(v, subs, now) for v in videos],
        }

    snapshot["quota_used"] = client.quota_used
    return snapshot


def _is_short(video):
    seconds = duration_seconds(video.get("contentDetails", {}).get("duration"))
    return seconds is not None and seconds <= MAX_SHORT_SECONDS


def _subscriber_counts(client, channel_ids):
    counts = {}
    for ch in client.channels(sorted(channel_ids)):
        stats = ch.get("statistics", {})
        # Channels can hide their subscriber count; treat that as unknown.
        counts[ch["id"]] = None if stats.get("hiddenSubscriberCount") else int(stats.get("subscriberCount", 0))
    return counts


def _flatten(video, subs, now):
    sn, st = video["snippet"], video.get("statistics", {})
    published = datetime.fromisoformat(sn["publishedAt"].replace("Z", "+00:00"))
    hours = max((now - published).total_seconds() / 3600, 1.0)
    views = int(st.get("viewCount", 0))
    return {
        "id": video["id"],
        "title": sn.get("title", ""),
        "channel_id": sn["channelId"],
        "channel_title": sn.get("channelTitle", ""),
        "published_at": sn["publishedAt"],
        "views": views,
        "likes": int(st.get("likeCount", 0)),
        "comments": int(st.get("commentCount", 0)),
        "views_per_hour": round(views / hours, 1),
        "channel_subs": subs.get(sn["channelId"]),
        "duration_s": duration_seconds(video["contentDetails"]["duration"]),
    }
