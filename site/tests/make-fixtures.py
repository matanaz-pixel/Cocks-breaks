#!/usr/bin/env python3
"""Creates test photos (many formats, sizes and broken files) for the editor tests.
Usage: python3 site/tests/make-fixtures.py [output_dir]   (default: $TMPDIR/site-qa-fixtures)
Needs Pillow."""
import os, random, sys, tempfile
from PIL import Image, ImageDraw
out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(tempfile.gettempdir(), 'site-qa-fixtures')
os.makedirs(out + '/imgs', exist_ok=True); os.chdir(out)
random.seed(1)
def noisy(w, h):
    im = Image.new('RGB', (w, h)); d = ImageDraw.Draw(im)
    for y in range(h): d.line([(0, y), (w, y)], fill=(200 - y * 120 // h, 140 + y * 40 // h, 110 + y * 100 // h))
    for _ in range(4000):
        x, y = random.randrange(w), random.randrange(h); d.ellipse([x, y, x + 30, y + 30], fill=tuple(random.randrange(256) for _ in range(3)))
    return im
noisy(3000, 4000).save('hero.jpg', quality=92)
im = noisy(4000, 3000); ex = im.getexif(); ex[0x0112] = 6; im.save('rot.jpg', quality=90, exif=ex)
rgba = Image.new('RGBA', (800, 800), (255, 0, 0, 0)); ImageDraw.Draw(rgba).ellipse([100, 100, 700, 700], fill=(30, 120, 200, 255)); rgba.save('alpha.png')
open('notimage.txt', 'w').write('hello'); open('huge.jpg', 'wb').write(b'\0' * 32_000_000)
def base(w, h):
    im = Image.new('RGB', (w, h)); d = ImageDraw.Draw(im)
    for y in range(0, h, max(1, h // 200)): d.rectangle([0, y, w, y + max(1, h // 200)], fill=(220 - y * 100 // h, 150 + y * 50 // h, 120 + y * 90 // h))
    d.ellipse([w // 4, h // 4, w * 3 // 4, h * 3 // 4], fill=(240, 230, 200)); return im
b = base(1600, 1200); os.chdir('imgs')
b.save('photo.JPG', quality=90); b.save('w.webp'); b.save('g.gif'); b.save('b.bmp'); b.save('t.tiff')
b.save('prog.jpg', quality=88, progressive=True); b.convert('CMYK').save('cmyk.jpg', quality=90); b.save('png_named_jpg.jpg', format='PNG')
Image.new('RGB', (1, 1), (255, 0, 0)).save('one.png'); open('empty.jpg', 'wb').close(); open('fake.jpg', 'wb').write(b'not an image')
base(9000, 6000).save('big54mp.jpg', quality=60)
open('v.svg', 'w').write('<svg xmlns="http://www.w3.org/2000/svg" width="400" height="300"><rect width="400" height="300" fill="#a85a39"/></svg>')
try: b.save('a.avif')
except Exception: pass
print('fixtures in', out)
