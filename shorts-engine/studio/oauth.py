"""One-time YouTube sign-in on your own computer. Prints the refresh token to store as a GitHub secret.

Uses Google's loopback flow for desktop apps: a browser opens, you sign in with the account
that owns the channel, and Google redirects back to a tiny local server started here.
"""
import http.server
import json
import secrets
import urllib.parse
import urllib.request
import webbrowser

from .youtube_upload import SCOPE, TOKEN_URL

AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"


def authorize(client_secrets_path, port=8765):
    cfg = json.load(open(client_secrets_path))
    cfg = cfg.get("installed") or cfg.get("web")
    redirect = f"http://127.0.0.1:{port}"
    state = secrets.token_urlsafe(16)
    url = AUTH_URL + "?" + urllib.parse.urlencode({
        "client_id": cfg["client_id"], "redirect_uri": redirect, "response_type": "code",
        "scope": SCOPE, "access_type": "offline", "prompt": "consent", "state": state,
    })
    result = {}

    class Handler(http.server.BaseHTTPRequestHandler):
        def do_GET(self):
            q = urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query)
            result.update({k: v[0] for k, v in q.items()})
            self.send_response(200)
            self.end_headers()
            self.wfile.write(b"Signed in. You can close this tab and go back to the terminal.")

        def log_message(self, *a):
            pass

    print("Opening the browser. If it does not open, visit:\n" + url)
    webbrowser.open(url)
    with http.server.HTTPServer(("127.0.0.1", port), Handler) as server:
        server.handle_request()
    if result.get("state") != state or "code" not in result:
        raise SystemExit(f"Sign-in failed: {result.get('error', 'no code returned')}")

    body = urllib.parse.urlencode({
        "code": result["code"], "client_id": cfg["client_id"], "client_secret": cfg["client_secret"],
        "redirect_uri": redirect, "grant_type": "authorization_code",
    }).encode()
    with urllib.request.urlopen(urllib.request.Request(TOKEN_URL, data=body), timeout=30) as resp:
        tokens = json.load(resp)
    print("\nAdd these as GitHub repository secrets:")
    print(f"YT_CLIENT_ID={cfg['client_id']}")
    print(f"YT_CLIENT_SECRET={cfg['client_secret']}")
    print(f"YT_REFRESH_TOKEN={tokens['refresh_token']}")
