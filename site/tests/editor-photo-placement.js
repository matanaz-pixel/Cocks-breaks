// Photo placement: the owner must find a place to add a photo wherever a photo frame appears.
const REPO = require('path').resolve(__dirname, '..', '..');
const FIX = process.env.FIXTURES || require('path').join(require('os').tmpdir(), 'site-qa-fixtures');
const { chromium } = require(process.env.PLAYWRIGHT_PATH || 'playwright');
const fs = require('fs'), path = require('path');
const EDITOR = 'file://' + REPO + '/editor/site-editor.html';
let fails = 0; const ok = (c, m) => { if (!c) fails++; console.log((c ? 'PASS ' : 'FAIL ') + m); };
const wait = ms => new Promise(r => setTimeout(r, ms));
(async () => {
  const br = await chromium.launch(); const ctx = await br.newContext({ viewport: { width: 1440, height: 900 }, locale: 'he-IL' });
  const p = await ctx.newPage(); const errs = []; p.on('pageerror', e => errs.push(e.message));
  await p.goto(EDITOR); await wait(700);

  // welcome dialog is centred and reachable
  const box = await p.locator('#dlg').boundingBox();
  ok(box && Math.abs((box.x + box.width / 2) - 720) < 30 && Math.abs((box.y + box.height / 2) - 450) < 120, 'welcome dialog is centred on a desktop screen ' + JSON.stringify(box && { x: Math.round(box.x), y: Math.round(box.y), w: Math.round(box.width) }));
  await p.click('#dlg button'); await wait(200);
  const tab = async id => { await p.click(`.tab[data-id="${id}"]`); await wait(450); };
  const frame = () => p.frameLocator('#frame');

  // 1. home tab has both photo slots, visible without scrolling through other groups
  await tab('home');
  ok(await p.locator('.img-slot[data-slot="hero"]').isVisible() && await p.locator('.img-slot[data-slot="about"]').isVisible(), 'home tab shows the hero and personal photo slots');
  await p.locator('.img-slot[data-slot="hero"] input[type=file]').setInputFiles(FIX + '/hero.jpg'); await wait(1500);
  await p.click('#pvPages button:first-child'); await wait(800);
  ok(await frame().locator('.arch img.ph').count() === 1, 'photo chosen in the home tab appears in the preview');

  // 2. every service tab has its own slot
  for (let i = 0; i < 4; i++) { await tab('svc' + i); ok(await p.locator('.img-slot').count() === 1 && await p.locator('.img-slot').isVisible(), `service tab ${i} shows its photo slot`); }

  // 3. the visible choose button opens the native file chooser by itself
  await tab('svc1');
  const [chooser0] = await Promise.all([p.waitForEvent('filechooser', { timeout: 4000 }).catch(() => null), p.locator('.img-slot label.filebtn').click()]);
  ok(!!chooser0, 'clicking "בחירת תמונה מהמחשב" opens the file chooser'); if (chooser0) await chooser0.setFiles([]).catch(() => { });

  // 4. clicking the empty photo frame in the preview jumps to the right slot and opens the chooser
  await tab('contact');
  await p.click('#pvPages button:nth-child(3)'); await wait(900);   // sleep page preview
  const [chooser1] = await Promise.all([p.waitForEvent('filechooser', { timeout: 4000 }).catch(() => null), frame().locator('.s-art').click()]);
  await wait(500);
  ok(await p.locator('.tab[data-id="svc1"]').getAttribute('aria-selected') === 'true', 'clicking the service photo frame in the preview switches to that service tab');
  ok(await p.locator('.img-slot[data-slot="sleep"]').evaluate(e => e.classList.contains('flash')), 'the matching photo slot is highlighted');
  ok(!!chooser1, 'clicking the frame opens the file chooser directly');
  if (chooser1) { await chooser1.setFiles(FIX + '/rot.jpg'); await wait(1500); ok(await p.locator('.img-slot[data-slot="sleep"] .thumb img').count() === 1, 'photo chosen through the frame click is added'); }
  // hero frame from another tab
  await tab('contact'); await p.click('#pvPages button:first-child'); await wait(900);
  await frame().locator('.arch').click(); await wait(600);
  ok(await p.locator('.tab[data-id="home"]').getAttribute('aria-selected') === 'true', 'clicking the home photo frame switches to the home tab');
  ok(/לחצי כאן/.test(await frame().locator('.photo small').innerText()), 'empty frames in the preview say "לחצי כאן להוספת תמונה"');

  // 5. published pages keep the original placeholder wording and have no editor hooks
  const html = (await p.evaluate(() => 0), fs.readFileSync(REPO + '/site/index.html', 'utf8'));
  ok(!/data-photo/.test(html) && html.includes('כאן תהיה התמונה'), 'built site has no preview-only attributes');

  // 6. warning when the editor runs inside a frame
  const wrap = path.join(REPO, 'editor', '__wrap_test.html'); fs.writeFileSync(wrap, '<iframe src="site-editor.html" style="width:1200px;height:800px"></iframe>');
  const p2 = await ctx.newPage(); await p2.goto('file://' + wrap); await wait(1500);
  const inner = p2.frames().find(f => f.url().endsWith('site-editor.html'));
  ok(inner && await inner.locator('.notice[role=alert]').count() === 1, 'editor shows a "open it directly in Chrome" warning when embedded in a frame');
  fs.unlinkSync(wrap);
  ok(await p.locator('.top').first().evaluate(e => e.previousElementSibling === null), 'no warning banner when opened directly');

  // 7. phone: dialog fits, status visible
  const m = await br.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true, locale: 'he-IL' }); const pm = await m.newPage();
  await pm.goto(EDITOR); await wait(700);
  const mb = await pm.locator('#dlg').boundingBox();
  ok(mb && mb.x >= 8 && mb.x + mb.width <= 382 && mb.y >= 0 && mb.y + mb.height <= 844, 'welcome dialog fits inside a phone screen ' + JSON.stringify(mb && { x: Math.round(mb.x), y: Math.round(mb.y), w: Math.round(mb.width), h: Math.round(mb.height) }));
  await pm.click('#dlg button'); await wait(300);
  ok(await pm.locator('#status').isVisible(), 'save status is visible on a phone');
  ok(errs.length === 0, 'no page errors ' + JSON.stringify(errs));
  console.log('FAILS', fails); await br.close(); process.exit(fails ? 1 : 0);
})().catch(e => { console.log('CRASH', e); process.exit(2); });
