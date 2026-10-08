#!/usr/bin/env python3
"""End-to-end test against a fake Instagram Graph server (no network, no real account).

Run:  python3 tests/test_flow.py
"""
import json
import os
import subprocess
import sys
import tempfile
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
IG = [sys.executable, str(ROOT / "scripts" / "ig.py")]
STATE = {"posts": [], "calls": [], "polls": {}, "next_id": 1000}
HOST_DIR = {}


def jpg(path, color, size=(800, 1000)):
    im = Image.new("RGB", size, color)
    ImageDraw.Draw(im).ellipse([100, 200, 700, 800], fill=(255, 255, 255))
    im.save(path, "JPEG")


class Fake(BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def _json(self, obj, code=200):
        b = json.dumps(obj).encode()
        self.send_response(code); self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(b))); self.end_headers(); self.wfile.write(b)

    def do_GET(self):
        u = urlparse(self.path); q = {k: v[0] for k, v in parse_qs(u.query).items()}
        if u.path.startswith("/img/"):
            data = (Path(HOST_DIR["feed"]) / u.path.split("/")[-1]).read_bytes()
            return self._raw(data, "image/jpeg")
        if u.path.startswith("/hosted/"):
            data = (Path(HOST_DIR["hosted"]) / Path(u.path).name.replace("%20", " ")).read_bytes()
            return self._raw(data, "image/jpeg")
        assert q.get("access_token") == "TESTTOKEN", "token missing"
        parts = u.path.strip("/").split("/")[1:]
        if parts[-1] == "media" and parts[0] == "17841":
            return self._json({"data": STATE["posts"]})
        if parts[-1] == "content_publishing_limit":
            return self._json({"data": [{"quota_usage": 2, "config": {"quota_total": 100}}]})
        if parts[0].startswith("C"):
            n = STATE["polls"][parts[0]] = STATE["polls"].get(parts[0], 0) + 1
            return self._json({"status_code": "FINISHED" if n >= 2 else "IN_PROGRESS", "id": parts[0]})
        if parts[0].startswith("M"):
            return self._json({"permalink": f"https://instagram.com/p/{parts[0]}/", "id": parts[0]})
        self._json({"error": {"message": "unknown", "code": 100}}, 400)

    def _raw(self, data, ctype):
        self.send_response(200); self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data))); self.end_headers(); self.wfile.write(data)

    def do_POST(self):
        u = urlparse(self.path)
        body = {k: v[0] for k, v in parse_qs(self.rfile.read(int(self.headers.get("Content-Length", 0))).decode()).items()}
        assert body.get("access_token") == "TESTTOKEN"
        parts = u.path.strip("/").split("/")[1:]
        STATE["calls"].append((parts[-1], body))
        STATE["next_id"] += 1
        if parts[-1] == "media":
            return self._json({"id": f"C{STATE['next_id']}"})
        if parts[-1] == "media_publish":
            return self._json({"id": f"M{STATE['next_id']}"})
        if parts[-1] == "comments":
            return self._json({"id": "K1"})
        self._json({"error": {"message": "unknown", "code": 100}}, 400)


def run(args, env, ok=True):
    p = subprocess.run(IG + args, capture_output=True, text=True, env=env)
    if ok and p.returncode:
        raise SystemExit(f"FAILED {args}\n{p.stdout}\n{p.stderr}")
    return p


def main():
    tmp = Path(tempfile.mkdtemp())
    feed, hosted, inbox = tmp / "feed", tmp / "hosted", tmp / "inbox"
    for d in (feed, hosted, inbox):
        d.mkdir()
    HOST_DIR.update(feed=feed, hosted=hosted)
    srv = HTTPServer(("127.0.0.1", 0), Fake)
    port = srv.server_address[1]
    threading.Thread(target=srv.serve_forever, daemon=True).start()
    base = f"http://127.0.0.1:{port}"
    colors = [(200, 120, 60), (210, 130, 70), (190, 110, 50), (220, 140, 80), (180, 100, 40), (205, 125, 65)]
    for i, c in enumerate(colors):
        jpg(feed / f"{i}.jpg", c)
        STATE["posts"].append({
            "id": f"P{i}", "media_type": "IMAGE", "media_url": f"{base}/img/{i}.jpg", "permalink": f"https://ig/p/{i}",
            "timestamp": f"2026-09-{10 + i}T17:30:00+0000", "like_count": 10 * (i + 1), "comments_count": i,
            "caption": f"פוסט מספר {i} 🍕🔥\nטקסט קצר לדוגמה\n\n#פיצה #אוכל #רוברטה"})

    env = {**os.environ, "IG_SKILL_HOME": str(tmp / "home"), "IG_GRAPH_BASE": base,
           "IG_ACCESS_TOKEN": "TESTTOKEN", "IG_USER_ID": "17841", "IG_USERNAME": "test_account",
           "IG_MEDIA_HOST": "static", "IG_MEDIA_STATIC_DIR": str(hosted), "IG_MEDIA_BASE_URL": f"{base}/hosted"}
    (tmp / "home").mkdir()

    # 1) learn --------------------------------------------------------------
    r = run(["learn", "--limit", "10"], env)
    data = json.loads((tmp / "home/brand/style-data.json").read_text())
    assert data["posts_analyzed"] == 6, data["posts_analyzed"]
    assert data["caption"]["hashtags_per_post_median"] == 3
    assert data["caption"]["hebrew_letter_share"] > 0.5
    assert data["palette_overall"] and data["top_performers"][0]["engagement"] > 0
    assert (tmp / "home/brand/contact-sheet-1-top-performing.jpg").exists()
    print("learn ✓", data["tone"])

    # 2) single image: draft → publish blocked until approved → publish -----------
    src = inbox / "pizza.jpg"
    jpg(src, (90, 140, 200), size=(3000, 2000))
    cap = tmp / "cap.txt"; cap.write_text("פיצה חדשה בתפריט 🍕\n\n#פיצה", encoding="utf-8")
    r = run(["draft", str(src), "--caption-file", str(cap), "--text", "שלום עולם", "--text-pos", "center"], env)
    info = json.loads(r.stdout[r.stdout.index("{"):])
    did = info["draft_id"]; assert info["kind"] == "IMAGE" and not info["errors"], info
    assert Path(info["preview"][0]).exists()
    p = run(["publish", did], env, ok=False)
    assert p.returncode != 0 and "not approved" in p.stderr, p.stderr
    run(["publish", did, "--dry-run"], env, ok=False)
    run(["approve", did], env)
    r = run(["publish", did], env)
    assert "PUBLISHED" in r.stdout, r.stdout
    kinds = [c[0] for c in STATE["calls"]]
    assert kinds == ["media", "media_publish"], kinds
    assert STATE["calls"][0][1]["image_url"].startswith(base) and "caption" in STATE["calls"][0][1]
    p = run(["publish", did], env, ok=False)
    assert p.returncode != 0 and "Already published" in p.stderr
    print("single image ✓")

    # 3) carousel ------------------------------------------------------------
    STATE["calls"].clear()
    files = []
    for i in range(3):
        f = inbox / f"s{i}.jpg"; jpg(f, (50 * i, 100, 200)); files.append(str(f))
    r = run(["draft", *files, "--caption", "קרוסלה", "--alt", "a", "--alt", "b", "--first-comment-file", str(cap)], env)
    info = json.loads(r.stdout[r.stdout.index("{"):]); did = info["draft_id"]
    assert info["kind"] == "CAROUSEL" and len(info["preview"]) == 2
    run(["approve", did], env); run(["publish", did], env)
    kinds = [c[0] for c in STATE["calls"]]
    assert kinds == ["media"] * 4 + ["media_publish", "comments"], kinds
    assert STATE["calls"][0][1]["is_carousel_item"] == "true"
    assert STATE["calls"][3][1]["media_type"] == "CAROUSEL" and STATE["calls"][3][1]["children"].count(",") == 2
    print("carousel ✓")

    # 4) reel ------------------------------------------------------------------
    STATE["calls"].clear()
    vid = inbox / "clip.mp4"
    subprocess.run(["ffmpeg", "-y", "-f", "lavfi", "-i", "testsrc=size=1280x720:rate=24:duration=4",
                    "-f", "lavfi", "-i", "sine=frequency=440:duration=4", "-c:v", "libx264", "-c:a", "aac",
                    "-pix_fmt", "yuv420p", "-shortest", str(vid)], capture_output=True, check=True)
    r = run(["draft", str(vid), "--caption", "ריל"], env)
    info = json.loads(r.stdout[r.stdout.index("{"):]); did = info["draft_id"]
    assert info["kind"] == "REELS", info
    run(["approve", did], env)
    # fake host serves jpeg content-type only; skip the media-type check for the video upload in this test
    env2 = {**env, "IG_SKIP_URL_CHECK": "1"}
    run(["publish", did], env2)
    assert STATE["calls"][0][1]["media_type"] == "REELS" and STATE["calls"][0][1]["video_url"].endswith("02.mp4") is False
    print("reel ✓")

    # 5) validation catches bad input ------------------------------------------
    r = run(["draft", str(src), "--caption", "#a " * 31], env, ok=False)
    assert r.returncode == 3 and "hashtags" in r.stdout
    print("validation ✓")

    # 6) panel side alternates between published posts --------------------------------
    def side_of(draft_id):
        return json.loads((tmp / "home/drafts" / draft_id / "draft.json").read_text())["panel_side"]
    cfg_path = tmp / "home/brand/config.json"   # the test home has default config (no 'alternate'): opt in like the real brand config
    cfg = json.loads(cfg_path.read_text()); cfg["split"] = {"panel_side": "alternate"}
    cfg_path.write_text(json.dumps(cfg))
    split = ["--layout", "split", "--text", "בדיקה", "--no-tone"]
    r = run(["draft", str(src), "--caption", "א", *split], env)
    d1 = json.loads(r.stdout[r.stdout.index("{"):])["draft_id"]
    assert side_of(d1) == "left", side_of(d1)
    run(["approve", d1], env); run(["publish", d1], env)
    r = run(["draft", str(src), "--caption", "ב", *split], env)
    d2 = json.loads(r.stdout[r.stdout.index("{"):])["draft_id"]
    assert side_of(d2) == "right", side_of(d2)
    print("alternating panel ✓")
    print("\nALL TESTS PASSED")


if __name__ == "__main__":
    main()
