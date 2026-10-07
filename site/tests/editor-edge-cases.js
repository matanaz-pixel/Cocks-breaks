// Portable test: run with  node site/tests/NAME  (needs Playwright; set PLAYWRIGHT_PATH if it is installed globally)
const REPO = require('path').resolve(__dirname, '..', '..');
const FIX = process.env.FIXTURES || require('path').join(require('os').tmpdir(), 'site-qa-fixtures');
const SHOTS = require('path').join(require('os').tmpdir(), 'site-qa-shots'); require('fs').mkdirSync(SHOTS, { recursive: true });
const { chromium } = require(process.env.PLAYWRIGHT_PATH || 'playwright');
const D = FIX; let fails = 0; const ok = (c, m) => { if (!c) fails++; console.log((c ? 'PASS ' : 'FAIL ') + m); }; const wait = ms => new Promise(r => setTimeout(r, ms));
(async () => {
  const br = await chromium.launch(); const ctx = await br.newContext({ viewport: { width: 1440, height: 900 }, locale: 'he-IL', acceptDownloads: true }); const p = await ctx.newPage();
  const errs = []; p.on('pageerror', e => errs.push(e.message)); p.on('console', m => { if (m.type() === 'error') errs.push(m.text()); });
  let dlgMode = 'accept', dlgMsgs = []; p.on('dialog', async d => { dlgMsgs.push(d.message()); dlgMode === 'accept' ? await d.accept() : await d.dismiss(); });
  await p.goto('file://' + REPO + '/editor/site-editor.html'); await wait(800); await p.click('#dlg button');
  const tab = async id => { await p.click(`.tab[data-id="${id}"]`); await wait(400); };
  const frame = () => p.frameLocator('#frame');

  await tab('images');
  await p.setInputFiles('#file-sleep', D + '/notimage.txt'); await wait(500);
  ok((await p.locator('.img-slot').nth(3).locator('.img-err').innerText()).includes('לא הצליח לקרוא') && await p.locator('.img-slot').nth(3).locator('img').count() === 0, 'non-image file: inline red explanation shown and slot unchanged');
  await p.setInputFiles('#file-sleep', D + '/huge.jpg'); await wait(800);
  ok((await p.locator('.img-slot').nth(3).locator('.img-err').innerText()).includes('גדול מדי') && await p.locator('.img-slot').nth(3).locator('img').count() === 0, 'file over 30MB rejected with inline explanation');
  await p.setInputFiles('#file-sleep', { name: 'IMG_1.HEIC', mimeType: 'image/heic', buffer: Buffer.from('xxxxftypheicxxxx') }); await wait(600);
  ok((await p.locator('.img-slot').nth(3).locator('.img-err').innerText()).includes('HEIC') && (await p.locator('.img-slot').nth(3).locator('.img-err').innerText()).includes('וואטסאפ'), 'iPhone HEIC photo: specific explanation with the WhatsApp workaround');
  const b64 = require('fs').readFileSync(D + '/imgs/photo.JPG').toString('base64');
  await p.evaluate(async (b64) => { const bytes = Uint8Array.from(atob(b64), c => c.charCodeAt(0)); const dt = new DataTransfer(); dt.items.add(new File([bytes], 'drag.jpg', { type: 'image/jpeg' })); document.querySelector('[data-drop="sleep"]').dispatchEvent(new DragEvent('drop', { dataTransfer: dt, bubbles: true, cancelable: true })); }, b64);
  await wait(1200);
  ok(await p.locator('.img-slot').nth(3).locator('img').count() === 1 && await p.locator('.img-slot').nth(3).locator('.img-err').count() === 0, 'drag and drop onto the frame adds the photo and clears the earlier error');
  await p.click('.img-slot:nth-of-type(4) button:has-text("הסרה")'); await wait(300);
  await p.setInputFiles('#file-sleep', D + '/hero.jpg'); await wait(1500);
  await p.click('.img-slot:nth-of-type(4) button:has-text("הסרה")'); await wait(400);
  ok(await p.locator('.img-slot').nth(3).locator('img').count() === 0, 'photo removal works');
  await p.click('#btnUndo'); await wait(600);
  ok(await p.locator('.img-slot').nth(3).locator('img').count() === 1, 'undo brings the removed photo back');

  await tab('contact');
  await p.getByLabel('טלפון ווואטסאפ').fill('abc'); await wait(300);
  ok(await p.locator('label:has-text("טלפון ווואטסאפ") .ph-flag').isVisible(), 'invalid phone is flagged next to the field');
  await tab('publish');
  ok((await p.locator('.issues .must').allInnerTexts()).some(t => t.includes('הטלפון')), 'invalid phone listed as must-fix on publish tab');
  dlgMode = 'dismiss'; dlgMsgs = [];
  let downloaded = false; p.once('download', () => downloaded = true);
  await p.click('#btnDownload'); await wait(1200);
  ok(dlgMsgs.length === 1 && dlgMsgs[0].includes('הטלפון') && !downloaded, 'download with must-fix issues asks first; cancelling prevents the download');
  dlgMode = 'accept';
  const [dl] = await Promise.all([p.waitForEvent('download'), p.click('#btnDownload')]); ok(!!dl.suggestedFilename(), 'confirming allows download anyway');

  await tab('contact');
  await p.getByLabel('השם שלך').fill('א'.repeat(6000)); await wait(1000);
  ok(await p.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth), 'editor survives 6000-char name (no overflow)');
  ok((await frame().locator('.brand').innerText()).length > 5000 && await frame().locator('body').evaluate(b => document.documentElement.scrollWidth <= document.documentElement.clientWidth + 2), 'preview survives 6000-char name without horizontal scroll');
  await p.getByLabel('השם שלך').fill('דנה'); await wait(600);

  // focus ring + keyboard
  await p.getByLabel('השם שלך').focus();
  const ring = await p.getByLabel('השם שלך').evaluate(e => { const s = getComputedStyle(e); return s.boxShadow !== 'none' || (s.outlineStyle !== 'none' && s.outlineWidth !== '0px'); });
  ok(ring, 'text field shows a visible focus indicator');
  await p.keyboard.press('Tab'); ok(await p.evaluate(() => document.activeElement.tagName) === 'INPUT', 'Tab moves to next field');

  // service tab related-links labels & funding warning present
  await tab('home'); await p.locator('summary', { hasText: 'השתתפות כספית' }).click();
  ok(await p.locator('.notice:has-text("הנוסח הזה חשוב")').isVisible(), 'funding section shows the verify-wording warning');
  await tab('svc1'); await p.locator('summary', { hasText: 'קישורים לשירותים אחרים' }).click();
  ok((await p.locator('.item b').allInnerTexts()).some(t => t.startsWith('קישור אל:')), 'related-link items are named by target service');

  ok(errs.length === 0, 'no page errors: ' + JSON.stringify(errs));
  console.log('FAILS', fails); await br.close(); process.exit(fails ? 1 : 0);
})().catch(e => { console.log('CRASH', e); process.exit(2); });
