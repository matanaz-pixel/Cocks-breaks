/**
 * בקשות תיקון לספר המתכונים.
 *
 * התקנה (חד-פעמית):
 * 1. בגיליון: הרחבות > Apps Script. מוחקים את מה שיש, מדביקים את הקובץ הזה ושומרים.
 * 2. פריסה > פריסה חדשה > סוג: אפליקציית אינטרנט. ביצוע בתור: אני. מי יכול לגשת: כל אחד.
 * 3. מאשרים הרשאות. אם מופיע "Google לא אימתה את האפליקציה": Advanced > Go to (unsafe). זה הסקריפט שלך.
 * 4. מעתיקים את כתובת ה-Web App (מסתיימת ב-/exec) ל-CORRECTION_URL ב-recipes.html.
 * אחרי שינוי בקוד: פריסה > ניהול פריסות > עריכה > גרסה חדשה (אחרת הכתובת ממשיכה להריץ את הגרסה הקודמת).
 *
 * חשוב: הגיליון צריך להיות משותף לצפייה בלבד. כל מי שיכול לערוך את הגיליון יכול גם לאשר בקשות.
 *
 * הזרימה: האתר שולח בקשה (doPost) > נוספת שורה בטאב "בקשות תיקון" בסטטוס "ממתין" >
 * בוחרים "אושר" בעמודת הסטטוס (אפשר כמה שורות יחד) > onEdit מעדכן את טאב המתכונים.
 */
const RECIPES_SHEET = 'recipes';
const REQUESTS_SHEET = 'בקשות תיקון';
const FIELDS = { 'שם': 'שם', 'מצרכים': 'מצרכים', 'הוראות הכנה': 'הוראות הכנה', 'הערות': 'הערות' };   // שדה > כותרת העמודה בטאב המתכונים
const HEADERS = ['זמן', 'קטגוריה', 'שם מתכון', 'שדה', 'הנוסח שהטבח ראה', 'נוסח מוצע', 'הערה', 'שולח', 'סטטוס'];
const STATUS_COL = 9;
const ST = { PENDING: 'ממתין', APPROVED: 'אושר', FORCE: 'אשר בכל מקרה', REJECTED: 'נדחה', APPLIED: 'הוחל', CONFLICT: 'התנגשות', NOTFOUND: 'מתכון לא נמצא' };
const RATE_LIMIT = 30;            // בקשות לכל 10 דקות, לכל השולחים יחד
const NOTIFY_OWNER = true;        // מייל לבעלים, לכל היותר פעם בשעה

function doGet() {
  return reply('ok');
}

function doPost(e) {
  const lock = LockService.getScriptLock();
  try {
    const d = JSON.parse(e.postData.contents);
    if (d.website) return reply('ok');                       // מלכודת לבוטים
    const cat = clip(d.cat, 100).trim(), title = clip(d.title, 200).trim(), field = clip(d.field, 30);
    const proposed = clip(d.proposed, 5000).trim();
    if (!proposed || !FIELDS[field]) return reply('invalid');
    if (!rateOk()) return reply('busy');

    lock.waitLock(15000);
    const recipes = SpreadsheetApp.getActive().getSheetByName(RECIPES_SHEET);
    const row = findRecipeRow(recipes, cat, title);
    if (!row) return reply('unknown');
    const current = String(recipes.getRange(row, columnOf(recipes, FIELDS[field])).getValue());
    if (norm(current) === norm(proposed)) return reply('same');
    // בסיס ההשוואה הוא הנוסח שהטבח ראה בדף. אם הגיליון השתנה מאז, האישור יסומן "התנגשות" ולא ידרוס את השינוי.
    const base = d.base == null ? current : clip(d.base, 5000);

    const sh = requestsSheet();
    if (isDuplicate(sh, cat, title, field, proposed)) return reply('ok');
    sh.appendRow([new Date(), cat, title, field, base, proposed, clip(d.comment, 300), clip(d.sender, 60), ST.PENDING].map(safe));
    notify();
    return reply('ok');
  } catch (err) {
    return reply('error');
  } finally {
    try { lock.releaseLock(); } catch (x) {}
  }
}

/** מופעל אוטומטית בכל עריכה בגיליון. פועל רק על עמודת הסטטוס בטאב הבקשות, גם בהדבקה על כמה שורות. */
function onEdit(e) {
  const range = e.range, sh = range.getSheet();
  if (sh.getName() !== REQUESTS_SHEET) return;
  const c0 = range.getColumn(), c1 = c0 + range.getNumColumns() - 1;
  if (STATUS_COL < c0 || STATUS_COL > c1) return;
  const r0 = Math.max(range.getRow(), 2), r1 = range.getRow() + range.getNumRows() - 1;
  for (let r = r0; r <= r1; r++) {
    const status = String(sh.getRange(r, STATUS_COL).getValue()).trim();
    if (status === ST.APPROVED || status === ST.FORCE) applyRequest(sh, r, status === ST.FORCE);
  }
}

function applyRequest(sh, r, force) {
  const [, cat, title, field, base, proposed] = sh.getRange(r, 1, 1, STATUS_COL).getValues()[0];
  const statusCell = sh.getRange(r, STATUS_COL);
  const recipes = SpreadsheetApp.getActive().getSheetByName(RECIPES_SHEET);
  const row = findRecipeRow(recipes, cat, title);
  const col = FIELDS[field] ? columnOf(recipes, FIELDS[field]) : 0;
  if (!row || !col) { statusCell.setValue(ST.NOTFOUND); return; }   // למשל אם שם המתכון שונה מאז הבקשה
  const cell = recipes.getRange(row, col);
  if (!force && norm(String(cell.getValue())) !== norm(String(base))) {
    statusCell.setValue(ST.CONFLICT);                        // המתכון שונה מאז הבקשה: בודקים ידנית או "אשר בכל מקרה"
    return;
  }
  cell.setValue(safe(String(proposed)));
  statusCell.setValue(ST.APPLIED);
}

function requestsSheet() {
  const ss = SpreadsheetApp.getActive();
  let sh = ss.getSheetByName(REQUESTS_SHEET);
  if (sh) return sh;
  sh = ss.insertSheet(REQUESTS_SHEET, ss.getSheets().length);
  sh.appendRow(HEADERS);
  sh.setFrozenRows(1);
  sh.getRange(1, 1, 1, HEADERS.length).setFontWeight('bold');
  sh.setColumnWidths(5, 2, 320);
  sh.getRange(2, STATUS_COL, sh.getMaxRows() - 1, 1).setDataValidation(
    SpreadsheetApp.newDataValidation().requireValueInList(Object.keys(ST).map(k => ST[k]), true).setAllowInvalid(true).build());
  return sh;
}

function columnOf(sheet, name) {
  const header = sheet.getRange(1, 1, 1, sheet.getLastColumn()).getValues()[0].map(h => String(h).trim());
  return header.indexOf(name) + 1;
}

function findRecipeRow(sheet, cat, title) {
  const cc = columnOf(sheet, 'קטגוריה'), tc = columnOf(sheet, 'שם');
  if (!cc || !tc || sheet.getLastRow() < 2) return 0;
  const n = sheet.getLastRow() - 1;
  const cats = sheet.getRange(2, cc, n, 1).getValues(), titles = sheet.getRange(2, tc, n, 1).getValues();
  for (let i = 0; i < n; i++) {
    if (String(cats[i][0]).trim() === String(cat).trim() && String(titles[i][0]).trim() === String(title).trim()) return i + 2;
  }
  return 0;
}

function isDuplicate(sh, cat, title, field, proposed) {
  const last = sh.getLastRow();
  if (last < 2) return false;
  const from = Math.max(2, last - 49);
  const rows = sh.getRange(from, 1, last - from + 1, STATUS_COL).getValues();
  return rows.some(r => r[1] === safe(cat) && r[2] === safe(title) && r[3] === field && norm(r[5]) === norm(safe(proposed)) && r[8] === ST.PENDING);
}

function rateOk() {
  const cache = CacheService.getScriptCache();
  const n = Number(cache.get('req-count') || 0) + 1;
  cache.put('req-count', String(n), 600);
  return n <= RATE_LIMIT;
}

function notify() {
  if (!NOTIFY_OWNER) return;
  const cache = CacheService.getScriptCache();
  if (cache.get('notified')) return;
  cache.put('notified', '1', 3600);
  try {
    MailApp.sendEmail(Session.getEffectiveUser().getEmail(), 'יש בקשות תיקון חדשות בספר המתכונים',
      'לאישור: טאב "' + REQUESTS_SHEET + '" בגיליון\n' + SpreadsheetApp.getActive().getUrl());
  } catch (err) {}
}

const clip = (v, n) => String(v == null ? '' : v).slice(0, n);
const norm = s => String(s).replace(/^'/, '').replace(/\r/g, '').split('\n').map(x => x.trim()).filter(Boolean).join('\n');
const safe = s => (typeof s === 'string' && /^[=+\-@]/.test(s) ? "'" + s : s);   // מונע פרשנות כנוסחה
const reply = s => ContentService.createTextOutput(s);
