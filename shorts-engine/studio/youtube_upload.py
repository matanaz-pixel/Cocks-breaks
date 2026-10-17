"""Upload a video to YouTube with the Data API v3 (resumable upload, OAuth refresh token).

One-time setup creates a refresh token: python -m studio auth (see README).
videos.insert costs 1,600 quota units, so the default 10,000/day allows about 6 uploads.
Note: until the Google Cloud app passes Google's audit, uploads from it are locked to private.
"""
import json
import os
import urllib.parse
import urllib.request

TOKEN_URL = "https://oauth2.googleapis.com/token"
UPLOAD_URL = "https://www.googleapis.com/upload/youtube/v3/videos?uploadType=resumable&part=snippet,status"
SCOPE = "https://www.googleapis.com/auth/youtube.upload"


def access_token(client_id, client_secret, refresh_token):
    body = urllib.parse.urlencode({
        "client_id": client_id, "client_secret": client_secret,
        "refresh_token": refresh_token, "grant_type": "refresh_token",
    }).encode()
    with urllib.request.urlopen(urllib.request.Request(TOKEN_URL, data=body), timeout=30) as resp:
        return json.load(resp)["access_token"]


def build_metadata(story, privacy="public", publish_at=None):
    desc = story["description"].strip()
    desc += "\n\nSources:\n" + "\n".join(story.get("sources", []))
    desc += "\n\n#shorts #business #money #history"
    status = {
        "privacyStatus": "private" if publish_at else privacy,
        "selfDeclaredMadeForKids": False,
        # Narration is synthetic but nothing here depicts a real person or event realistically,
        # so YouTube's altered-content label does not apply. Flip to True if that changes.
        "containsSyntheticMedia": False,
    }
    if publish_at:
        status["publishAt"] = publish_at
    title = story["title"]
    if "#shorts" not in title.lower() and len(title) + 8 <= 100:
        title += " #shorts"
    return {
        "snippet": {"title": title[:100], "description": desc[:4900], "tags": story.get("tags", [])[:15],
                    "categoryId": "27", "defaultLanguage": "en", "defaultAudioLanguage": "en"},
        "status": status,
    }


def upload(video_path, metadata, token):
    size = os.path.getsize(video_path)
    start = urllib.request.Request(UPLOAD_URL, data=json.dumps(metadata).encode(), method="POST", headers={
        "Authorization": f"Bearer {token}", "Content-Type": "application/json; charset=UTF-8",
        "X-Upload-Content-Type": "video/mp4", "X-Upload-Content-Length": str(size),
    })
    with urllib.request.urlopen(start, timeout=60) as resp:
        session = resp.headers["Location"]
    with open(video_path, "rb") as f:
        put = urllib.request.Request(session, data=f.read(), method="PUT", headers={
            "Authorization": f"Bearer {token}", "Content-Type": "video/mp4", "Content-Length": str(size),
        })
    with urllib.request.urlopen(put, timeout=600) as resp:
        return json.load(resp)["id"]
