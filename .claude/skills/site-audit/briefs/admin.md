# Administrative and launch-readiness audit

Goal: find what could go wrong after launch, in ownership, accounts, money, maintenance and handover. The owner is a non-technical sole practitioner. Assume the site will be hosted for free on a static host (Netlify or Cloudflare Pages) with a custom domain, and that the owner edits it herself with `editor/site-editor.html`, then uploads the ZIP by drag and drop.

## What to read
`editor/README.md`, `site/README.md`, `site/src/content.json`, the editor's publish tab text in `editor/src/editor.js`, `site/src/generator.js` (the issue checklist in `findIssues`), and the generated ZIP content (run the editor test `site/tests/editor-flow.js`, or build with `node site/src/build.js --issues`).

## Check
1. **Ownership and access**: who should own the domain, hosting, email, Google and social accounts; two-factor authentication; recovery e-mail and phone; what happens if the husband or the developer disappears, or if she loses her laptop; shared access.
2. **Domain and hosting**: choosing a name; `.co.il` versus `.com`; registrar versus host; DNS records for hosting and email; renewal dates and auto-renew; the cost per year; what breaks if the domain lapses; moving from a free subdomain to a custom domain without losing Google ranking; redirects from `www` to the root.
3. **Email**: addresses on her own domain, forwarding, spam and deliverability, the address shown on the site and its public exposure.
4. **Launch checklist**: everything that still contains placeholders, and where (list each placeholder with file and field); items the editor's pre-publish check does not catch; what the generator should warn about but does not.
5. **Publishing workflow**: the exact steps from edit to live; failure modes (uploading only part of the ZIP, uploading the editor file to the public host, forgetting images, old cache); how she knows the update worked; how to roll back to the previous version.
6. **Backups and continuity**: browser-only storage in the editor and how to lose it; backup habits; where backups should live; how to move to a new computer; version history; whether a second person can take over using the README alone.
7. **Search and discovery**: Google Search Console and sitemap submission, Google Business Profile (categories, service area, hours, reviews, link to the site), Instagram and Facebook link-in-bio, WhatsApp Business profile and catalogue, consistency of name, address and phone number across places.
8. **Operations of the business behind the site**: what happens when a parent writes on WhatsApp (response time promise, working hours, auto-reply, intake questionnaire, how discovery calls are booked, how payment and invoices are handled, session formats, cancellation policy), whether the site promises anything the owner cannot operationally deliver.
9. **Money and cost**: yearly cost estimate; free-tier limits and what happens if exceeded; paid options worth it later (email, forms, scheduling).
10. **Maintenance**: monthly checklist, who checks links, the testimonials' consent record, how to update funding information when conditions change, annual review of legal pages, dependency on the generator and editor (they are plain JS, no packages).
11. **Documentation gaps**: what a new owner or helper would not understand from the READMEs. Rewrite one paragraph of the README if it is unclear.

## Output
A prioritised checklist: must-do before launch, should-do in the first month, can wait. For each item: what, why, who does it, rough effort. Flag any step that depends on a decision the owner has not made yet and list the decision as a question.
