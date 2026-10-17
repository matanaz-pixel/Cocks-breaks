"""Minimal YouTube Data API v3 client (stdlib only, API key auth).

Quota costs per call: search.list = 100 units, videos.list = 1, channels.list = 1.
The default daily quota is 10,000 units per Google Cloud project.
"""
import json
import re
import urllib.parse
import urllib.request

API = "https://www.googleapis.com/youtube/v3/"

SEARCH_COST = 100
LIST_COST = 1


class QuotaExceeded(RuntimeError):
    pass


class YouTubeClient:
    def __init__(self, api_key, quota_budget=5000):
        self.api_key = api_key
        self.quota_budget = quota_budget
        self.quota_used = 0

    def _get(self, endpoint, cost, **params):
        if self.quota_used + cost > self.quota_budget:
            raise QuotaExceeded(f"budget {self.quota_budget} units reached")
        params["key"] = self.api_key
        url = API + endpoint + "?" + urllib.parse.urlencode(params)
        with urllib.request.urlopen(url, timeout=30) as resp:
            data = json.load(resp)
        self.quota_used += cost
        return data

    def search_shorts(self, query, published_after, max_results=50, region="US"):
        """Video ids for recent short videos matching query, most viewed first."""
        data = self._get(
            "search", SEARCH_COST,
            part="id", q=query, type="video", videoDuration="short",
            order="viewCount", publishedAfter=published_after,
            maxResults=max_results, regionCode=region, relevanceLanguage="en",
        )
        return [item["id"]["videoId"] for item in data.get("items", [])]

    def videos(self, ids):
        out = []
        for chunk in _chunks(ids, 50):
            data = self._get("videos", LIST_COST, part="snippet,statistics,contentDetails", id=",".join(chunk))
            out.extend(data.get("items", []))
        return out

    def channels(self, ids):
        out = []
        for chunk in _chunks(ids, 50):
            data = self._get("channels", LIST_COST, part="statistics", id=",".join(chunk))
            out.extend(data.get("items", []))
        return out


def _chunks(items, n):
    items = list(items)
    for i in range(0, len(items), n):
        yield items[i:i + n]


_DURATION = re.compile(r"PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?")


def duration_seconds(iso):
    m = _DURATION.fullmatch(iso or "")
    if not m:
        return None
    h, mi, s = (int(x or 0) for x in m.groups())
    return h * 3600 + mi * 60 + s
