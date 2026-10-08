#!/usr/bin/env python3
"""Rebuilds the 10-post example set from one photo:  python3 build_set02.py <photo.jpg>
Shows how the skill applies the learned layout (panel + photo + colour bar), the feed colour grade,
different crops/zooms, a 3-tile series and a photo-only post. Nothing is published."""
import subprocess, sys
from pathlib import Path
from PIL import Image, ImageOps

HERE = Path(__file__).resolve().parent
IG = [sys.executable, str(HERE.parent.parent / "scripts" / "ig.py")]
OUT = HERE / "set-02"
photo = sys.argv[1]

TAN, ROSE, MOCHA, AQUA, SAGE = "#C8A07A", "#B8897A", "#8A6A5C", "#8FD3DB", "#A9B8A0"
POSTS = [  # n, ratio, text, focus, zoom, bar
    (1, "9:16", "כך מקשיבים\nלפעוט", "0.50,0.42", 1.5, ROSE),
    (2, "4:5", "אוכל\nבלי\nמאבקים", "0.60,0.62", 1.3, TAN),
    (3, "9:16", "צחוק\nהוא שפה\nמשותפת", "0.80,0.50", 1.4, SAGE),
    (4, "4:5", "10 דקות\nקשר לפני\nהשינה", "0.72,0.50", 1.0, MOCHA),
    (5, "9:16", "גם הורים\nצריכים\nלצחוק", "0.70,0.50", 1.15, AQUA),
    (6, "4:5", "ילדים לומדים\nממה שהם\nרואים", "0.62,0.55", 1.1, ROSE),
    (7, "9:16", "מילים\nראשונות", "0.45,0.45", 1.7, TAN),
    (8, "4:5", "גבולות\nבאהבה", "0.75,0.55", 1.2, MOCHA),
]

def run(args):
    subprocess.run(IG + args, check=True, capture_output=True)

OUT.mkdir(exist_ok=True)
# grid order is 1..8 then the photo-only post; panel side alternates per post -> checkerboard in a 3-column grid
SIDE = {n: ("left" if i % 2 == 0 else "right") for i, n in enumerate([1, 2, 3, 4, 5, 6, 7, 8])}
for n, ratio, text, focus, zoom, bar in POSTS:
    d = OUT / f"_{n}"
    run(["prepare", photo, "--out", str(d), "--layout", "split", "--ratio", ratio, "--focus", focus,
         "--zoom", str(zoom), "--bar-color", bar, "--panel-side", SIDE[n], "--text", text])
    (d / Path(photo).with_suffix(".jpg").name).replace(OUT / f"post-{n:02d}-{ratio.replace(':', 'x')}.jpg")
    d.rmdir()

# 9: three-tile series (continuous panel + photo across the grid), darker mocha bands like the pinned posts
d = OUT / "_9"
run(["prepare", photo, "--out", str(d), "--layout", "split", "--tiles", "3", "--focus", "0.60,0.52",
     "--panel-pct", "40", "--bar-color", "#9C6B5A", "--band-color", "#6B5750",
     "--text", "איך יוצרים\nבוקר רגוע\nבבית?\nטיפ מס' 1"])
for t in sorted(d.glob("*-t?.jpg")):
    t.replace(OUT / f"post-09-series-{t.stem[-1]}.jpg")
for f in d.glob("*"):
    f.unlink()
d.rmdir()

# 10: photo-only, graded, full bleed (like her family/milestone posts)
d = OUT / "_10"
run(["prepare", photo, "--out", str(d), "--layout", "plain", "--ratio", "4:5", "--focus", "0.7,0.5"])
(d / Path(photo).with_suffix(".jpg").name).replace(OUT / "post-10-4x5.jpg")
d.rmdir()

# profile-grid mock (Instagram shows 3:4 tiles): series first (pinned), then posts 1-8 and 10
order = [OUT / f"post-09-series-{i}.jpg" for i in (1, 2, 3)] + \
        [next(OUT.glob(f"post-{n:02d}-*.jpg")) for n in (1, 2, 3, 4, 5, 6, 7, 8, 10)]
tw, th, gap = 360, 480, 4
rows = -(-len(order) // 3)
grid = Image.new("RGB", (3 * tw + 2 * gap, rows * th + (rows - 1) * gap), "#121212")
for i, f in enumerate(order):
    grid.paste(ImageOps.fit(Image.open(f).convert("RGB"), (tw, th)), ((i % 3) * (tw + gap), (i // 3) * (th + gap)))
grid.save(OUT / "grid-mock.jpg", quality=90)
print("built:", sorted(p.name for p in OUT.glob("*.jpg")))
