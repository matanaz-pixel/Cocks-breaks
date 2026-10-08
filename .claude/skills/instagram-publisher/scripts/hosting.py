"""Instagram's API fetches media from a public URL, so local files must be hosted first.

Providers (set IG_MEDIA_HOST in .env):
  cloudinary — CLOUDINARY_URL=cloudinary://<key>:<secret>@<cloud_name>   (free tier is enough)
  command    — IG_MEDIA_UPLOAD_CMD='aws s3 cp {path} s3://bucket/{name} --acl public-read && echo https://.../{name}'
               (run without a shell; last stdout line must be the public URL)
  static     — IG_MEDIA_STATIC_DIR=/path/served/by/web  +  IG_MEDIA_BASE_URL=https://example.com/ig/
"""
from __future__ import annotations

import hashlib
import json
import mimetypes
import shutil
import subprocess
import shlex
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from pathlib import Path

from common import die


def _multipart(fields: dict, file_field: str, path: Path) -> tuple[bytes, str]:
    boundary = uuid.uuid4().hex
    parts = []
    for k, v in fields.items():
        parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n{v}\r\n'.encode())
    ctype = mimetypes.guess_type(path.name)[0] or "application/octet-stream"
    parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{file_field}"; filename="{path.name}"\r\n'
                 f"Content-Type: {ctype}\r\n\r\n".encode() + path.read_bytes() + b"\r\n")
    parts.append(f"--{boundary}--\r\n".encode())
    return b"".join(parts), f"multipart/form-data; boundary={boundary}"


def _cloudinary(path: Path, kind: str, env: dict) -> str:
    url = env.get("CLOUDINARY_URL", "")
    p = urllib.parse.urlparse(url)
    if p.scheme != "cloudinary" or not p.username or not p.password or not p.hostname:
        die("CLOUDINARY_URL must look like cloudinary://<api_key>:<api_secret>@<cloud_name>")
    ts = str(int(time.time()))
    public_id = f"ig-{uuid.uuid4().hex[:12]}"
    to_sign = f"public_id={public_id}&timestamp={ts}{p.password}"
    fields = {"api_key": p.username, "timestamp": ts, "public_id": public_id,
              "signature": hashlib.sha1(to_sign.encode()).hexdigest()}
    body, ctype = _multipart(fields, "file", path)
    resource = "video" if kind == "video" else "image"
    req = urllib.request.Request(f"https://api.cloudinary.com/v1_1/{p.hostname}/{resource}/upload", data=body,
                                 headers={"Content-Type": ctype}, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=600) as r:
            return json.loads(r.read().decode())["secure_url"]
    except urllib.error.HTTPError as e:
        die(f"Cloudinary upload failed: {e.read().decode(errors='replace')[:300]}")
    return ""


def _command(path: Path, env: dict) -> str:
    tmpl = env.get("IG_MEDIA_UPLOAD_CMD")
    if not tmpl:
        die("IG_MEDIA_UPLOAD_CMD is not set")
    args = [a.replace("{path}", str(path)).replace("{name}", path.name) for a in shlex.split(tmpl)]
    p = subprocess.run(args, capture_output=True, text=True)
    if p.returncode:
        die(f"Upload command failed: {p.stderr.strip()[-300:]}")
    lines = p.stdout.strip().splitlines()
    if not lines or not lines[-1].startswith("http"):
        die("Upload command must print the public URL as its last stdout line")
    return lines[-1].strip()


def _static(path: Path, env: dict) -> str:
    d, base = env.get("IG_MEDIA_STATIC_DIR"), env.get("IG_MEDIA_BASE_URL")
    if not d or not base:
        die("IG_MEDIA_STATIC_DIR and IG_MEDIA_BASE_URL must both be set")
    name = f"{uuid.uuid4().hex[:10]}-{path.name}"
    Path(d).mkdir(parents=True, exist_ok=True)
    shutil.copyfile(path, Path(d) / name)
    return base.rstrip("/") + "/" + urllib.parse.quote(name)


def verify_public(url: str, wait: int = 90) -> None:
    """Instagram rejects URLs that redirect to HTML / 404 — check before burning an API call."""
    deadline = time.time() + wait
    last = ""
    while time.time() < deadline:
        try:
            req = urllib.request.Request(url, headers={"Range": "bytes=0-0", "User-Agent": "facebookexternalhit/1.1"})
            with urllib.request.urlopen(req, timeout=20) as r:
                ctype = r.headers.get("Content-Type", "")
                if r.status in (200, 206) and ("image" in ctype or "video" in ctype or "octet-stream" in ctype):
                    return
                last = f"status {r.status}, content-type {ctype}"
        except Exception as e:  # noqa: BLE001
            last = str(e)
        time.sleep(3)
    die(f"Hosted URL is not publicly fetchable as media: {url} ({last})")


def upload(path: Path, kind: str, env: dict) -> str:
    host = (env.get("IG_MEDIA_HOST") or "").lower()
    if host == "cloudinary":
        url = _cloudinary(path, kind, env)
    elif host == "command":
        url = _command(path, env)
    elif host == "static":
        url = _static(path, env)
    else:
        die("No media host configured. Set IG_MEDIA_HOST to cloudinary | command | static in .env (see references/setup-he.md).")
    if not env.get("IG_SKIP_URL_CHECK"):
        verify_public(url)
    return url
