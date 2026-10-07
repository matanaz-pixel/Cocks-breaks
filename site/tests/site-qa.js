// Portable test: run with  node site/tests/NAME  (needs Playwright; set PLAYWRIGHT_PATH if it is installed globally)
const REPO = require('path').resolve(__dirname, '..', '..');
const FIX = process.env.FIXTURES || require('path').join(require('os').tmpdir(), 'site-qa-fixtures');
const SHOTS = require('path').join(require('os').tmpdir(), 'site-qa-shots'); require('fs').mkdirSync(SHOTS, { recursive: true });
const { chromium } = require(process.env.PLAYWRIGHT_PATH || 'playwright');
const fs = require('fs'), path = require('path');
const root = process.env.SITE_ROOT || REPO + '/site';
const shots = SHOTS;
const pages = ['index','newborn','sleep','toddlers','development'];
const widths = [[320,568],[390,844],[768,1024],[1440,900]];
let fails = 0; const F = [];
const ok = (c, m) => { if (!c) { fails++; F.push(m); } console.log((c ? '  PASS ' : '  FAIL ') + m); };
const url = p => 'file://' + path.join(root, p + '.html');
(async () => {
  const browser = await chromium.launch();
  // ---- cross-page link integrity (static)
  const htmls = Object.fromEntries(pages.map(p => [p, fs.readFileSync(path.join(root, p + '.html'), 'utf8')]));
  for (const p of pages) {
    const hrefs = [...htmls[p].matchAll(/href="([^"]+)"/g)].map(m => m[1]).filter(h => !/^(https?:|tel:|mailto:|data:)/.test(h));
    const bad = [];
    for (const h of hrefs) {
      const [file, hash] = h.split('#');
      const tgt = file ? file.replace('.html', '') : p;
      if (!htmls[tgt]) { bad.push(h); continue; }
      if (hash && !new RegExp(`id="${hash}"`).test(htmls[tgt])) bad.push(h);
    }
    ok(bad.length === 0, `${p}: internal links resolve ${JSON.stringify(bad)}`);
    const ids = [...htmls[p].matchAll(/ id="([^"]+)"/g)].map(m => m[1]);
    const dup = ids.filter((x, i) => ids.indexOf(x) !== i);
    ok(dup.length === 0, `${p}: unique ids ${JSON.stringify(dup)}`);
  }
  for (const p of pages) for (const [w, h] of widths) {
    console.log(`\n== ${p} @ ${w}x${h}`);
    const mob = w < 761;
    const ctx = await browser.newContext({ viewport: { width: w, height: h }, isMobile: mob, hasTouch: mob, locale: 'he-IL' });
    const page = await ctx.newPage();
    const errs = [], ext = [];
    page.on('pageerror', e => errs.push(e.message));
    page.on('console', m => { if (['error', 'warning'].includes(m.type())) errs.push(m.type() + ': ' + m.text()); });
    page.on('request', r => { const u = r.url(); if (!u.startsWith('file:') && !u.startsWith('data:')) ext.push(u); });
    await page.goto(url(p), { waitUntil: 'networkidle' });
    await page.waitForTimeout(250);
    ok(errs.length === 0, 'no console errors/warnings ' + JSON.stringify(errs));
    ok(ext.length === 0, 'no external requests ' + JSON.stringify(ext));
    const m = await page.evaluate(() => ({
      sw: document.documentElement.scrollWidth, cw: document.documentElement.clientWidth,
      h1: document.querySelectorAll('h1').length,
      emptyLinks: [...document.querySelectorAll('a,button')].filter(a => !a.textContent.trim() && !a.getAttribute('aria-label')).length,
      navs: [...document.querySelectorAll('nav')].filter(n => !n.getAttribute('aria-label')).length,
      fonts: document.fonts.check("400 16px Assistant") && document.fonts.check("400 16px 'Varela Round'"),
      ld: (() => { try { JSON.parse(document.querySelector('script[type="application/ld+json"]').textContent); return true } catch (e) { return false } })(),
      wa: [...document.querySelectorAll('a[href*="wa.me"]')].map(a => { try { return decodeURIComponent(a.href.split('text=')[1]).startsWith('היי') } catch (e) { return false } }).every(Boolean),
      blank: [...document.querySelectorAll('a[target=_blank]')].filter(a => !/noopener/.test(a.rel)).length,
    }));
    ok(m.sw <= m.cw, `no horizontal scroll (${m.sw}/${m.cw})`);
    ok(m.h1 === 1, 'one h1');
    ok(m.emptyLinks === 0, 'no unlabeled links/buttons');
    ok(m.navs === 0, 'every <nav> has aria-label');
    ok(m.fonts && m.ld && m.wa && m.blank === 0, 'fonts embedded, JSON-LD valid, wa.me text decodes, noopener set');
    // FAQ
    const d = page.locator('details').first();
    await d.locator('summary').scrollIntoViewIfNeeded(); await d.locator('summary').click();
    ok(await d.evaluate(x => x.open), 'FAQ opens');
    await d.locator('summary').click(); ok(!(await d.evaluate(x => x.open)), 'FAQ closes');
    // touch targets
    if (mob) {
      const small = await page.evaluate(() => {
        const sel = '.burger,.brand,.btn,.switch a,.wa-chips a,.social a,.mail,summary,.fab,.crumbs a,.svc,.rel';
        return [...document.querySelectorAll(sel)].filter(e => { const r = e.getBoundingClientRect(); const cs = getComputedStyle(e); return r.width > 0 && r.height > 0 && cs.visibility !== 'hidden' && (r.height < 43.5 || r.width < 43.5) }).map(e => e.className + ':' + Math.round(e.getBoundingClientRect().width) + 'x' + Math.round(e.getBoundingClientRect().height));
      });
      ok(small.length === 0, 'touch targets >=44px ' + JSON.stringify(small.slice(0, 6)));
    }
    // menu behaviour
    if (mob) {
      await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' }));
      const b = page.locator('#burger'), menu = page.locator('#menu');
      await b.click(); ok(await menu.evaluate(e => e.classList.contains('open')) && (await b.getAttribute('aria-expanded')) === 'true' && /סגירת/.test(await b.getAttribute('aria-label')), 'menu opens, aria updates');
      await page.keyboard.press('Escape'); ok(!(await menu.evaluate(e => e.classList.contains('open'))), 'Escape closes menu');
      await b.click(); await page.locator('.brand').click(); ok(!(await menu.evaluate(e => e.classList.contains('open'))), 'brand click closes menu');
      await b.click(); await page.locator('#menu a').first().click(); await page.waitForTimeout(300);
      if (p === 'index' || true) ok(!(await menu.evaluate(e => e.classList.contains('open'))), 'link click closes menu');
    }
    // fab
    const fab = page.locator('#fab');
    const vis = () => fab.evaluate(e => { const s = getComputedStyle(e); return s.display !== 'none' && s.visibility !== 'hidden' && parseFloat(s.opacity) > .9 });
    await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' })); await page.waitForTimeout(350);
    ok(!(await vis()), 'FAB hidden at top');
    if (mob) {
      await page.evaluate(() => window.scrollTo({ top: 1200, behavior: 'instant' })); await page.waitForTimeout(450);
      ok(await vis(), 'FAB visible after scroll');
      await page.evaluate(() => document.getElementById('contact').scrollIntoView({ behavior: 'instant' })); await page.waitForTimeout(450);
      ok(!(await vis()), 'FAB hidden over contact');
      await page.evaluate(() => { document.getElementById('fab').focus(); });
      ok(await page.evaluate(() => document.activeElement.id !== 'fab'), 'hidden FAB not focusable');
    }
    if (w === 1440 || w === 390) await page.screenshot({ path: path.join(shots, `${p}-${w}.png`), fullPage: true });
    await ctx.close();
  }
  // ---- contrast scan (computed styles), desktop, all pages
  console.log('\n== contrast scan');
  for (const p of pages) {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } }); const page = await ctx.newPage();
    await page.goto(url(p), { waitUntil: 'networkidle' });
    const low = await page.evaluate(() => {
      const parse = c => { const m = c.match(/rgba?\(([^)]+)\)/); if (!m) return null; const [r, g, b, a = 1] = m[1].split(',').map(parseFloat); return { r, g, b, a } };
      const lum = ({ r, g, b }) => { const f = v => { v /= 255; return v <= .03928 ? v / 12.92 : ((v + .055) / 1.055) ** 2.4 }; return .2126 * f(r) + .7152 * f(g) + .0722 * f(b) };
      const over = (fg, bg) => ({ r: fg.r * fg.a + bg.r * (1 - fg.a), g: fg.g * fg.a + bg.g * (1 - fg.a), b: fg.b * fg.a + bg.b * (1 - fg.a), a: 1 });
      const bgOf = el => { let stack = []; for (let e = el; e; e = e.parentElement) { const cs = getComputedStyle(e); if (cs.backgroundImage !== 'none' && !/^none/.test(cs.backgroundImage)) return null; const c = parse(cs.backgroundColor); if (c && c.a > 0) { stack.push(c); if (c.a === 1) break } } let base = { r: 255, g: 255, b: 255, a: 1 }; for (const c of stack.reverse()) base = over(c, base); return base };
      const res = [];
      const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
      const seen = new Set();
      while (walker.nextNode()) {
        const t = walker.currentNode; if (!t.textContent.trim()) continue; const el = t.parentElement;
        if (seen.has(el) || el.closest('svg,script,style,noscript')) continue; seen.add(el);
        const cs = getComputedStyle(el); const r = el.getBoundingClientRect(); if (r.width === 0 || cs.visibility === 'hidden') continue;
        const bg = bgOf(el); if (!bg) continue;
        let fg = parse(cs.color); fg = over({ ...fg, a: fg.a * parseFloat(cs.opacity) }, bg);
        const L1 = lum(fg), L2 = lum(bg), ratio = (Math.max(L1, L2) + .05) / (Math.min(L1, L2) + .05);
        const size = parseFloat(cs.fontSize), bold = parseInt(cs.fontWeight) >= 700 || cs.fontFamily.includes('Secular');
        const large = size >= 24 || (size >= 18.66 && bold);
        if (ratio < (large ? 3 : 4.5)) res.push(`${el.tagName.toLowerCase()}.${el.className}[${t.textContent.trim().slice(0, 28)}] ${ratio.toFixed(2)} ${size}px`);
      }
      return res;
    });
    ok(low.length === 0, `${p}: text contrast AA ${JSON.stringify(low.slice(0, 8))}`);
    await ctx.close();
  }
  // ---- keyboard focus visibility on dark/clay surfaces + FAQ + no-JS + landscape menu
  console.log('\n== focus / no-JS / landscape');
  {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } }); const page = await ctx.newPage();
    await page.goto(url('index'), { waitUntil: 'networkidle' });
    const probe = async sel => page.evaluate(s => { const e = document.querySelector(s); e.focus({ focusVisible: true }); const cs = getComputedStyle(e); let bg = null; for (let x = e; x; x = x.parentElement) { const c = getComputedStyle(x).backgroundColor; if (c && c !== 'rgba(0, 0, 0, 0)') { bg = c; break } } return { ow: cs.outlineWidth, oc: cs.outlineColor, bg, style: cs.outlineStyle } }, sel);
    for (const sel of ['.contact .btn', '.contact .mail', '.contact .social a', '.wa-chips a', 'summary', '.svc']) {
      const r = await probe(sel); ok(r.style !== 'none' && r.ow !== '0px' && r.oc !== r.bg, `focus ring distinct from surface: ${sel} ${r.oc} on ${r.bg}`);
    }
    // FAQ outline not clipped: check no overflow:hidden on details
    ok(await page.evaluate(() => getComputedStyle(document.querySelector('details')).overflow !== 'hidden'), 'details has no overflow:hidden (focus ring not clipped)');
    await ctx.close();
  }
  {
    const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, javaScriptEnabled: false }); const page = await ctx.newPage();
    await page.goto(url('sleep'), { waitUntil: 'networkidle' });
    const r = await page.evaluate(() => ({ menu: getComputedStyle(document.getElementById('menu')).display, burger: getComputedStyle(document.getElementById('burger')).display, sw: document.documentElement.scrollWidth }));
    ok(r.menu !== 'none' && r.burger === 'none' && r.sw <= 390, 'no-JS mobile: nav links visible, burger hidden ' + JSON.stringify(r));
    await page.screenshot({ path: path.join(shots, 'nojs-390.png') });
    await ctx.close();
  }
  {
    const ctx = await browser.newContext({ viewport: { width: 667, height: 375 }, isMobile: true, hasTouch: true }); const page = await ctx.newPage();
    await page.goto(url('index'), { waitUntil: 'networkidle' });
    await page.click('#burger');
    const r = await page.evaluate(() => { const m = document.getElementById('menu'); return { sh: m.scrollHeight, ch: m.clientHeight, oy: getComputedStyle(m).overflowY, bottom: Math.round(m.getBoundingClientRect().bottom), vh: innerHeight } });
    ok(r.bottom <= r.vh + 1 && (r.sh <= r.ch || r.oy === 'auto'), 'landscape phone: open menu fits or scrolls ' + JSON.stringify(r));
    await page.screenshot({ path: path.join(shots, 'landscape-menu.png') });
    await ctx.close();
  }
  console.log(`\nTOTAL FAILS: ${fails}`); F.forEach(f => console.log(' -', f));
  await browser.close(); process.exit(fails ? 1 : 0);
})();
