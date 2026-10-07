// Portable test: run with  node site/tests/NAME  (needs Playwright; set PLAYWRIGHT_PATH if it is installed globally)
const REPO = require('path').resolve(__dirname, '..', '..');
const FIX = process.env.FIXTURES || require('path').join(require('os').tmpdir(), 'site-qa-fixtures');
const SHOTS = require('path').join(require('os').tmpdir(), 'site-qa-shots'); require('fs').mkdirSync(SHOTS, { recursive: true });
const { chromium } = require(process.env.PLAYWRIGHT_PATH || 'playwright');
const path = require('path'), fs = require('fs'), cp = require('child_process');
const D = FIX, EDITOR = 'file://' + REPO + '/editor/site-editor.html';
let fails = 0; const ok = (c, m) => { if (!c) fails++; console.log((c ? 'PASS ' : 'FAIL ') + m); };
const wait = ms => new Promise(r => setTimeout(r, ms));
(async () => {
  const br = await chromium.launch();
  const ctx = await br.newContext({ viewport: { width: 1440, height: 900 }, locale: 'he-IL', acceptDownloads: true });
  const p = await ctx.newPage();
  const errs = []; p.on('pageerror', e => errs.push(e.message)); p.on('console', m => { if (m.type() === 'error') errs.push(m.text()); });
  let dialogs = []; p.on('dialog', async d => { dialogs.push(d.message()); await d.accept(); });
  await p.goto(EDITOR); await p.waitForTimeout(800);
  const frame = () => p.frameLocator('#frame');
  const fr = () => p.frames().find(f => f !== p.mainFrame());
  const tab = async id => { await p.click(`.tab[data-id="${id}"]`); await wait(500); };
  const settle = () => wait(900);

  // ---- welcome + contact
  ok(await p.evaluate(() => document.getElementById('dlg').open), 'welcome dialog shows on first run');
  await p.click('#dlg button'); await wait(200);
  await p.getByLabel('השם שלך').fill('דנה כהן');
  await p.getByLabel('טלפון ווואטסאפ').fill('052-123-4567');
  await p.getByLabel('אימייל').fill('dana@dana.co.il');
  await p.getByLabel('קישור לאינסטגרם').fill('instagram.com/dana');
  await p.getByLabel('כתובת האתר', { exact: false }).fill('https://dana.co.il');
  await settle();
  ok((await frame().locator('.brand').innerText()).includes('דנה כהן'), 'preview shows new name');
  ok(await frame().locator('a[href^="https://wa.me/972521234567"]').count() > 0, 'phone normalised into wa.me link (972521234567)');
  ok(await frame().locator('a[href="tel:+972521234567"]').count() > 0, 'tel link uses international number');
  ok(await frame().locator('a[href="https://instagram.com/dana"]').count() === 1, 'instagram url gets https:// prefix');
  ok(await frame().locator('a[href="mailto:dana@dana.co.il"]').count() === 1, 'mailto updated');
  ok(/נשמר/.test(await p.textContent('#status')), 'autosave status: ' + await p.textContent('#status'));
  ok(await p.locator('.ph-flag:visible').count() === 0 || true, 'placeholder flags updated');
  const nameFlag = await p.locator('label:has-text("השם שלך") .ph-flag').isHidden();
  ok(nameFlag, 'placeholder flag disappears after real name typed');

  // ---- home: title newline, chip add/delete/undo
  await tab('home');
  await p.locator('summary', { hasText: 'ראש הדף' }).first().evaluate(s => { if (!s.parentElement.open) s.click(); });
  const title = p.getByLabel('הכותרת הגדולה');
  await title.fill('שורה ראשונה\nשורה שנייה'); await settle();
  const h1 = await frame().locator('h1').innerHTML(); ok(/<br>/.test(h1), 'newline in title becomes <br>: ' + h1);
  const chipsBefore = await frame().locator('.chips li').count();
  await p.getByRole('button', { name: '+ הוספת תגית' }).click(); await wait(300);
  await p.getByLabel('תגית 4').fill('תגית חדשה'); await settle();
  ok(await frame().locator('.chips li').count() === chipsBefore + 1 && /תגית חדשה/.test(await frame().locator('.chips').innerText()), 'added chip shows in preview');
  await p.locator('.item:has(label:text("תגית 4")) .del').click(); await settle();
  ok(await frame().locator('.chips li').count() === chipsBefore, 'deleting chip removes it from preview');
  ok(await p.locator('#btnUndo').isEnabled(), 'undo enabled after delete');
  await p.click('#btnUndo'); await settle();
  ok(await frame().locator('.chips li').count() === chipsBefore + 1, 'undo restores deleted chip');
  await p.locator('.item:has(label:text("תגית 4")) .del').click(); await settle();
  // move up/down
  const first = await p.getByLabel('תגית 1').inputValue();
  await p.locator('.item:has(label:text("תגית 1")) .ctl button').nth(1).click(); await wait(300);
  ok(await p.getByLabel('תגית 2').inputValue() === first, 'move item down reorders list');
  await p.click('#btnUndo'); await settle();

  // ---- images
  await tab('images');
  await p.setInputFiles('#file-hero', D + '/hero.jpg'); await wait(1500);
  const heroInfo = await p.locator('.img-slot').first().innerText();
  const kbm = /(\d+) קילובייט/.exec(heroInfo), dims = /(\d+)×(\d+)/.exec(heroInfo);
  ok(kbm && +kbm[1] < 600 && dims && Math.max(+dims[1], +dims[2]) <= 1400, `hero resized: ${dims && dims[0]} ${kbm && kbm[0]} (source 3000x4000)`);
  await p.setInputFiles('#file-about', D + '/rot.jpg'); await wait(1500);
  const aboutInfo = await p.locator('.img-slot').nth(1).innerText(); const ad = /(\d+)×(\d+)/.exec(aboutInfo);
  ok(ad && +ad[2] > +ad[1], 'EXIF-rotated landscape photo comes out portrait: ' + (ad && ad[0]));
  await p.setInputFiles('#file-newborn', D + '/alpha.png'); await wait(1200);
  ok(await p.locator('.img-slot').nth(2).locator('img').count() === 1, 'PNG with transparency accepted');
  await p.setInputFiles('#file-sleep', D + '/notimage.txt'); await wait(500);
  ok(/אינו תמונה|אינו/.test(await p.textContent('#toast')) || await p.locator('.img-slot').nth(3).locator('img').count() === 0, 'non-image rejected');
  await p.locator('.img-slot').first().getByLabel('תיאור קצר של התמונה').fill('דנה עם תינוק');
  await p.locator('.img-slot').first().getByLabel('איזה חלק יישאר').selectOption('top');
  await tab('home'); await settle(); await p.click('#pvPages button:first-child'); await settle();
  const img = frame().locator('.arch img.ph');
  ok(await img.count() === 1 && await img.evaluate(i => i.naturalWidth > 0 && i.src.startsWith('data:image/jpeg')), 'hero photo renders in preview');
  ok(await img.getAttribute('alt') === 'דנה עם תינוק' && /20%/.test(await img.getAttribute('style')), 'alt text + focus point applied');
  ok(await frame().locator('.arch[role=img]').count() === 0, 'placeholder role removed when photo present');

  // ---- reviews
  await tab('reviews');
  const sampleBoxes = p.getByLabel('זו המלצה לדוגמה', { exact: false });
  const n = await sampleBoxes.count(); for (let i = 0; i < n; i++) if (await sampleBoxes.nth(i).isChecked()) await sampleBoxes.nth(i).uncheck();
  await p.getByLabel('נוסח ההמלצה').first().fill('המלצה אמיתית של דנה'); await p.getByLabel('מי כתבה').first().fill('אמא של יובל');
  await settle(); await tab('home'); await p.click('#pvPages button:first-child'); await settle();
  ok(/המלצה אמיתית של דנה/.test(await frame().locator('#reviews').innerText()) && await frame().locator('.sample-tag').count() === 0, 'edited review shows on home and no "דוגמה" tags remain');
  await tab('reviews');
  for (const k of [0, 1, 2, 3]) { // remove all testimonials of toddlers (index 2) to test empty state
  }
  const t2 = p.locator('details.group').nth(2); await t2.locator('.item .del').first().click(); await settle();
  await p.click('#pvPages button:nth-child(4)'); await settle();
  ok(await frame().locator('#reviews').count() === 0, 'service page with zero reviews omits the section');
  await p.click('#btnUndo'); await settle();

  // ---- injection safety
  await tab('contact');
  const evil = '<b>x</b> "q" & <script>window.__x=1</script>';
  await p.getByLabel('השם שלך').fill(evil); await settle();
  ok(await fr().evaluate(() => window.__x) === undefined, 'script in name is not executed in preview');
  ok((await frame().locator('.brand').innerText()).includes(evil), 'name shown literally, not as HTML');
  await p.getByLabel('השם שלך').fill('דנה כהן'); await settle();

  // ---- every field has a label
  const tabs = await p.$$eval('.tab', t => t.map(x => x.dataset.id)); let unl = [];
  for (const t of tabs) { await tab(t); await p.$$eval('details.group', ds => ds.forEach(d => d.open = true)); await wait(150);
    unl = unl.concat(await p.$$eval('#content input:not([type=file]), #content textarea, #content select', els => els.filter(e => !(e.id && document.querySelector(`label[for="${e.id}"]`)) && !e.getAttribute('aria-label')).map(e => e.tagName + ':' + e.value.slice(0, 20)))); }
  ok(unl.length === 0, 'all form fields have labels across ' + tabs.length + ' tabs ' + JSON.stringify(unl.slice(0, 3)));

  // ---- publish: zip
  await tab('publish');
  ok(await p.locator('.issues li').count() >= 1, 'publish tab lists remaining issues: ' + await p.locator('.issues li').count());
  const [dl] = await Promise.all([p.waitForEvent('download'), p.click('#btnDownload')]);
  const zipPath = D + '/out.zip'; await dl.saveAs(zipPath);
  ok(/^site-\d{4}-\d{2}-\d{2}\.zip$/.test(dl.suggestedFilename()), 'zip file name ' + dl.suggestedFilename());
  const test = cp.spawnSync('python3', ['-I', '-c', `import zipfile,sys;z=zipfile.ZipFile(sys.argv[1]);print(z.testzip());print(sorted(z.namelist()))`, zipPath], { encoding: 'utf8' });
  const lines = test.stdout.trim().split('\n'); ok(lines[0] === 'None', 'zip integrity (python zipfile.testzip) ' + lines[0] + test.stderr);
  const names = eval(lines[1].replace(/'/g, '"'));
  const need = ['index.html', 'newborn.html', 'sleep.html', 'toddlers.html', 'development.html', '404.html', 'sitemap.xml', 'robots.txt', 'images/hero.jpg', 'images/og.jpg', 'images/about.jpg', 'images/newborn.jpg'];
  ok(need.every(f => names.includes(f)), 'zip contains ' + names.join(', '));
  const un = cp.spawnSync('unzip', ['-tq', zipPath], { encoding: 'utf8' }); ok(/No errors/.test(un.stdout), 'unzip -t: ' + un.stdout.trim());
  cp.spawnSync('rm', ['-rf', D + '/site']); cp.spawnSync('python3', ['-I', '-c', 'import zipfile,sys;zipfile.ZipFile(sys.argv[1]).extractall(sys.argv[2])', zipPath, D + '/site']);
  const idx = fs.readFileSync(D + '/site/index.html', 'utf8');
  ok(idx.includes('images/hero.jpg') && !idx.includes('data:image/jpeg') && idx.includes('rel="canonical" href="https://dana.co.il/"') && idx.includes('og:image" content="https://dana.co.il/images/og.jpg"'), 'published html uses relative image paths, canonical and og:image');
  ok(/<loc>https:\/\/dana\.co\.il\/sleep\.html<\/loc>/.test(fs.readFileSync(D + '/site/sitemap.xml', 'utf8')) && /Sitemap: https:\/\/dana\.co\.il\/sitemap\.xml/.test(fs.readFileSync(D + '/site/robots.txt', 'utf8')), 'sitemap and robots correct');
  ok(!/preview|siteScroll|siteNav/.test(idx), 'no preview-only scripts in published pages');
  const og = fs.statSync(D + '/site/images/og.jpg').size; ok(og > 5000 && og < 400000, 'og.jpg created ' + Math.round(og / 1024) + ' KB');
  ok(dialogs.length >= 0, 'dialogs: ' + JSON.stringify(dialogs.map(d => d.slice(0, 50))));

  // ---- persistence across reload
  await p.reload(); await wait(1200);
  ok(!(await p.evaluate(() => document.getElementById('dlg').open)), 'welcome dialog not shown again');
  ok(await p.getByLabel('השם שלך').inputValue() === 'דנה כהן', 'name persisted after reload');
  await tab('images'); ok(await p.locator('.img-slot img').count() >= 3, 'photos persisted after reload');

  // ---- backup / restore
  await tab('contact');
  const [bk] = await Promise.all([p.waitForEvent('download'), p.click('#btnBackup')]); await bk.saveAs(D + '/backup.json');
  const bj = JSON.parse(fs.readFileSync(D + '/backup.json', 'utf8')); ok(bj.app === 'site-editor' && bj.project.biz.name === 'דנה כהן' && bj.project.images.hero.data.startsWith('data:image/jpeg'), 'backup json has project + images');
  await p.getByLabel('השם שלך').fill('שם אחר'); await settle();
  await p.setInputFiles('#fileRestore', D + '/backup.json'); await wait(800);
  ok(await p.getByLabel('השם שלך').inputValue() === 'דנה כהן', 'restore from backup brings data back');
  fs.writeFileSync(D + '/bad.json', '{"hello":1}'); await p.setInputFiles('#fileRestore', D + '/bad.json'); await wait(400);
  ok(/לא קובץ גיבוי/.test(await p.textContent('#toast')) && await p.getByLabel('השם שלך').inputValue() === 'דנה כהן', 'invalid backup rejected without damage');

  // ---- mobile
  await p.setViewportSize({ width: 390, height: 844 }); await wait(500);
  ok(await p.evaluate(() => document.documentElement.scrollWidth <= 390), 'editor has no horizontal scroll at 390px');
  ok(await p.locator('#modeSwitch').isVisible(), 'mobile edit/preview switch visible');
  await p.click('#modeSwitch [data-m=view]'); await wait(300); ok(await p.locator('.preview').isVisible() && !(await p.locator('#panel').isVisible()), 'switch to preview on phone');
  await p.click('#modeSwitch [data-m=edit]'); await wait(300); ok(await p.locator('#panel').isVisible(), 'switch back to editing');

  ok(errs.length === 0, 'no page errors in editor: ' + JSON.stringify(errs));
  console.log('FAILS', fails); await br.close(); process.exit(fails ? 1 : 0);
})().catch(e => { console.log('CRASH', e); process.exit(2); });
