/**
 * בקשות תיקון לספר המתכונים.
 *
 * התקנה (חד-פעמית): בגיליון, תפריט הרחבות > Apps Script, מדביקים את הקובץ הזה,
 * שמירה, ואז פריסה > פריסה חדשה > סוג: אפליקציית אינטרנט
 *   - ביצוע בתור: אני | מי יכול לגשת: כל אחד
 * מאשרים הרשאות ומעתיקים את כתובת ה-Web App ל-CORRECTION_URL ב-recipes.html.
 *
 * הזרימה: האתר שולח בקשה (doPost) > נוספת שורה בטאב "בקשות תיקון" בסטטוס "ממתין" >
 * מי שיש לו עריכה בגיליון בוחר "אושר" בתפריט הנפתח > onEdit מעדכן את הגיליון הראשי.
 */
const RECIPES_SHEET = 'recipes';
const REQUESTS_SHEET = 'בקשות תיקון';
const FIELD_COLUMN = { 'שם': 2, 'מצרכים': 3, 'הוראות הכנה': 4, 'הערות': 5 };
const HEADERS = ['זמן', 'קטגוריה', 'שם מתכון', 'שדה', 'ערך נוכחי', 'ערך מוצע', 'הערה', 'שולח', 'סטטוס'];
const STATUS_COL = 9;
const ST = { PENDING: 'ממתין', APPROVED: 'אושר', FORCE: 'אשר בכל מקרה', REJECTED: 'נדחה', APPLIED: 'הוחל', CONFLICT: 'התנגשות' };
const NOTIFY_OWNER = true;

function doGet() {
  return ContentService.createTextOutput('ok');
}

function doPost(e) {
  const lock = LockService.getScriptLock();
  try {
    const d = JSON.parse(e.postData.contents);
    if (d.website) return reply('ok');                       // מלכודת לבוטים
    const cat = clip(d.cat, 100), title = clip(d.title, 200), field = clip(d.field, 30);
    const proposed = clip(d.proposed, 5000).trim();
    if (!proposed || !FIELD_COLUMN[field]) return reply('invalid');

    lock.waitLock(15000);
    const recipes = SpreadsheetApp.getActive().getSheetByName(RECIPES_SHEET);
    const row = findRecipeRow(recipes, cat, title);
    if (!row) return reply('unknown');
    const current = String(recipes.getRange(row, FIELD_COLUMN[field]).getValue());
    if (norm(current) === norm(proposed)) return reply('same');

    requestsSheet().appendRow([new Date(), cat, title, field, current, safe(proposed), clip(d.comment, 300), clip(d.sender, 60), ST.PENDING]);
    notify(cat, title, field);
    return reply('ok');
  } catch (err) {
    return reply('error');
  } finally {
    try { lock.releaseLock(); } catch (x) {}
  }
}

/** מופעל אוטומטית בכל עריכה בגיליון. פועל רק כשמשנים סטטוס בטאב הבקשות. */
function onEdit(e) {
  const sh = e.range.getSheet();
  if (sh.getName() !== REQUESTS_SHEET || e.range.getColumn() !== STATUS_COL || e.range.getRow() < 2) return;
  const status = e.range.getValue();
  if (status === ST.APPROVED || status === ST.FORCE) applyRequest(sh, e.range.getRow(), status === ST.FORCE);
}

function applyRequest(sh, r, force) {
  const [, cat, title, field, oldValue, proposed] = sh.getRange(r, 1, 1, STATUS_COL).getValues()[0];
  const statusCell = sh.getRange(r, STATUS_COL);
  const recipes = SpreadsheetApp.getActive().getSheetByName(RECIPES_SHEET);
  const row = findRecipeRow(recipes, cat, title);
  const col = FIELD_COLUMN[field];
  if (!row || !col) { statusCell.setValue(ST.CONFLICT); return; }
  const cell = recipes.getRange(row, col);
  if (!force && norm(String(cell.getValue())) !== norm(String(oldValue))) {
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
  sh = ss.insertSheet(REQUESTS_SHEET);
  sh.appendRow(HEADERS);
  sh.setFrozenRows(1);
  sh.getRange(1, 1, 1, HEADERS.length).setFontWeight('bold');
  sh.setColumnWidths(5, 2, 320);
  sh.getRange(2, STATUS_COL, 999, 1).setDataValidation(
    SpreadsheetApp.newDataValidation().requireValueInList(Object.keys(ST).map(k => ST[k]), true).setAllowInvalid(false).build());
  return sh;
}

function findRecipeRow(sheet, cat, title) {
  const values = sheet.getRange(2, 1, Math.max(sheet.getLastRow() - 1, 1), 2).getValues();
  for (let i = 0; i < values.length; i++) {
    if (String(values[i][0]).trim() === String(cat).trim() && String(values[i][1]).trim() === String(title).trim()) return i + 2;
  }
  return 0;
}

function notify(cat, title, field) {
  if (!NOTIFY_OWNER) return;
  try {
    MailApp.sendEmail(Session.getEffectiveUser().getEmail(), 'בקשת תיקון חדשה בספר המתכונים',
      cat + ' / ' + title + ' (' + field + ')\nלאישור: ' + SpreadsheetApp.getActive().getUrl());
  } catch (err) {}
}

const clip = (v, n) => String(v == null ? '' : v).slice(0, n);
const norm = s => String(s).replace(/\r/g, '').split('\n').map(x => x.trim()).filter(Boolean).join('\n');
const safe = s => (/^[=+\-@]/.test(s) ? "'" + s : s);        // מונע פרשנות כנוסחה
const reply = s => ContentService.createTextOutput(s);
