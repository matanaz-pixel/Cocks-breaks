#!/usr/bin/env python3
# בונה את חמש הגרסאות מהתבניות ומ-content.py. הרצה: python3 build.py
import pathlib, sys
from urllib.parse import quote
from jinja2 import Environment, FileSystemLoader, StrictUndefined
here = pathlib.Path(__file__).parent
sys.path.insert(0, str(here))
from content import CONTENT
c = dict(CONTENT)
c["about"] = [p.replace("{name}", c["name"]) for p in c["about"]]
c["wa_link"] = f"https://wa.me/{c['phone_intl']}?text={quote(c['wa_text'])}"
c["tel_link"] = "tel:+" + c["phone_intl"]
env = Environment(loader=FileSystemLoader(str(here)), undefined=StrictUndefined, autoescape=False)
sprite = (here / "sprite.html").read_text(encoding="utf-8")
out = here.parent
for tpl in sorted(here.glob("v*.tpl.html")):
    html = env.get_template(tpl.name).render(c=c, sprite=sprite)
    target = out / (tpl.name.replace(".tpl", ""))
    target.write_text(html, encoding="utf-8")
    print("built", target.name, len(html) // 1024, "KB")
