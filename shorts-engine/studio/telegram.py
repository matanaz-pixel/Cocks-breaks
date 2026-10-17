"""Approval step over the Telegram Bot API (free).

The bot sends each new video to your chat with Approve / Reject buttons.
Telegram also stores the file, so the uploader later downloads the approved video from there.
Bot downloads are limited to 20 MB, which a 30-60 second Short stays well under.
"""
import json
import mimetypes
import urllib.parse
import urllib.request
import uuid

API = "https://api.telegram.org/bot{token}/{method}"
FILE = "https://api.telegram.org/file/bot{token}/{path}"


class Telegram:
    def __init__(self, token, chat_id):
        self.token, self.chat_id = token, str(chat_id)

    def _call(self, method, params=None, files=None):
        url = API.format(token=self.token, method=method)
        if files:
            body, ctype = _multipart(params or {}, files)
            req = urllib.request.Request(url, data=body, headers={"Content-Type": ctype})
        else:
            req = urllib.request.Request(url, data=json.dumps(params or {}).encode(),
                                         headers={"Content-Type": "application/json"})
        with urllib.request.urlopen(req, timeout=120) as resp:
            data = json.load(resp)
        if not data.get("ok"):
            raise RuntimeError(f"Telegram {method} failed: {data}")
        return data["result"]

    def send_for_approval(self, video_path, item_id, caption):
        keyboard = {"inline_keyboard": [[
            {"text": "✅ Approve", "callback_data": f"ok:{item_id}"},
            {"text": "❌ Reject", "callback_data": f"no:{item_id}"},
        ]]}
        with open(video_path, "rb") as f:
            msg = self._call("sendVideo", {
                "chat_id": self.chat_id, "caption": caption[:1000], "supports_streaming": "true",
                "reply_markup": json.dumps(keyboard),
            }, files={"video": (f"{item_id}.mp4", f.read())})
        return {"message_id": msg["message_id"], "file_id": msg["video"]["file_id"]}

    def updates(self, offset=None):
        params = {"timeout": 0, "allowed_updates": ["callback_query", "message"]}
        if offset is not None:
            params["offset"] = offset
        return self._call("getUpdates", params)

    def decisions(self, offset=None):
        """Returns ({item_id: 'approved'|'rejected'}, next_offset). Only button taps from our chat count."""
        found, next_offset = {}, offset
        for upd in self.updates(offset):
            next_offset = upd["update_id"] + 1
            cb = upd.get("callback_query")
            if not cb or str(cb.get("message", {}).get("chat", {}).get("id")) != self.chat_id:
                continue
            verdict, _, item_id = cb.get("data", "").partition(":")
            if verdict in ("ok", "no") and item_id:
                found[item_id] = "approved" if verdict == "ok" else "rejected"
                self._call("answerCallbackQuery", {"callback_query_id": cb["id"],
                                                   "text": "Approved, uploading soon" if verdict == "ok" else "Rejected"})
        return found, next_offset

    def download(self, file_id, dest):
        info = self._call("getFile", {"file_id": file_id})
        with urllib.request.urlopen(FILE.format(token=self.token, path=info["file_path"]), timeout=300) as resp:
            with open(dest, "wb") as f:
                f.write(resp.read())
        return dest

    def say(self, text):
        return self._call("sendMessage", {"chat_id": self.chat_id, "text": text[:4000],
                                          "disable_web_page_preview": True})


def _multipart(fields, files):
    boundary = uuid.uuid4().hex
    parts = []
    for k, v in fields.items():
        parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n{v}\r\n'.encode())
    for k, (name, content) in files.items():
        ctype = mimetypes.guess_type(name)[0] or "application/octet-stream"
        parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{k}"; filename="{name}"\r\n'
                     f"Content-Type: {ctype}\r\n\r\n".encode() + content + b"\r\n")
    parts.append(f"--{boundary}--\r\n".encode())
    return b"".join(parts), f"multipart/form-data; boundary={boundary}"
