const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const path = require('path');
const vm = require('node:vm');

// מריץ את apps-script/Code.gs מול גיליון מדומה, כדי לבדוק את לוגיקת doPost בלי להתקין כלום ב-Google
function makeEnv(){
  const cache = {}; const rows = [];
  const recipes = { header: ['קטגוריה', 'שם', 'מצרכים', 'הוראות הכנה', 'הערות'], data: [['פס קר', 'סחוג', '1 ק"ג עגבניות', 'מבשלים', '']] };
  const requests = { appendRow: r => rows.push(r), getLastRow: () => rows.length + 1, getRange: () => ({ getValues: () => rows.map(r => r.concat([])) }) };
  const recipeSheet = {
    getRange(r, c, nr = 1, nc = 1){
      return { getValues: () => r === 1 ? [recipes.header] : Array.from({ length: nr }, (_, i) => Array.from({ length: nc }, (_, j) => recipes.data[r - 2 + i][c - 1 + j])), getValue: () => recipes.data[r - 2][c - 1] };
    },
    getLastColumn: () => recipes.header.length, getLastRow: () => recipes.data.length + 1,
  };
  const ctx = {
    SpreadsheetApp: { getActive: () => ({ getSheetByName: n => n === 'recipes' ? recipeSheet : n === 'בקשות תיקון' ? requests : null, getUrl: () => 'u' }) },
    LockService: { getScriptLock: () => ({ waitLock(){}, releaseLock(){} }) },
    CacheService: { getScriptCache: () => ({ get: k => cache[k] ?? null, put: (k, v) => { cache[k] = v; } }) },
    ContentService: { createTextOutput: s => s }, MailApp: { sendEmail(){} }, Session: { getEffectiveUser: () => ({ getEmail: () => 'x' }) },
    Utilities: { computeDigest: (a, s) => Buffer.from(String(s)), DigestAlgorithm: { MD5: 1 }, base64EncodeWebSafe: b => Buffer.from(b).toString('base64url') },
    Date, Math, JSON, String, Number, Object, Array, RegExp,
  };
  vm.createContext(ctx);
  vm.runInContext(fs.readFileSync(path.join(__dirname, '../apps-script/Code.gs'), 'utf8'), ctx);
  const post = body => ctx.doPost({ postData: { contents: JSON.stringify(body) } });
  return { post, rows, cache };
}
const base = { cat: 'פס קר', title: 'סחוג', field: 'מצרכים', proposed: '2 ק"ג עגבניות', sender: 'דני' };

test('accepts a valid request and records server-side current as base', () => {
  const { post, rows } = makeEnv();
  assert.strictEqual(post({ ...base, base: 'נוסח מזויף' }), 'ok');
  assert.strictEqual(rows.length, 1);
  assert.strictEqual(rows[0][4], '1 ק"ג עגבניות');
  assert.match(rows[0][7], /הגיליון השתנה/);
});
test('sender is required', () => {
  const { post, rows } = makeEnv();
  assert.strictEqual(post({ ...base, sender: '  ' }), 'invalid');
  assert.strictEqual(rows.length, 0);
});
test('per-sender limit does not lock out other senders', () => {
  const { post } = makeEnv();
  let busy = 0;
  for(let i = 0; i < 15; i++) if(post({ ...base, proposed: 'p' + i, sender: 'spammer' }) === 'busy') busy++;
  assert.ok(busy >= 5);
  assert.strictEqual(post({ ...base, proposed: 'שונה לגמרי', sender: 'דנה' }), 'ok');
});
test('limit window is fixed, key includes the window number', () => {
  const { post, cache } = makeEnv();
  post(base);
  assert.ok(Object.keys(cache).some(k => /^rs-\d+-/.test(k)));
});
test('formulas and hidden control chars are neutralised', () => {
  const { post, rows } = makeEnv();
  post({ ...base, proposed: '=HYPERLINK("http://x")', sender: '\t=cmd' });
  assert.ok(String(rows[0][5]).startsWith("'"));
  assert.ok(String(rows[0][8]).startsWith("'"));
});
test('unknown recipe is rejected', () => {
  const { post } = makeEnv();
  assert.strictEqual(post({ ...base, title: 'לא קיים' }), 'unknown');
});
