// בדיקת תקינות לקבצי המתכונים שבריפו. נכשל על שגיאות מבנה, ומדפיס אזהרות תוכן (לא מכשיל).
const fs = require('fs');
const path = require('path');
const R = require('../recipes-lib.js');
const root = path.join(__dirname, '..');
const errors = [], warns = [];
function load(file){
  const text = fs.readFileSync(path.join(root, file), 'utf8');
  const t = R.parseTable(text);
  if(!t){ errors.push(file + ': חסרות עמודות חובה (' + R.REQUIRED.map(k => R.COLUMNS[k]).join(', ') + ') או שאין שורות'); return []; }
  return t;
}
const main = load('recipes.csv'), extra = load('recipes-extra.csv');
const key = r => r.cat + '|' + r.title;
function check(file, list){
  const seen = new Set();
  for(const r of list){
    const k = key(r);
    if(!r.cat) errors.push(`${file}: "${r.title}" בלי קטגוריה`);
    if(r.title.includes('|')) errors.push(`${file}: "${r.title}" מכיל | (מפתח המתכון נשבר)`);
    if(seen.has(k)) errors.push(`${file}: כפילות ${k}`);
    seen.add(k);
    if(!r.ingredients.length) errors.push(`${file}: ${k} בלי מצרכים`);
    if(!r.steps.length) warns.push(`${file}: ${k} בלי הוראות הכנה`);
    if(!/^(תפוקה|מנות):/m.test(r.note)) warns.push(`${file}: ${k} בלי שורת תפוקה`);
    if(!/אלרגנ|חיי מדף/.test(r.note)) warns.push(`${file}: ${k} בלי אלרגנים/חיי מדף`);
    r.ingredients.forEach(l => { if(!l.endsWith(':') && !/\d|חצי|רבע|שליש/.test(l)) warns.push(`${file}: ${k}: מצרך בלי כמות: ${l}`); });
    for(const v of r.variants || []) if(!v.ingredients.length) errors.push(`${file}: ${k}: גרסה ריקה ${v.label}`);
  }
}
check('recipes.csv', main); check('recipes-extra.csv', extra);
// recipes-extra.csv הוא עותק של שורות שקיימות ב-recipes.csv בלבד, ואסור שיסטה ממנו
const byKey = new Map(main.map(r => [key(r), r]));
for(const r of extra){
  const m = byKey.get(key(r));
  if(!m) errors.push(`recipes-extra.csv: ${key(r)} לא קיים ב-recipes.csv`);
  else if(JSON.stringify([m.ingredients, m.steps, m.note]) !== JSON.stringify([r.ingredients, r.steps, r.note])) errors.push(`recipes-extra.csv: ${key(r)} שונה מהעותק ב-recipes.csv`);
}
const titles = {}; main.forEach(r => (titles[r.title] ||= []).push(r.cat));
for(const [t, cats] of Object.entries(titles)) if(cats.length > 1) warns.push(`שם כפול בין קטגוריות: ${t} (${cats.join(', ')})`);
console.log(`מתכונים: ${main.length} (+${extra.length} ב-extra)`);
const grouped = {};
warns.forEach(w => { const k = w.replace(/^[^:]+: .*?: ?/, '').includes('בלי') ? w.match(/בלי [^:]+|שם כפול|מצרך בלי כמות/)?.[0] : 'אחר'; grouped[k] = (grouped[k] || 0) + 1; });
console.log('אזהרות תוכן:', grouped);
if(process.argv.includes('--verbose')) warns.forEach(w => console.log('  אזהרה:', w));
if(errors.length){ errors.forEach(e => console.error('שגיאה:', e)); process.exit(1); }
console.log('בדיקת CSV עברה.');
