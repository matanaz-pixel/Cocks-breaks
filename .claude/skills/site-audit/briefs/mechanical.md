# Mechanical audit

Goal: find real bugs in the site pages and in the editor. Try to break things.

## Setup
- Built site: `site/*.html` (5 pages plus `404.html`). Generator: `site/src/generator.js`. Editor: `editor/site-editor.html`, built from `editor/src/*` and `site/src/*`.
- Playwright is installed (`PLAYWRIGHT_PATH`). Only Chromium is available. If Firefox or WebKit are missing under `/opt/pw-browsers`, say so and do not download anything.
- Run the existing suite first: `cd site && npm test`. Read `site/tests/*.js` to see what is already covered, then go beyond it.

## Check
1. **Site pages**: every viewport from 320x568 to 1920x1080, landscape phones, 200% zoom; horizontal scroll; overlap; clipped text; keyboard-only navigation and visible focus; skip link; menu behaviour including resize while open; anchors landing under the sticky header; no-JS; reduced motion; forced dark scheme; print; blocked network.
2. **Content stress**: very long words and names, empty sections, hundreds of list items, RTL and LTR mixing (phone number, email, URLs, Latin words in Hebrew), emoji, quotes and ampersands in every text field.
3. **Editor**: every tab and every field; add, delete, reorder, undo for each list type; image slot with every format; persistence after reload and after clearing site data; backup and restore (corrupt file, old version, file from a different app); ZIP export (open the extracted site and re-run the site tests on it); autosave failure path (block IndexedDB); two editor tabs open at once; very large projects (many photos); keyboard use; screen reader names.
4. **Data loss**: list every way the owner can lose work without a warning, and test each.
5. **Security**: script injection through any editable field, in the preview and in the published HTML; `javascript:` URLs; sandbox of the preview iframe; what the published JSON-LD does with hostile text.
6. **Generated output**: HTML validity (duplicate ids, bad nesting, missing alt), JSON-LD validity, sitemap and robots when a site URL is set, canonical and og tags, relative image paths, file names, page weight per page and total ZIP size.
7. **Build reproducibility**: does `npm run build` from a clean checkout give identical output? Does the editor open from `file://` without any network access?

## Output
Table of confirmed bugs: severity, page or tab, exact steps, observed vs expected, evidence, suggested fix naming the selector or function. Then cosmetic nits, then what passed, then what could not be tested.
