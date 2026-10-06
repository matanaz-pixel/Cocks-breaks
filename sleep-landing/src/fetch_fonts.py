#!/usr/bin/env python3
# מוריד את הגופנים של גרסה 2 (עברית + לטינית בלבד) ומשבץ אותם כ-base64 ב-fonts/v2.css.
# מריצים פעם אחת. אין צורך להריץ שוב אלא אם מחליפים גופנים.
import re, base64, urllib.request, pathlib
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
URL = "https://fonts.googleapis.com/css2?family=Assistant:wght@300..700&family=Varela+Round&display=swap"
def get(u):
    return urllib.request.urlopen(urllib.request.Request(u, headers={"User-Agent": UA}), timeout=30).read()
css = get(URL).decode()
out = []
for m in re.finditer(r"/\* (\S+) \*/\s*@font-face \{(.*?)\}", css, re.S):
    subset, body = m.groups()
    if subset not in ("hebrew", "latin"):
        continue
    url = re.search(r"url\((.*?)\)", body).group(1)
    data = base64.b64encode(get(url)).decode()
    body = re.sub(r"url\(.*?\) format\('woff2'\)", f"url(data:font/woff2;base64,{data}) format('woff2')", body)
    out.append("@font-face {" + re.sub(r"\s+", " ", body).strip() + "}")
p = pathlib.Path(__file__).parent / "fonts" / "v2.css"
p.write_text("\n".join(out), encoding="utf-8")
print(len(out), "faces,", p.stat().st_size // 1024, "KB")
