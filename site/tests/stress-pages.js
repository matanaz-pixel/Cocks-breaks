// Portable test: run with  node site/tests/NAME  (needs Playwright; set PLAYWRIGHT_PATH if it is installed globally)
const REPO = require('path').resolve(__dirname, '..', '..');
const FIX = process.env.FIXTURES || require('path').join(require('os').tmpdir(), 'site-qa-fixtures');
const SHOTS = require('path').join(require('os').tmpdir(), 'site-qa-shots'); require('fs').mkdirSync(SHOTS, { recursive: true });
const { chromium } = require(process.env.PLAYWRIGHT_PATH || 'playwright');
(async () => {
  const b = await chromium.launch(); let fails = 0; const ok = (c, m) => { if (!c) fails++; console.log((c ? 'PASS ' : 'FAIL ') + m) };
  for (const [dir, label] of [['stress-hollow', 'hollow'], ['stress-hostile', 'hostile']]) for (const f of ['index', 'newborn', 'sleep', 'toddlers', 'development', '404']) {
    const p = await b.newPage(); const errs = []; p.on('pageerror', e => errs.push(e.message)); p.on('dialog', d => { errs.push('dialog ' + d.message()); d.dismiss() });
    await p.goto('file://' + FIX + '/' + dir + '/' + f + '.html'); await p.waitForTimeout(250);
    const r = await p.evaluate(() => ({ pwn: window.__pwn, imgs: document.querySelectorAll('img[src="x"]').length, scripts: document.querySelectorAll('main script').length, h1: document.querySelectorAll('h1').length, sw: document.documentElement.scrollWidth, cw: document.documentElement.clientWidth, jsonld: (() => { try { JSON.parse(document.querySelector('script[type="application/ld+json"]').textContent); return true } catch (e) { return false } })(), canon: document.querySelector('link[rel=canonical]') && document.querySelector('link[rel=canonical]').href, jsHref: [...document.querySelectorAll('a')].filter(a => /^javascript:/i.test(a.getAttribute('href') || '')).length }));
    ok(errs.length === 0 && r.pwn === undefined && r.imgs === 0 && r.scripts === 0, `${label}/${f}: no script execution, no injected elements ${JSON.stringify(errs)}`);
    ok(r.h1 === 1 && r.jsonld && r.sw <= r.cw + 1, `${label}/${f}: one h1, JSON-LD parses, no horizontal scroll (${r.sw}/${r.cw})`);
    if (label === 'hostile') ok(r.jsHref === 0 && !(r.canon || '').startsWith('javascript'), `${label}/${f}: javascript: URL never becomes an href (${r.canon})`);
    await p.close();
  }
  console.log('FAILS', fails); await b.close(); process.exit(fails ? 1 : 0);
})();
