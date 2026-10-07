// Portable test: run with  node site/tests/NAME  (needs Playwright; set PLAYWRIGHT_PATH if it is installed globally)
const REPO = require('path').resolve(__dirname, '..', '..');
const FIX = process.env.FIXTURES || require('path').join(require('os').tmpdir(), 'site-qa-fixtures');
const SHOTS = require('path').join(require('os').tmpdir(), 'site-qa-shots'); require('fs').mkdirSync(SHOTS, { recursive: true });
const { chromium } = require(process.env.PLAYWRIGHT_PATH || 'playwright');
const fs = require('fs'), path = require('path');
const files = fs.readdirSync(FIX + '/imgs').filter(f => !/[^\x00-\x7f]/.test(f)).sort();
// extra cases: HEIC bytes (iPhone) with image/heic type, and empty mime type
const extra = [
  { name: 'IMG_0001.HEIC', mimeType: 'image/heic', buffer: Buffer.from('....ftypheic....garbage') },
  { name: 'תמונה שלי.jpeg', mimeType: 'image/jpeg', buffer: fs.readFileSync(FIX + '/imgs/photo.JPG') },
  { name: 'noext', mimeType: '', buffer: fs.readFileSync(FIX + '/imgs/photo.JPG') },
  { name: 'photo.jpg', mimeType: '', buffer: fs.readFileSync(FIX + '/imgs/photo.JPG') },
];
(async () => {
  const b = await chromium.launch(); const ctx = await b.newContext({ viewport: { width: 1440, height: 900 }, locale: 'he-IL' });
  const p = await ctx.newPage(); const errs = []; p.on('pageerror', e => errs.push(e.message));
  await p.goto('file://' + REPO + '/editor/site-editor.html'); await p.waitForTimeout(700); await p.click('#dlg button');
  await p.click('.tab[data-id="images"]'); await p.waitForTimeout(300);
  const cases = files.map(f => ({ label: f, arg: FIX + '/imgs/' + f })).concat(extra.map(e => ({ label: e.name + ' [' + (e.mimeType || 'no mime') + ']', arg: e })));
  for (const c of cases) {
    await p.evaluate(() => { document.getElementById('toast').hidden = true; });
    await p.setInputFiles('#file-hero', c.arg).catch(e => console.log('setInputFiles error', e.message));
    const t0 = Date.now(); let res = 'no reaction';
    for (let i = 0; i < 60; i++) { await p.waitForTimeout(250);
      const has = await p.locator('.img-slot').first().locator('img').count(); const toast = await p.evaluate(() => { const e = document.querySelector('.img-slot .img-err'); return e ? e.textContent.slice(0, 70) + '…' : ''; });
      if (has) { res = 'OK ' + (await p.locator('.img-slot').first().innerText()).match(/\d+×\d+/)[0]; break; } if (toast) { res = 'MESSAGE: ' + toast; break; } }
    console.log((res.startsWith('OK') ? 'ok   ' : 'FAIL ') + c.label.padEnd(34) + res + '  (' + (Date.now() - t0) + 'ms)');
    if (res.startsWith('OK')) { await p.click('.img-slot >> nth=0 >> button:has-text("הסרה")'); await p.waitForTimeout(250); }
  }
  console.log('page errors:', JSON.stringify(errs)); await b.close();
})();
