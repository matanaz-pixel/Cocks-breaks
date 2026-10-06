#!/usr/bin/env python3
# בונה את כל האתר מ-content.py ומהתבניות. הרצה: python3 site/src/build.py
# עם --artifact DIR: בונה גם עותק לפרסום, שבו דף הבית נכתב כקטע ללא תגיות html/head/body.
import pathlib, re, sys
from urllib.parse import quote
from jinja2 import Environment, FileSystemLoader, StrictUndefined
here = pathlib.Path(__file__).parent
sys.path.insert(0, str(here))
from content import BIZ, SERVICES, HOME
out = here.parent

def wa(text):
    return f"https://wa.me/{BIZ['phone_intl']}?text={quote(text)}"

env = Environment(loader=FileSystemLoader(str(here)), undefined=StrictUndefined, autoescape=False)
env.globals["wa"] = wa
SVM = {s["slug"]: s for s in SERVICES}
css = (here / "style.css").read_text(encoding="utf-8")
fonts = (here / "fonts.css").read_text(encoding="utf-8")
sprite = (here / "sprite.html").read_text(encoding="utf-8")
about = dict(HOME)
about["about"] = [p.replace("{name}", BIZ["name"]) for p in HOME["about"]]

def render(tpl, **kw):
    return env.get_template(tpl).render(B=BIZ, SV=SERVICES, SVM=SVM, H=about, css=css, fonts=fonts, sprite=sprite, **kw)

def nav(h):
    return [("שירותים", h + "#services"), ("הגישה ההוליסטית", h + "#holistic"), ("עליי", h + "#about"),
            ("המלצות", "#reviews"), ("מימון", "#funding"), ("שאלות", "#faq")]

pages = {}
ld_home = {"@context": "https://schema.org", "@type": "ProfessionalService", "name": BIZ["name"], "description": HOME["meta"],
           "telephone": "+" + BIZ["phone_intl"], "email": BIZ["email"], "sameAs": [BIZ["instagram"], BIZ["facebook"]], "inLanguage": "he",
           "hasOfferCatalog": {"@type": "OfferCatalog", "name": "שירותים", "itemListElement": [
               {"@type": "Offer", "itemOffered": {"@type": "Service", "name": s["short"], "description": s["meta"]}} for s in SERVICES]}}
pages["index.html"] = render("home.html", S=None, h="", nav=nav(""), page_title=f"{BIZ['name']} | {HOME['title']}", page_desc=HOME["meta"],
                             page_wa=wa(HOME["wa_text"]), ld=ld_home)
for s in SERVICES:
    ld = {"@context": "https://schema.org", "@type": "Service", "name": s["short"], "description": s["meta"], "inLanguage": "he",
          "provider": {"@type": "ProfessionalService", "name": BIZ["name"], "telephone": "+" + BIZ["phone_intl"]}}
    pages[s["file"]] = render("service.html", S=s, h="index.html", nav=nav("index.html"), page_title=f"{s['short']} | {BIZ['name']}",
                              page_desc=s["meta"], page_wa=wa(s["wa_text"]), ld=ld)

def fragment(html):
    """Publish mode: the host wraps the main page in its own skeleton, so emit title, styles, scripts and body content only."""
    title = "<title>אתר הדרכת הורים ושינה</title>"
    head = re.search(r"<head>(.*)</head>", html, re.S).group(1)
    styles = "".join(re.findall(r"<style>.*?</style>", head, re.S))
    scripts = "".join(re.findall(r"<script[^>]*>.*?</script>", head, re.S))
    body = re.search(r"<body[^>]*>(.*)</body>", html, re.S).group(1)
    root_vars = re.search(r"<body([^>]*)>", html).group(1)
    pre = "<script>document.documentElement.lang='he';document.documentElement.dir='rtl'</script>"
    return f"{title}\n{styles}\n{scripts}\n{pre}\n{body}"

for name, html in pages.items():
    (out / name).write_text(html, encoding="utf-8")
    print("built", name, len(html) // 1024, "KB")
if "--artifact" in sys.argv:
    d = pathlib.Path(sys.argv[sys.argv.index("--artifact") + 1]); d.mkdir(parents=True, exist_ok=True)
    for name, html in pages.items():
        (d / name).write_text(fragment(html) if name == "index.html" else html, encoding="utf-8")
    print("artifact copy ->", d)
