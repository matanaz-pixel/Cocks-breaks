const test = require('node:test');
const assert = require('node:assert');
const R = require('../recipes-lib.js');

test('timers: Hebrew phrases', () => {
  const t = s => R.findTimes(s).map(x => x.sec);
  assert.deepStrictEqual(t('מבשלים 10 דקות'), [600]);
  assert.deepStrictEqual(t('כחצי דקה'), [30]);
  assert.deepStrictEqual(t('דקה וחצי'), [90]);
  assert.deepStrictEqual(t('רבע שעה'), [900]);
  assert.deepStrictEqual(t('שעה ורבע'), [4500]);
  assert.deepStrictEqual(t('שעה וחצי'), [5400]);
  assert.deepStrictEqual(t('1,5 שעות'), [5400]);
  assert.deepStrictEqual(t('2 שעות וחצי'), [9000]);
  assert.deepStrictEqual(t('שעתיים'), [7200]);
  assert.deepStrictEqual(t('שלוש דקות'), [180]);
  assert.deepStrictEqual(t('30 שניות'), [30]);
  assert.deepStrictEqual(t('בעוד שעה'), [3600]);
  assert.deepStrictEqual(t('אין זמן כאן'), []);
});
test('timers: ranges keep both ends', () => {
  const [x] = R.findTimes('אופים 40-60 דקות');
  assert.strictEqual(x.sec, 2400); assert.strictEqual(x.max, 3600);
  const [y] = R.findTimes('5-6 שעות');
  assert.strictEqual(y.sec, 18000); assert.strictEqual(y.max, 21600);
});
test('timers: no false hits inside words', () => {
  assert.deepStrictEqual(R.findTimes('מדקדקים ושעון'), []);
});

const rec = (title, ing, steps = [], cat = 'רטבים', note = '') => ({ title, cat, ingredients: ing, steps, note });
test('search: "שום" does not match שמן/שמנת', () => {
  const tokens = R.queryTokens('שום');
  assert.ok(R.searchScore(rec('שמן רוזמרין', ['1 ליטר שמן', '2 ענפים רוזמרין']), tokens) === 0);
  assert.ok(R.searchScore(rec('שמנת', ['1 ליטר שמנת']), tokens) === 0);
  assert.ok(R.searchScore(rec('מטבוחה', ['5 שיני שום']), tokens) > 0);
});
test('search: spelling, plural, finals', () => {
  assert.ok(R.searchScore(rec('עגבניות', ['3 עגבניות']), R.queryTokens('עגבניה')) > 0);
  assert.ok(R.searchScore(rec('פיצה', []), R.queryTokens('פצה')) > 0);
  assert.ok(R.searchScore(rec('לחם', ['לחם']), R.queryTokens('לחם')) > 0);
});
test('search: title outranks ingredient outranks steps', () => {
  const t = R.queryTokens('בצל');
  const a = R.searchScore(rec('קרם בצל', []), t), b = R.searchScore(rec('מרק', ['1 בצל']), t), c = R.searchScore(rec('מרק', [], ['מוסיפים בצל']), t);
  assert.ok(a > b && b > c && c > 0);
});
test('search: typo only with fuzzy', () => {
  const t = R.queryTokens('ויניגט');
  const r = rec('ויניגרט כללי', []);
  assert.strictEqual(R.searchScore(r, t), 0);
  assert.ok(R.searchScore(r, t, true) > 0);
});
test('search: all tokens required', () => {
  assert.strictEqual(R.searchScore(rec('מרק', ['בצל']), R.queryTokens('בצל גזר')), 0);
});

test('scaling: lead quantity only', () => {
  assert.strictEqual(R.scaleLine('2 ק"ג בצל', 2), '4 ק"ג בצל');
  assert.strictEqual(R.scaleLine('500 גרם סוכר', 3), '1.5 ק"ג סוכר');
  assert.strictEqual(R.scaleLine('חצי ליטר חלב', 2), '1 ליטר חלב');
});
test('scaling: flags lines with extra quantities', () => {
  assert.ok(R.hasUnscaledQty('6 גרם ג\'לטין (מומס ב-24 גרם מים)'));
  assert.ok(R.hasUnscaledQty('100 גרם אריסה לכל 2 ק"ג'));
  assert.ok(!R.hasUnscaledQty('2 ק"ג בצל'));
});
test('scaling: step quantities are found for highlighting', () => {
  const q = R.splitQuantities('מערבבים עם 400 גרם מלח ומוסיפים 2 ליטר מים').filter(p => p.q).map(p => p.t);
  assert.deepStrictEqual(q, ['400 גרם', '2 ליטר']);
});
test('multiplier parsing', () => {
  assert.strictEqual(R.parseMultiplier('2'), 2);
  assert.strictEqual(R.parseMultiplier('1,5'), 1.5);
  assert.strictEqual(R.parseMultiplier('½'), 0.5);
  assert.strictEqual(R.parseMultiplier('0'), null);
  assert.strictEqual(R.parseMultiplier('abc'), null);
});
test('yield with portions', () => {
  assert.strictEqual(R.yieldText('מנות: 100 (270 גרם למנה)', 2), '200 מנות (270 גרם למנה)');
});

test('CSV parse + table', () => {
  const csv = 'קטגוריה,שם,מצרכים,הוראות הכנה,הערות,תפריט נוכחי\nרטבים,"רוטב, אדום","1 ק""ג עגבניות\n2 בצלים","מבשלים 10 דקות",,כן\n';
  const t = R.parseTable(csv);
  assert.strictEqual(t.length, 1);
  assert.strictEqual(t[0].title, 'רוטב, אדום');
  assert.deepStrictEqual(t[0].ingredients, ['1 ק"ג עגבניות', '2 בצלים']);
  assert.strictEqual(t[0].current, true);
});
test('table without required columns is rejected', () => {
  assert.strictEqual(R.parseTable('a,b\n1,2\n'), null);
});
