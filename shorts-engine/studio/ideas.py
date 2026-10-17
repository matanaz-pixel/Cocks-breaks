"""Write new stories with Gemini's free tier when the bank runs low (optional: needs GEMINI_API_KEY).

Breakout titles from the market report are passed in as inspiration. Every generated story
is marked needs_review and must cite sources; the Telegram approval step is the human check.
"""
import json
import os
import re
import urllib.request

MODEL = os.environ.get("GEMINI_MODEL", "gemini-2.5-flash")
URL = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={key}"
SHOW_TYPES = {"title", "year", "number", "text", "versus", "end"}

PROMPT = """You write scripts for "Billion Dollar Blunders", a YouTube Shorts channel about famous,
well-documented business and money mistakes. Write {n} NEW stories, none about: {used}.

Rules:
- Only facts that are well documented on Wikipedia or major news outlets. If unsure, pick another story.
  Use "reportedly" for disputed claims. No living private individuals, no tragedies, no medical or legal claims.
- 6 or 7 beats, 95 to 130 spoken words in total. Beat 1 is a hook under 15 words.
  The last beat asks a question and ends with "Follow for more billion dollar blunders."
- Numbers in "say" are written as words; numbers in "show" are digits.
- Each beat has "say" (narration) and "show", one of:
  {{"type":"title","text":"..."}}  (3-6 words, put the key word in CAPS)
  {{"type":"year","value":1999,"label":"..."}}
  {{"type":"number","value":50,"prefix":"$","suffix":"M","label":"..."}}
  {{"type":"text","text":"..."}}  (2-6 words)
  {{"type":"versus","left":"$50M","left_label":"...","right":"$300B+","right_label":"..."}}
  {{"type":"end","text":"..."}}  (last beat only)
- id: kebab-case. title: under 60 characters. description: 1-2 sentences. tags: 5 lowercase.
- sources: 1-3 URLs you are confident exist.

Recent breakout Shorts for inspiration (do not copy them): {trends}

Return JSON: {{"stories":[{{"id":..., "title":..., "description":..., "tags":[...], "sources":[...], "beats":[...]}}]}}"""


def validate(story):
    problems = []
    for key in ("id", "title", "description", "tags", "sources", "beats"):
        if not story.get(key):
            problems.append(f"missing {key}")
    beats = story.get("beats", [])
    if not 5 <= len(beats) <= 8:
        problems.append("needs 5-8 beats")
    words = sum(len(b.get("say", "").split()) for b in beats)
    if not 70 <= words <= 160:
        problems.append(f"{words} spoken words")
    for b in beats:
        show = b.get("show", {})
        if show.get("type") not in SHOW_TYPES:
            problems.append(f"bad show type {show.get('type')}")
        if show.get("type") in ("year", "number") and not isinstance(show.get("value"), (int, float)):
            problems.append("numeric show needs a number value")
    if beats and beats[-1].get("show", {}).get("type") != "end":
        problems.append("last beat must be type end")
    if not re.fullmatch(r"[a-z0-9-]+", story.get("id", "")):
        problems.append("id must be kebab-case")
    return problems


def generate(n, used_ids, trends, key=None):
    key = key or os.environ["GEMINI_API_KEY"]
    body = {
        "contents": [{"parts": [{"text": PROMPT.format(n=n, used=", ".join(used_ids) or "none",
                                                       trends="; ".join(trends[:15]) or "none")}]}],
        "generationConfig": {"responseMimeType": "application/json", "temperature": 0.9},
    }
    req = urllib.request.Request(URL.format(model=MODEL, key=key), data=json.dumps(body).encode(),
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=120) as resp:
        data = json.load(resp)
    text = data["candidates"][0]["content"]["parts"][0]["text"]
    stories = json.loads(text)["stories"]
    good = []
    for s in stories:
        problems = validate(s)
        if problems or s["id"] in used_ids:
            print(f"Dropped generated story {s.get('id')}: {problems or 'duplicate'}")
            continue
        s["needs_review"] = True
        good.append(s)
    return good


def trend_titles(report_json_path):
    try:
        day = json.load(open(report_json_path))
    except (OSError, ValueError):
        return []
    return [v["title"] for v in day.get("outliers", []) if v.get("niche") in
            ("business-stories", "personal-finance", "investing", "tech-gadgets", "ai-tools")]
