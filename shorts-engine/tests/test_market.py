import json
import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path

from market.collect import collect
from market.report import load_history, render_markdown
from market.score import outliers, rank
from market.youtube import QuotaExceeded, duration_seconds

NOW = datetime(2026, 10, 15, 12, tzinfo=timezone.utc)

NICHES = [
    {"id": "finance", "name": "Finance", "queries": ["money"], "rpm_tier": 5, "ai_fit": 4, "policy_risk": 0.1},
    {"id": "riddles", "name": "Riddles", "queries": ["riddle"], "rpm_tier": 1, "ai_fit": 5, "policy_risk": 0.1},
    {"id": "kids", "name": "Kids", "queries": ["kids"], "rpm_tier": 1, "ai_fit": 3, "policy_risk": 0.5},
]


def video(vid, channel, views, duration="PT45S", published="2026-10-14T12:00:00Z"):
    return {
        "id": vid,
        "snippet": {"title": f"title {vid}", "channelId": channel, "channelTitle": channel, "publishedAt": published},
        "statistics": {"viewCount": str(views)},
        "contentDetails": {"duration": duration},
    }


class FakeClient:
    """Stands in for YouTubeClient; spends quota like the real one."""

    def __init__(self, results, subs, budget=10_000):
        self.results, self.subs, self.budget, self.quota_used = results, subs, budget, 0

    def _spend(self, cost):
        if self.quota_used + cost > self.budget:
            raise QuotaExceeded()
        self.quota_used += cost

    def search_shorts(self, query, since):
        self._spend(100)
        return [v["id"] for v in self.results[query]]

    def videos(self, ids):
        self._spend(1)
        every = {v["id"]: v for vs in self.results.values() for v in vs}
        return [every[i] for i in ids]

    def channels(self, ids):
        self._spend(1)
        return [{"id": c, "statistics": {"subscriberCount": str(self.subs[c])}} for c in ids]


def fake_client(budget=10_000):
    results = {
        "money": [video("f1", "smallfin", 400_000), video("f2", "bigfin", 900_000), video("f3", "midfin", 150_000)],
        "riddle": [video("r1", "bigriddle", 2_000_000), video("r2", "bigriddle", 50_000),
                   video("r3", "longvid", 10_000_000, duration="PT8M")],
        "kids": [video("k1", "kidsch", 5_000_000)],
    }
    subs = {"smallfin": 20_000, "bigfin": 3_000_000, "midfin": 400_000, "bigriddle": 5_000_000,
            "longvid": 100, "kidsch": 10_000_000}
    return FakeClient(results, subs, budget)


class MarketTests(unittest.TestCase):
    def test_duration_parsing(self):
        self.assertEqual(duration_seconds("PT45S"), 45)
        self.assertEqual(duration_seconds("PT2M5S"), 125)
        self.assertEqual(duration_seconds("PT1H"), 3600)
        self.assertIsNone(duration_seconds("bogus"))

    def test_collect_drops_long_videos_and_attaches_subs(self):
        snap = collect(fake_client(), NICHES, now=NOW)
        riddle_ids = [v["id"] for v in snap["niches"]["riddles"]["videos"]]
        self.assertNotIn("r3", riddle_ids)
        f1 = snap["niches"]["finance"]["videos"][0]
        self.assertEqual(f1["channel_subs"], 20_000)
        self.assertEqual(f1["views_per_hour"], round(400_000 / 24, 1))

    def test_collect_skips_niches_when_quota_runs_out(self):
        snap = collect(fake_client(budget=210), NICHES, now=NOW)
        self.assertEqual(list(snap["niches"]), ["finance", "riddles"])
        self.assertEqual(snap["skipped"], ["kids"])

    def test_rpm_outweighs_raw_views(self):
        snap = collect(fake_client(), NICHES, now=NOW)
        ids = [r["id"] for r in rank(snap, NICHES)]
        # Riddles and kids get more views per video, but finance pays far more per view.
        self.assertEqual(ids[0], "finance")
        self.assertEqual(ids[-1], "kids")

    def test_outliers_need_views_far_above_subscribers(self):
        snap = collect(fake_client(), NICHES, now=NOW)
        found = outliers(snap)
        self.assertEqual([v["id"] for v in found], ["f1"])
        self.assertEqual(found[0]["ratio"], 20.0)

    def test_report_waits_for_enough_days_before_recommending(self):
        snap = collect(fake_client(), NICHES, now=NOW)
        day = {"date": "2026-10-15", "quota_used": snap["quota_used"], "skipped": [],
               "ranking": rank(snap, NICHES), "outliers": outliers(snap)}
        with tempfile.TemporaryDirectory() as d:
            for i in range(1, 15):
                Path(d, f"2026-10-{i:02d}.json").write_text(json.dumps(day))
            history = load_history(d)
            self.assertIn("Ready to lock a niche", render_markdown(day, history))
            self.assertIn("more days", render_markdown(day, history[:3]))


if __name__ == "__main__":
    unittest.main()
