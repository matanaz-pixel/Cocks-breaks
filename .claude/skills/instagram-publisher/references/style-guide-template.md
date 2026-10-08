# STYLE_GUIDE.md — template

Copy this structure into `brand/STYLE_GUIDE.md`. Fill every field with something concrete and checkable. Mark each rule **always / often / sometimes** and cite post numbers from the contact sheets as evidence. Write "unknown — ask user" rather than guessing.

```markdown
# Style guide — @<username>
Learned from <N> posts on <date>. Top performers weighted higher. Edit freely — this file wins over the stats.

## 1. Essence (2 lines)
What the feed feels like, and who it is for.

## 2. Colour & tone
- Palette: #xxxxxx (role: background), #xxxxxx (accent) … (from style-data.json + what you see)
- Grading: brightness <dark/mid/bright>, saturation <muted/natural/punchy>, warmth <cool/neutral/warm>, contrast <soft/crisp>
- Filters / look: e.g. "warm, slightly lifted blacks, no heavy filters"
- Never: e.g. "cold blue casts, neon"

## 3. Photography & composition
- Dominant subjects, angle (top-down / 45° / eye level), distance, background, light (natural / flash)
- Framing ratio: 4:5 / 1:1 / 9:16 and how often
- Crop habits: subject centred / rule-of-thirds / negative space for text
- People/hands/props in frame? Consistent surfaces?

## 4. Text on image
- Used on <x>% of posts. Where (top/center/bottom), size relative to width, colour, box or shadow, alignment
- Font character: serif/sans/handwritten/display; weight; case. Exact font: <name or "ask user">
- Language & direction (Hebrew RTL / English), max words
- Logo / watermark: where, how big

## 5. Formats
- Mix: feed images x%, carousels y% (avg N slides), reels z%
- Carousel pattern: slide 1 = …, last slide = …
- Reel pattern: length, hook in first 2s, captions burned in?, music (note: API cannot add audio)

## 6. Voice & caption
- Language(s) and mixing rules
- Length: median <n> chars / <n> lines; first line ≤ <n> chars
- Opening pattern + 3–5 real example first lines
- Emojis: median <n>/post; the usual ones: …; position (line start / end)
- Line-break rhythm
- CTA habit + recurring sign-off line (verbatim)
- Hashtags: <n> per post; placement (end / first comment); the 8–12 core tags; topic tags that rotate
- @mentions / location habits
- Never: words, tones, claims the account never uses

## 7. Cadence (informational)
Typical days/hours (timezone), posts per week.

## 8. Do / Don't checklist for a new post
- [ ] …
- [ ] …
```
