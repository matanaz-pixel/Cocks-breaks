# Design and UX audit

Goal: judge whether the site looks professional and trustworthy, converts parents into WhatsApp conversations, and whether the editor is usable by a non-technical owner. Look at real screenshots, not only code.

## Setup
Render pages with Playwright at 390x844, 768x1024 and 1440x900. Take viewport screenshots after an instant scroll (`window.scrollTo({top, behavior:'instant'})`) because the site uses smooth scrolling and has sticky and fixed elements; full-page screenshots misplace those. View every screenshot with the Read tool. Do this for the home page and all four service pages, then for each tab of `editor/site-editor.html`, with the placeholder state and again after uploading a real-looking photo (fixtures: `python3 site/tests/make-fixtures.py`).

## Audience
Exhausted parents of babies and toddlers, mostly on a phone, often at night, in Hebrew. The owner is a sleep consultant, parent guide and developmental companion. Goal of the site: trust and branding first, then a low-pressure sales tone with a clear next step (WhatsApp).

## Check
1. **First impression**: within the first screen, is it clear who she is, what she offers, for whom, and what to do next? Hierarchy, whitespace, one clear primary action.
2. **Visual quality**: colour harmony, type scale, alignment, consistency of cards, icons and radii across the home page and the four service pages; awkward line breaks (single-word last lines in headings), RTL details, widows, mixed number and Latin text; imagery slots with and without photos; decorative shapes colliding with text.
3. **Conversion path**: number and placement of calls to action on a phone, the floating WhatsApp button, how a parent moves from the home page to the right service, whether the four-service layout confuses, pre-written WhatsApp messages.
4. **Copy**: Hebrew quality, tone (warm, moderately sales-oriented, no pressure), claims that sound unprovable or exaggerated, repetition across pages, unclear words, gendered wording, jargon. Flag sentences that should change, quoting them.
5. **Trust signals**: credentials, testimonials, photo of the owner, contact details, transparency about process and funding, medical disclaimers; what is missing that a cautious parent would look for (pricing information, location, session length, cancellation).
6. **Accessibility**: compute real contrast from rendered pixels or computed styles, tap targets, focus order, headings, landmarks, reading order in RTL, motion, zoom, colour not being the only signal.
7. **Performance feel**: page weight, font loading, layout shift when photos load.
8. **Editor UX**: can a non-technical person understand each tab without help? Label clarity, number of fields per screen, risk of confusing actions, error messages, the publish flow, what happens on a first visit, mobile use of the editor. Name the three most likely places a user would get stuck.
9. **"Template feel"**: does anything look generic, auto-generated or inconsistent with a premium personal brand?

## Output
Findings by severity with a screenshot description and the exact element, text or selector. Then the three changes that would most improve conversion and trust. Then what looked good and should stay unchanged.
