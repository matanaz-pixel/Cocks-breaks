# Style guide — @yael__lahav
**Status: v0 (provisional).** Learned on 2026-10-08 from ONE profile-grid screenshot (≈24 tiles, bio, highlights). Captions, hashtags, engagement and posting times were **not visible**, so those sections are marked `unknown`. Refresh with `ig.py learn` once the API is connected, or with screenshots of 4–6 posts that include their captions. Edit freely — this file wins over any statistics.

## 1. Essence
Warm, calm, honest motherhood. A professional voice (parenting & sleep counselor, M.A.) wrapped in real, unposed family moments — the feed feels like a trusted friend who happens to be an expert. Audience: parents of babies/toddlers, expecting parents.

Bio signals (verbatim facts): "יעל ליבה להב – יועצת שינה בכירה, מדריכת הורים ומלווה התפתחותית"; "אמא של שי-לה ולה-אור ✨"; offers a gradual, sensitive sleep process with daily support; link in bio to a booking page. Uses the ✨ emoji.

## 2. Signature layout — "panel + photo"  *(always on text posts; ~70% of the grid)*
Vertical **9:16** (Reel cover / Story / pinned series), reproduced by `ig.py … --layout split`:
- **Text panel ≈ 42% of the width**, full height, flat cream/blush-white `#F7F0EC`. **Side alternates from post to post** (left → right → left…; user decision, `split.panel_side: "alternate"`), which makes a checkerboard in the 3-column grid. NB: the 15 existing panel posts measured so far are all LEFT, so the alternation starts with the next post. The 3-tile series keeps the panel on the left like the pinned posts.
- **Photo fills the other ≈ 58%** (the side opposite the panel) edge to edge, full height (no border, no rounded corners).
- **Thin colour bar along the bottom (~2% of height)**, colour sampled from the photo or the topic: warm tan `#C8A07A`, rose-mocha `#B8897A`, deep mocha `#8A6A5C`, occasionally aqua `#8FD3DB`/sage. A faint blush band on top appears on some.
- **Pinned 3-part series** (feed tips): same panel + photo split across a continuous 3-tile triptych, darker mocha top/bottom bands (`#6B5750` / `#9C6B5A`), text left-aligned with a hand-drawn underline on "טיפ מס' 1".

## 3. Typography (text panel)
- Hand-written, **thin, monoline, rounded pen** — not a bold marker. Near-black ink `#161616`. English accents ("here we go again") in a flowing calligraphic script, sometimes pastel blue/pink for gender-reveal ("boy / girl").
- **Large**, centred in the panel, **2–5 short lines**, ≤ 6 words total, one idea per line, generous line spacing, vertically centred.
- Hebrew RTL; titles are topic hooks, not sentences.
- Closest available match shipped: **Gveret Levin** (`brand/fonts/`) with the stroke thinned in code (`split.ink_thin: 4` in `brand/config.json`) to approach the fine pen. ⚠ Still an approximation: Gveret is slanted and a little more playful. (Playpen Sans Hebrew was tried and rejected: its mem reads as alef — "ממה" became "אמה".) **Ask the user for the exact Canva font name** and drop the file in `brand/fonts/`.

## 4. Photography
- Candid, **real moments, not posed**: laughing, kissing, nursing, messy faces, a newborn crying right after birth. Close and intimate; faces fill the frame.
- **Natural warm light**, often outdoors/golden hour, minimal retouching. Skin stays warm.
- Wardrobe/props in **soft neutrals & pastels**: cream, beige, blush pink, sage, dusty blue; textured knits, linen.
- Mix of baby-only, mother+baby, couple+baby, family, bump/pregnancy, and the account owner solo (e.g. "יומולדת 36").
- Pure-photo posts (no text panel) are used as **carousels** for milestone/story content (family shoot, birth).
- Never: heavy filters, cold blue casts, stock-photo gloss.

## 5. Palette  *(estimated by eye from a compressed screenshot — verify against the Canva brand kit)*
| role | hex |
|---|---|
| panel / background | `#F7F0EC` |
| ink | `#161616` |
| bar — tan | `#C8A07A` |
| bar — rose mocha | `#B8897A` |
| bar — deep mocha | `#8A6A5C` |
| accent — aqua / pastel pink / pastel blue | `#8FD3DB` / `#F2C6CF` / `#B9D4F0` |

## 6. Highlights (stories covers)
Cream circle, soft blush line-art icon inside a thin laurel wreath; one or two words under it: "עצמאות", "הצצה לתהליך 7/8", "הורים ממליצים". Keep this system for any new highlight.

## 7. Formats & topics (from the grid)
- Mostly **Reels** with the panel layout; carousels for photo stories; a pinned educational series at the top.
- Topic mix: sleep & development tips ("כך תגדלו ילדים עצמאיים", "רגרסיה סביב הסתגלות לגן", "כאבי שיניים"), personal motherhood moments ("חופשה משפחתית ראשונה", "יומולדת 36"), family milestones (pregnancy, birth, reveal), light emotional lines ("צידה אהבה לדרך", "המאהבת של בעלי").

## 8. Voice — on-image text (observed) & captions (**unknown**)
- On-image: short, warm, direct address to parents in plural ("כך תגדלו…", "תלמדו אותם…"), or personal first person ("…בונה"). No exclamation marks, no emojis in the artwork.
- Captions, hashtags, CTAs, sign-off: **unknown — not visible**. Provisional assumption used in examples: warm-professional, short paragraphs, 3 concrete tips, a question to the audience, 1–3 ✨/🤍 emojis, 3–5 Hebrew hashtags. **Confirm with real captions before relying on it.**

## 9. Cadence
unknown (profile showed 513 posts, 3,137 followers).

## 10. New-post checklist
- [ ] Real moment, faces visible, warm light — would it feel posed? Then pick another frame.
- [ ] Title ≤ 6 words, 2–5 lines, no emoji in the artwork.
- [ ] `--layout split`, panel left 42%, bar colour picked from the photo's dominant warm tone.
- [ ] Photo is colour-graded to the feed (soft, warm, lifted blacks; automatic via `brand/config.json → grade`, `--no-grade` to skip)
- [ ] Faces and hands stay inside the photo window (`--focus x,y` to shift the crop).
- [ ] Caption: hook line → one insight → 3 concrete tips → question to the audience.
- [ ] No claims about the child's age, health or the family that the user did not provide.
