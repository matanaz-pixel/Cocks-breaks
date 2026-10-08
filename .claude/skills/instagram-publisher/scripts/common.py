"""Shared paths, .env handling, config and the Instagram Graph API client (stdlib only)."""
from __future__ import annotations

import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

SKILL_DIR = Path(__file__).resolve().parent.parent
# IG_SKILL_HOME relocates all state (brand/, drafts/, cache/, .env) — used by the tests.
DATA_DIR = Path(os.environ.get("IG_SKILL_HOME") or SKILL_DIR)
BRAND_DIR = DATA_DIR / "brand"
DRAFTS_DIR = DATA_DIR / "drafts"
CACHE_DIR = DATA_DIR / "cache"
ENV_FILE = DATA_DIR / ".env"

DEFAULT_CONFIG = {
    "timezone": "Asia/Jerusalem",
    "default_ratio": "4:5",       # feed images / carousels
    "tone_strength": 0.35,        # 0 = never touch the photo, 1 = full match to the feed average
    "max_caption_chars": 2200,
    "max_hashtags": 30,
}


def die(msg: str, code: int = 1) -> None:
    print(f"ERROR: {msg}", file=sys.stderr)
    sys.exit(code)


# ---------------------------------------------------------------- env / config
def load_env() -> dict:
    """Values from .env, overridden by real environment variables."""
    values: dict = {}
    if ENV_FILE.exists():
        for line in ENV_FILE.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            k, v = line.split("=", 1)
            values[k.strip()] = v.strip().strip('"').strip("'")
    for k, v in os.environ.items():
        if k.startswith(("IG_", "CLOUDINARY_", "FB_")) and k != "IG_SKILL_HOME":
            values[k] = v
    return values


def write_env(updates: dict) -> None:
    """Merge `updates` into .env (keeps unrelated lines) and lock the file down to the owner."""
    lines = ENV_FILE.read_text(encoding="utf-8").splitlines() if ENV_FILE.exists() else []
    seen = set()
    out = []
    for line in lines:
        key = line.split("=", 1)[0].strip()
        if key in updates:
            out.append(f"{key}={updates[key]}")
            seen.add(key)
        else:
            out.append(line)
    for k, v in updates.items():
        if k not in seen:
            out.append(f"{k}={v}")
    ENV_FILE.write_text("\n".join(out) + "\n", encoding="utf-8")
    try:
        ENV_FILE.chmod(0o600)
    except OSError:
        pass


def load_config() -> dict:
    cfg = dict(DEFAULT_CONFIG)
    path = BRAND_DIR / "config.json"
    if path.exists():
        try:
            cfg.update(json.loads(path.read_text(encoding="utf-8")))
        except json.JSONDecodeError:
            die(f"{path} is not valid JSON")
    return cfg


def read_json(path: Path, default=None):
    if not path.exists():
        return default
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(path: Path, data) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(path.suffix + ".tmp")
    tmp.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    tmp.replace(path)


# ---------------------------------------------------------------- Graph API
class GraphError(Exception):
    def __init__(self, message, code=None, subcode=None, http_status=None, user_msg=None):
        super().__init__(message)
        self.code, self.subcode, self.http_status, self.user_msg = code, subcode, http_status, user_msg

    def hint(self) -> str:
        if self.code == 190:
            return "Access token is invalid/expired. Re-run: python3 scripts/ig.py auth-setup"
        if self.code in (10, 200) or self.code == 100 and "permission" in str(self).lower():
            return "Missing permission. Required: instagram_basic, instagram_content_publish, pages_show_list."
        if self.code == 9007 or self.subcode == 2207027:
            return "Media is not ready yet — wait and poll the container status."
        if self.subcode in (2207026, 2207003, 2207020):
            return "Media URL could not be fetched by Instagram — check the hosting URL is public and a direct file link."
        if self.code == 4 or self.code == 17 or self.code == 32:
            return "Rate limited by Meta. Wait before retrying."
        return ""


class Graph:
    """Minimal Graph API client. Retries only idempotent GETs."""

    def __init__(self, token: str | None = None, env: dict | None = None):
        env = env or load_env()
        self.token = token or env.get("IG_ACCESS_TOKEN")
        self.base = env.get("IG_GRAPH_BASE", "https://graph.facebook.com").rstrip("/")
        self.version = env.get("IG_GRAPH_VERSION", "v23.0")
        self.ig_user_id = env.get("IG_USER_ID")

    def _redact(self, text: str) -> str:
        return text.replace(self.token, "***") if self.token else text

    def _url(self, path: str) -> str:
        if path.startswith("http"):
            return path
        return f"{self.base}/{self.version}/{path.lstrip('/')}"

    def request(self, method: str, path: str, params: dict | None = None, retries: int | None = None):
        if not self.token:
            raise GraphError("No access token. Run: python3 scripts/ig.py auth-setup")
        params = {k: v for k, v in (params or {}).items() if v is not None}
        params["access_token"] = self.token
        data = None
        url = self._url(path)
        if method == "GET":
            url += ("&" if "?" in url else "?") + urllib.parse.urlencode(params)
        else:
            data = urllib.parse.urlencode(params).encode()
        attempts = (3 if method == "GET" else 1) if retries is None else retries
        last: Exception | None = None
        for i in range(attempts):
            req = urllib.request.Request(url, data=data, method=method)
            try:
                with urllib.request.urlopen(req, timeout=60) as resp:
                    return json.loads(resp.read().decode() or "{}")
            except urllib.error.HTTPError as e:
                body = e.read().decode(errors="replace")
                try:
                    err = json.loads(body).get("error", {})
                except json.JSONDecodeError:
                    err = {"message": body[:300]}
                last = GraphError(
                    self._redact(err.get("message", "HTTP error")),
                    err.get("code"), err.get("error_subcode"), e.code, err.get("error_user_msg"),
                )
                if e.code < 500 and err.get("code") not in (1, 2):
                    raise last
            except (urllib.error.URLError, TimeoutError) as e:
                last = GraphError(f"Network error: {self._redact(str(e))}")
            if i < attempts - 1:
                time.sleep(2 ** (i + 1))
        assert last is not None
        raise last

    def get(self, path, **params):
        return self.request("GET", path, params)

    def post(self, path, **params):
        return self.request("POST", path, params)

    def paginate(self, path: str, max_items: int, **params):
        """Yield items following paging.next until `max_items` items were produced."""
        got = 0
        page = self.get(path, **params)
        while True:
            for item in page.get("data", []):
                yield item
                got += 1
                if got >= max_items:
                    return
            nxt = page.get("paging", {}).get("next")
            if not nxt:
                return
            # `next` already carries the access token; call it without re-adding params
            with urllib.request.urlopen(nxt, timeout=60) as resp:
                page = json.loads(resp.read().decode())


def require_ig(env: dict | None = None) -> Graph:
    env = env or load_env()
    if not env.get("IG_ACCESS_TOKEN") or not env.get("IG_USER_ID"):
        die("Instagram is not connected yet. Run: python3 scripts/ig.py auth-setup  (see references/setup-he.md)")
    return Graph(env=env)


def slugify(s: str) -> str:
    return re.sub(r"[^a-zA-Z0-9_-]+", "-", s).strip("-").lower()
