"""Channel identity shared by the video renderer and the channel art."""
from pathlib import Path

CHANNEL_NAME = "Billion Dollar Blunders"
HANDLE = "@BillionDollarBlunders"
SERIES_LABEL = "BILLION DOLLAR BLUNDERS"
TAGLINE = "The worst money decisions in history, in under a minute."

W, H = 1080, 1920
FPS = 30

INK = (11, 14, 12)          # background, near-black with a green cast
INK_2 = (22, 30, 25)        # raised surfaces
PAPER = (246, 244, 236)     # main text
MUTED = (150, 160, 152)     # labels
GOLD = (242, 193, 78)       # accent: money, emphasis
LOSS = (229, 72, 77)        # what they got / lost
GAIN = (61, 214, 140)       # what it became

FONT_DIR = Path("/usr/share/fonts/opentype/inter")
FONTS = {
    "black": FONT_DIR / "Inter-Black.otf",
    "extrabold": FONT_DIR / "Inter-ExtraBold.otf",
    "bold": FONT_DIR / "Inter-Bold.otf",
    "semibold": FONT_DIR / "Inter-SemiBold.otf",
    "display": FONT_DIR / "InterDisplay-Black.otf",
}


def font_path(weight):
    p = FONTS[weight]
    if p.exists():
        return str(p)
    # GitHub runners and other machines: fall back to any bold sans that exists.
    for alt in ("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
                "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf"):
        if Path(alt).exists():
            return alt
    raise FileNotFoundError("No usable font; install fonts-inter")
