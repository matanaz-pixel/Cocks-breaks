# Legal and regulatory audit (Israel)

Goal: find legal and regulatory risks in what the site says, shows and collects. You are not a lawyer and this is not legal advice. For every item state: the risk, why, how confident you are, and what a professional should confirm. Use web search to verify the current state of each law and cite the source. If no search tool is available, mark every statement as "from memory, verify".

## What to read
All visible text: `site/src/content.json` (the single source of content), and the rendered pages in `site/*.html`. Also the editor texts in `editor/src/editor.js`, the footer disclaimer, the JSON-LD, and what the site stores or sends (nothing is sent anywhere unless a link is clicked; the editor stores data in the owner's browser).

## Topics to cover
1. **Privacy**: the Protection of Privacy Law and Amendment 13 (in force since August 2025). Does the site collect personal data today (forms, analytics, cookies, fonts from third parties)? What changes if the owner adds analytics, a form, a mailing list, or publishes photos and first names of children? Is a privacy policy needed now or only later? Database registration and notice duties. The owner's own client records.
2. **Consumer protection**: misleading advertising and omissions; testimonials (consent, authenticity, children's names); claims such as "ללא התחייבות", "ליווי צמוד", "תוצאות"; price display duties; cancellation and distance-selling rules; whether wording about results is safe. Check every superlative and implied guarantee.
3. **Professional titles and scope**: titles protected by law in Israel (for example in health professions and psychology) versus unregulated titles (sleep consultant, parent guide, developmental companion). Flag any wording that implies diagnosis, treatment, therapy, medical advice or a protected title. Check the medical disclaimers for adequacy.
4. **Funding claims**: statements that families can receive financial participation through the pregnancy basket (סל ההריון) or through reserve duty (מילואים). Are these accurate, who decides, what conditions apply, what documents are issued, and is the current wording safe ("לפי זכאות", "אינה מובטחת")? Say clearly what must be checked with the health funds or the relevant authority before publishing.
5. **Accessibility**: Israeli accessibility regulations for service providers' websites (Equal Rights for People with Disabilities, service accessibility adjustments), Israeli Standard 5568 (WCAG), whether a small business is exempt, whether an accessibility statement page is required and what it must contain. Compare against what the site actually does.
6. **Trade name and "גישת לילה טוב"**: is this a protected name, trademark or licensed method? What proof of qualification or permission should the owner hold before using it in headings and the footer? Tone of any claims of certification.
7. **Intellectual property**: photos (ownership, releases, minors), fonts used (Assistant and Varela Round, both under the SIL Open Font License; embedded as base64 in the pages: check licence conditions), icons (hand-drawn SVG in `site/src/sprite.html`), copied wording.
8. **Direct marketing**: the spam provisions of the Communications Law that would apply if she later sends promotional messages to people who contacted her through the site or WhatsApp.
9. **Business formalities relevant to the site**: sole trader or company details shown on the site, tax invoices (do not issue them from the site), professional liability insurance, contract and terms of service for clients, cancellation policy, record keeping. Say which of these the site should reference.
10. **Cookies and third parties**: the pages make no external requests today; confirm that, and list what would trigger duties if added later (analytics, maps, embedded video, chat widgets).

## Output
Table: topic, finding (quote the exact sentence or element), risk level, confidence, who should confirm, suggested safer wording where relevant. End with the short list of things that must be settled before launch and the list that can wait.
