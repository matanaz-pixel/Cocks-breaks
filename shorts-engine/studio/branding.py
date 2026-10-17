"""Channel art: profile picture, banner, video watermark and the About text."""
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw

from . import brand as B
from .render import Background, font

ABOUT = f"""{B.TAGLINE}

Blockbuster passing on Netflix. Kodak sitting on the digital camera. Ten thousand bitcoin spent on two pizzas.
Every short tells one real, documented business blunder, with the numbers that make it hurt.

New short every day. Sources are linked in every description.
Narration is computer-generated; every script is fact-checked and approved by a person before it goes live."""


def cracked_dollar(size):
    """A gold dollar sign split by a diagonal crack, the channel mark."""
    mark = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(mark)
    f = font("display", int(size * 0.92))
    box = d.textbbox((0, 0), "$", font=f)
    x = (size - (box[2] - box[0])) / 2 - box[0]
    y = (size - (box[3] - box[1])) / 2 - box[1]
    d.text((x, y), "$", font=f, fill=B.GOLD)

    crack = [(0, size * 0.58), (size * 0.38, size * 0.47), (size * 0.5, size * 0.56),
             (size * 0.64, size * 0.44), (size, size * 0.36)]
    upper = Image.new("L", mark.size, 0)
    ImageDraw.Draw(upper).polygon([(0, 0), (size, 0)] + crack[::-1], fill=255)
    lower = ImageChops.invert(upper)
    top = Image.new("RGBA", mark.size, (0, 0, 0, 0))
    top.paste(mark, (0, 0), upper)
    bottom = Image.new("RGBA", mark.size, (0, 0, 0, 0))
    bottom.paste(mark, (0, 0), lower)
    out = Image.new("RGBA", mark.size, (0, 0, 0, 0))
    shift = int(size * 0.035)
    out.alpha_composite(top, (-shift, -shift))
    out.alpha_composite(bottom, (shift, shift))
    return out


def profile(path, size=800):
    img = Image.new("RGB", (size, size), B.INK)
    d = ImageDraw.Draw(img)
    d.ellipse([size * 0.06, size * 0.06, size * 0.94, size * 0.94], fill=B.INK_2, outline=B.GOLD, width=int(size * 0.02))
    mark = cracked_dollar(int(size * 0.7))
    img.paste(mark, (int(size * 0.15), int(size * 0.15)), mark)
    img.save(path)


def watermark(path, size=150):
    mark = cracked_dollar(size)
    mark.save(path)


def banner(path):
    W, H = 2560, 1440
    bg = Background(seed=3).base.resize((W, H))
    d = ImageDraw.Draw(bg)
    for gx in range(0, W, 120):
        d.line([(gx, 0), (gx, H)], fill=(24, 32, 27), width=2)
    for gy in range(0, H, 120):
        d.line([(0, gy), (W, gy)], fill=(24, 32, 27), width=2)
    # Everything that matters sits inside YouTube's 1546x423 safe area in the middle.
    sx, sy, sw, sh = (W - 1546) // 2, (H - 423) // 2, 1546, 423
    mark = cracked_dollar(360)
    bg.paste(mark, (sx + 10, sy + 30), mark)
    tx = sx + 400
    big = font("display", 118)
    d.text((tx, sy + 40), "BILLION DOLLAR", font=big, fill=B.PAPER)
    d.text((tx, sy + 160), "BLUNDERS", font=big, fill=B.GOLD)
    sub = font("semibold", 46)
    d.text((tx, sy + 300), "The worst money decisions in history.", font=sub, fill=B.MUTED)
    d.rectangle([tx, sy + 372, tx + 520, sy + 376], fill=B.GOLD)
    bg.save(path)


def build_all(out_dir):
    out = Path(out_dir)
    out.mkdir(parents=True, exist_ok=True)
    profile(out / "profile.png")
    banner(out / "banner.png")
    watermark(out / "watermark.png")
    (out / "about.txt").write_text(ABOUT + "\n")
    return sorted(str(p) for p in out.iterdir())
