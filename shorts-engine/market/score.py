"""Turn a collected snapshot into a ranked niche table and a list of breakout videos.

The goal is money from views, so earnings per view (rpm_tier) carries the largest weight.
"""
from statistics import median

WEIGHTS = {"rpm": 0.40, "demand": 0.25, "opportunity": 0.20, "ai_fit": 0.15}
SMALL_CHANNEL_SUBS = 50_000
SMALL_CHANNEL_MIN_VIEWS = 100_000
OUTLIER_RATIO = 10
OUTLIER_MIN_VIEWS = 50_000


def niche_metrics(videos):
    if not videos:
        return {"videos": 0, "demand": 0.0, "small_hits": 0.0, "diversity": 0.0}
    known = [v for v in videos if v["channel_subs"] is not None]
    hits = [v for v in known if v["channel_subs"] < SMALL_CHANNEL_SUBS and v["views"] >= SMALL_CHANNEL_MIN_VIEWS]
    return {
        "videos": len(videos),
        "demand": median(v["views_per_hour"] for v in videos),
        "small_hits": len(hits) / len(known) if known else 0.0,
        "diversity": len({v["channel_id"] for v in videos}) / len(videos),
    }


def outliers(snapshot, limit=20):
    found = []
    for niche_id, data in snapshot["niches"].items():
        for v in data["videos"]:
            subs = v["channel_subs"]
            if subs and v["views"] >= OUTLIER_MIN_VIEWS and v["views"] >= OUTLIER_RATIO * subs:
                found.append({**v, "niche": niche_id, "ratio": round(v["views"] / subs, 1)})
    found.sort(key=lambda v: v["ratio"], reverse=True)
    return found[:limit]


def rank(snapshot, niches):
    by_id = {n["id"]: n for n in niches}
    metrics = {nid: niche_metrics(d["videos"]) for nid, d in snapshot["niches"].items() if nid in by_id}
    demand_pct = _percentiles({k: m["demand"] for k, m in metrics.items()})
    opp_raw = {k: 0.7 * m["small_hits"] + 0.3 * m["diversity"] for k, m in metrics.items()}
    opp_pct = _percentiles(opp_raw)

    rows = []
    for nid, m in metrics.items():
        n = by_id[nid]
        parts = {
            "rpm": (n["rpm_tier"] - 1) / 4,
            "demand": demand_pct[nid],
            "opportunity": opp_pct[nid],
            "ai_fit": (n["ai_fit"] - 1) / 4,
        }
        base = sum(WEIGHTS[k] * parts[k] for k in WEIGHTS)
        rows.append({
            "id": nid,
            "name": n["name"],
            "score": round(100 * base * (1 - n["policy_risk"]), 1),
            "median_views_per_hour": round(m["demand"], 1),
            "small_channel_hit_rate": round(m["small_hits"], 2),
            "videos": m["videos"],
            **{f"part_{k}": round(v, 2) for k, v in parts.items()},
        })
    rows.sort(key=lambda r: r["score"], reverse=True)
    return rows


def _percentiles(values):
    """Rank each value against the others: 0 for the lowest, 1 for the highest."""
    if len(values) < 2:
        return {k: 0.5 for k in values}
    ordered = sorted(values.values())
    n = len(ordered) - 1
    return {k: ordered.index(v) / n for k, v in values.items()}
