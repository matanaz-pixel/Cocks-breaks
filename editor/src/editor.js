/* עורך האתר: כל הלוגיקה בדפדפן. אין שרת ואין צורך באינטרנט. */
(function () {
  'use strict';
  var ASSETS = /*ASSETS*/null;
  var DEFAULTS = /*DEFAULTS*/null;
  var Gen = window.SiteGen;
  var $ = function (id) { return document.getElementById(id); };

  /* ---------- כלים ---------- */
  function h(tag, attrs) {
    var e = document.createElement(tag), kids = Array.prototype.slice.call(arguments, 2);
    Object.keys(attrs || {}).forEach(function (k) {
      var v = attrs[k];
      if (v == null || v === false) return;
      if (k === 'class') e.className = v;
      else if (k === 'text') e.textContent = v;
      else if (k.indexOf('on') === 0) e.addEventListener(k.slice(2), v);
      else if (k === 'value') e.value = v;
      else if (k === 'checked') e.checked = !!v;
      else e.setAttribute(k, v === true ? '' : v);
    });
    (function add(list) { list.forEach(function (c) { if (c == null || c === false) return; if (Array.isArray(c)) add(c); else e.append(c.nodeType ? c : document.createTextNode(c)); }); })(kids);
    return e;
  }
  var uidN = 0; function uid() { return 'f' + (++uidN); }
  function clone(o) { return JSON.parse(JSON.stringify(o)); }
  function getP(o, path) { return path.split('.').reduce(function (a, k) { return a == null ? a : a[k]; }, o); }
  function setP(o, path, v) { var ks = path.split('.'), last = ks.pop(), t = ks.reduce(function (a, k) { return a[k]; }, o); t[last] = v; }
  function mergeDefaults(def, saved) {
    if (Array.isArray(def)) return Array.isArray(saved) ? saved : def;
    if (def && typeof def === 'object') {
      var out = {}, s = (saved && typeof saved === 'object') ? saved : {};
      Object.keys(def).forEach(function (k) { out[k] = mergeDefaults(def[k], s[k]); });
      Object.keys(s).forEach(function (k) { if (!(k in out)) out[k] = s[k]; });
      return out;
    }
    return saved === undefined ? def : saved;
  }
  function mergeProject(def, saved) {
    var out = mergeDefaults(Object.assign({}, def, { services: [] }), Object.assign({}, saved, { services: [] }));
    out.services = def.services.map(function (ds) {
      var ss = (saved.services || []).filter(function (x) { return x.slug === ds.slug; })[0];
      return mergeDefaults(ds, ss || {});
    });
    out.images = (saved && saved.images) || {};
    return out;
  }
  function pad(n) { return n < 10 ? '0' + n : '' + n; }
  function hhmm(d) { return pad(d.getHours()) + ':' + pad(d.getMinutes()); }
  function dateStamp(d) { return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()); }
  var toastT;
  function toast(msg) { var t = $('toast'); t.textContent = msg; t.hidden = false; clearTimeout(toastT); toastT = setTimeout(function () { t.hidden = true; }, 3400); }
  function download(blob, name) {
    var a = h('a', { href: URL.createObjectURL(blob), download: name });
    document.body.append(a); a.click(); setTimeout(function () { URL.revokeObjectURL(a.href); a.remove(); }, 1500);
  }

  /* ---------- אחסון בדפדפן ---------- */
  var DB = {
    ok: true,
    open: function () {
      return new Promise(function (res, rej) {
        try {
          var r = indexedDB.open('site-editor', 1);
          r.onupgradeneeded = function () { r.result.createObjectStore('kv'); };
          r.onsuccess = function () { res(r.result); };
          r.onerror = function () { rej(r.error); };
        } catch (e) { rej(e); }
      });
    },
    get: function (k) { return DB.open().then(function (db) { return new Promise(function (res, rej) { var q = db.transaction('kv').objectStore('kv').get(k); q.onsuccess = function () { res(q.result); }; q.onerror = function () { rej(q.error); }; }); }); },
    set: function (k, v) { return DB.open().then(function (db) { return new Promise(function (res, rej) { var t = db.transaction('kv', 'readwrite'); t.objectStore('kv').put(v, k); t.oncomplete = function () { res(); }; t.onerror = function () { rej(t.error); }; }); }); }
  };

  /* ---------- מצב ---------- */
  var P = clone(DEFAULTS);
  var tabId = 'contact', pvPage = 'index.html', pvMode = 'desktop';
  var undoStack = [], openGroups = {}, pvScroll = {};
  var saveT, previewT, lastSaved = null, saveErr = false;

  function setStatus(txt, err) { var s = $('status'); s.textContent = txt; s.className = 'status' + (err ? ' err' : ''); }
  function pushUndo() { undoStack.push(JSON.stringify(P)); if (undoStack.length > 15) undoStack.shift(); $('btnUndo').disabled = false; }
  function changed() { schedulePreview(); scheduleSave(); updateTabDots(); }
  function scheduleSave() {
    setStatus('שומר…');
    clearTimeout(saveT);
    saveT = setTimeout(function () {
      DB.set('project', P).then(function () { lastSaved = new Date(); saveErr = false; setStatus('נשמר אוטומטית ✓ ' + hhmm(lastSaved)); })
        .catch(function () { saveErr = true; setStatus('השמירה האוטומטית לא עובדת בדפדפן הזה. שמרי קובץ גיבוי.', true); });
    }, 500);
  }

  /* ---------- מבנה הלשוניות ---------- */
  var ICON_LABELS = { heart: 'לב', sparkle: 'ניצוץ', moon: 'ירח', people: 'אנשים', clock: 'שעון', leaf: 'עלה', chat: 'שיחה', book: 'ספר', phone: 'טלפון', sprout: 'נבט', blocks: 'קוביות', baby: 'תינוק', wallet: 'ארנק', shield: 'מגן', sun: 'שמש', star: 'כוכב', check: 'וי' };
  function tabsList() {
    var t = [{ id: 'contact', label: 'פרטים וקישורים' }, { id: 'home', label: 'דף הבית' }];
    P.services.forEach(function (s, i) { t.push({ id: 'svc' + i, label: s.short }); });
    t.push({ id: 'images', label: 'תמונות' }, { id: 'reviews', label: 'המלצות' }, { id: 'publish', label: 'פרסום' });
    return t;
  }
  function pageForTab(id) {
    if (id === 'home' || id === 'contact') return 'index.html';
    var m = /^svc(\d+)$/.exec(id);
    return m ? P.services[+m[1]].file : null;
  }

  /* ---------- בניית שדות ---------- */
  function phFlag(spec, value) {
    if (!spec.ph) return null;
    var on = spec.ph(value);
    return h('span', { class: 'ph-flag', hidden: on ? null : true, text: 'עוד לא הוחלף' });
  }
  function grow(t) { if (t.scrollHeight > 0) { t.style.height = 'auto'; t.style.height = (t.scrollHeight + 2) + 'px'; } }
  function fieldText(spec, getV, setV) {
    var id = uid(), isArea = spec.t === 'area' || spec.t === 'title';
    var v = getV() || '';
    var inp = isArea ? h('textarea', { id: id, rows: spec.t === 'title' ? 2 : (spec.rows || 3) }) : h('input', { id: id, type: spec.type || 'text', class: spec.ltr ? 'ltr' : null });
    inp.value = v;
    var flag = phFlag(spec, v), count = spec.max ? h('div', { class: 'count' }) : null;
    function upd() { if (count) { var n = inp.value.length; count.textContent = n + ' תווים. מומלץ עד ' + spec.max; count.className = 'count' + (n > spec.max ? ' over' : ''); } if (flag) flag.hidden = !spec.ph(inp.value); if (isArea) grow(inp); }
    inp.addEventListener('input', function () { setV(inp.value); upd(); changed(); });
    upd();
    return h('div', { class: 'field' }, h('label', { for: id }, spec.label, flag), inp, spec.hint ? h('div', { class: 'hint', text: spec.hint }) : null, count);
  }
  function fieldCheck(spec, getV, setV) {
    var id = uid(), c = h('input', { id: id, type: 'checkbox', checked: !!getV() });
    c.addEventListener('change', function () { setV(c.checked); changed(); if (spec.rerender) renderContent(); });
    return h('div', { class: 'field' }, h('label', { class: 'check', for: id }, c, h('span', {}, spec.label, spec.hint ? h('span', { class: 'hint', style: 'display:block;font-weight:400', text: spec.hint }) : null)));
  }
  function fieldIcon(spec, getV, setV) {
    var id = uid(), s = h('select', { id: id });
    Gen.ICON_NAMES.forEach(function (n) { s.append(h('option', { value: n, text: ICON_LABELS[n] || n })); });
    s.value = getV() || 'heart';
    s.addEventListener('change', function () { setV(s.value); changed(); });
    return h('div', { class: 'field' }, h('label', { for: id, text: spec.label }), s);
  }
  function renderOne(spec, getV, setV) {
    if (spec.t === 'check') return fieldCheck(spec, getV, setV);
    if (spec.t === 'icon') return fieldIcon(spec, getV, setV);
    return fieldText(spec, getV, setV);
  }
  function F(spec) { return renderOne(spec, function () { return getP(P, spec.path); }, function (v) { setP(P, spec.path, v); }); }

  function ctlButtons(list, i, rerender) {
    function move(d) { pushUndo(); var j = i + d, x = list[i]; list[i] = list[j]; list[j] = x; changed(); rerender(); }
    return h('div', { class: 'ctl' },
      h('button', { type: 'button', 'aria-label': 'העבר למעלה', title: 'העבר למעלה', disabled: i === 0 ? true : null, onclick: function () { move(-1); } }, '↑'),
      h('button', { type: 'button', 'aria-label': 'העבר למטה', title: 'העבר למטה', disabled: i === list.length - 1 ? true : null, onclick: function () { move(1); } }, '↓'),
      h('button', { type: 'button', class: 'del', 'aria-label': 'מחיקה', title: 'מחיקה', onclick: function () { pushUndo(); list.splice(i, 1); changed(); rerender(); toast('נמחק. אפשר לבטל בכפתור "ביטול פעולה אחרונה" למעלה'); } }, '✕'));
  }
  function strList(spec) {
    var list = getP(P, spec.path);
    var box = h('div', { class: 'field' }, h('span', { class: 'lab', text: spec.label }), spec.hint ? h('div', { class: 'hint', style: 'margin:0 0 6px', text: spec.hint }) : null);
    var wrap = h('div', { class: 'list' });
    list.forEach(function (_, i) {
      var f = renderOne({ t: 'area', rows: 2, label: (spec.itemLabel || 'פריט') + ' ' + (i + 1) }, function () { return list[i]; }, function (v) { list[i] = v; });
      wrap.append(h('div', { class: 'item' }, f, h('div', { class: 'row' }, h('span'), ctlButtons(list, i, renderContent))));
    });
    box.append(wrap, h('div', { style: 'margin-top:10px' }, h('button', { type: 'button', class: 'btn small', onclick: function () { pushUndo(); list.push(''); changed(); renderContent(); } }, '+ ' + (spec.addLabel || 'הוספה'))));
    return box;
  }
  function objList(spec) {
    var list = getP(P, spec.path);
    var box = h('div', { class: 'field' }, h('span', { class: 'lab', text: spec.label }), spec.hint ? h('div', { class: 'hint', style: 'margin:0 0 6px', text: spec.hint }) : null);
    var wrap = h('div', { class: 'list' });
    list.forEach(function (it, i) {
      var title = spec.itemTitle ? spec.itemTitle(it, i) : (spec.itemLabel || 'פריט') + ' ' + (i + 1);
      var fields = spec.fields.map(function (fs) { return renderOne(fs, function () { return it[fs.key]; }, function (v) { it[fs.key] = v; }); });
      wrap.append(h('div', { class: 'item' }, h('div', { class: 'row' }, h('b', { text: title }), spec.fixed ? null : ctlButtons(list, i, renderContent)), fields));
    });
    box.append(wrap);
    if (!spec.fixed) box.append(h('div', { style: 'margin-top:10px' }, h('button', { type: 'button', class: 'btn small', onclick: function () { pushUndo(); list.push(clone(spec.newItem)); changed(); renderContent(); } }, '+ ' + (spec.addLabel || 'הוספה'))));
    return box;
  }
  function group(tab, title, open, kids) {
    var key = tab + '|' + title;
    var d = h('details', { class: 'group' }, h('summary', { text: title }), h('div', { class: 'body' }, kids));
    if (openGroups[key] === undefined ? open : openGroups[key]) d.open = true;
    d.addEventListener('toggle', function () { openGroups[key] = d.open; d.querySelectorAll('textarea').forEach(grow); });
    return d;
  }

  /* ---------- תוכן הלשוניות ---------- */
  var PH = Gen.PLACEHOLDERS;
  function contactTab() {
    return [
      h('h2', { text: 'פרטים וקישורים' }),
      h('p', { class: 'lead', text: 'הפרטים האלה מופיעים בכל עמודי האתר: בכפתורי הוואטסאפ, בטלפון ובתחתית.' }),
      F({ path: 'biz.name', label: 'השם שלך', hint: 'מופיע בראש האתר, בכותרות ובתחתית.', ph: function (v) { return !v || v === PH.name; } }),
      F({ path: 'biz.role', label: 'התואר המקצועי', hint: 'שורה קצרה שמופיעה בתחתית האתר.' }),
      F({ path: 'biz.phone', label: 'טלפון ווואטסאפ', hint: 'מספר הנייד שאליו יגיעו ההודעות מהאתר. למשל 050-123-4567.', type: 'tel', ltr: true, ph: function (v) { var n = Gen.normalizePhone(v); return !v || v === PH.phone || n.length < 11 || n.length > 12; } }),
      F({ path: 'biz.email', label: 'אימייל', type: 'email', ltr: true, ph: function (v) { return !v || /example\.com/i.test(v); } }),
      F({ path: 'biz.instagram', label: 'קישור לאינסטגרם', hint: 'הדביקי את הקישור לפרופיל. אם תשאירי ריק, האייקון יוסתר.', type: 'text', ltr: true, ph: function (v) { return Gen.fixUrl(v) === PH.instagram; } }),
      F({ path: 'biz.facebook', label: 'קישור לפייסבוק', hint: 'אם תשאירי ריק, האייקון יוסתר.', type: 'text', ltr: true, ph: function (v) { return Gen.fixUrl(v) === PH.facebook; } }),
      F({ path: 'biz.siteUrl', label: 'כתובת האתר (כשתדעי אותה)', hint: 'למשל https://www.mysite.co.il. מאפשרת תמונת שיתוף יפה ומפת אתר לגוגל. אפשר להשאיר ריק עכשיו.', type: 'text', ltr: true }),
      F({ path: 'biz.disclaimer', t: 'area', label: 'הערה משפטית בתחתית', hint: 'מומלץ להשאיר. שינוי כדאי לעשות בהתייעצות עם איש מקצוע.' })
    ];
  }
  function homeTab() {
    var T = 'home';
    return [
      h('h2', { text: 'דף הבית' }),
      h('p', { class: 'lead', text: 'פתחי כל חלק כדי לערוך אותו. התצוגה משמאל מתעדכנת תוך שניות.' }),
      photoGroup(T, ['hero', 'about'], 'תמונות בדף הבית'),
      group(T, 'ראש הדף', true, [
        F({ path: 'home.hero_eyebrow', label: 'שורה קטנה מעל הכותרת' }),
        F({ path: 'home.hero_title', t: 'title', label: 'הכותרת הגדולה', hint: 'לחיצה על Enter יוצרת שורה חדשה.' }),
        F({ path: 'home.hero_sub', t: 'area', label: 'משפט הסבר מתחת לכותרת' }),
        strList({ path: 'home.chips', label: 'התגיות הקטנות מתחת לכפתורים', itemLabel: 'תגית', addLabel: 'הוספת תגית' })]),
      group(T, 'כותרות של אזור השירותים', false, [
        F({ path: 'home.services_title', label: 'כותרת האזור' }), F({ path: 'home.services_sub', t: 'area', label: 'משפט הסבר' }),
        h('p', { class: 'hint', text: 'את הכרטיסים של השירותים עצמם עורכים בלשונית של כל שירות.' })]),
      group(T, 'הגישה ההוליסטית', false, [
        F({ path: 'home.holistic_title', t: 'title', label: 'כותרת' }), F({ path: 'home.holistic_intro', t: 'area', label: 'פתיחה' }),
        objList({ path: 'home.hats', label: 'שלושת הכובעים', fixed: true, fields: [{ key: 'title', label: 'כותרת' }, { key: 'text', t: 'area', label: 'הסבר' }, { key: 'icon', t: 'icon', label: 'אייקון' }] }),
        objList({ path: 'home.why', label: 'למה השילוב משנה', addLabel: 'הוספת סיבה', newItem: { title: '', text: '' }, fields: [{ key: 'title', label: 'כותרת' }, { key: 'text', t: 'area', label: 'הסבר' }] }),
        F({ path: 'home.holistic_example', t: 'area', label: 'דוגמה מהחיים', hint: 'אפשר למחוק את הטקסט כדי להסתיר את התיבה.' })]),
      group(T, 'עליי', false, [
        F({ path: 'home.about_title', label: 'כותרת' }),
        strList({ path: 'home.about', label: 'פסקאות הסיפור', itemLabel: 'פסקה', addLabel: 'הוספת פסקה', hint: 'אפשר לכתוב {name} כדי שהשם שלך יוכנס אוטומטית.' }),
        strList({ path: 'home.credentials', label: 'הכשרות ותארים', itemLabel: 'הכשרה', addLabel: 'הוספת הכשרה', hint: 'כתבי רק מה שנכון ושמותר לך להציג.' })]),
      group(T, 'השתתפות כספית', false, [
        h('div', { class: 'notice' }, h('b', { text: 'שימי לב: ' }), 'הנוסח הזה חשוב. לפני פרסום ודאי שהוא תואם את התנאים בפועל של סל ההריון והמילואים.'),
        F({ path: 'home.funding_title', label: 'כותרת' }), F({ path: 'home.funding_intro', t: 'area', label: 'פתיחה' }),
        objList({ path: 'home.funding_cards', label: 'הגורמים המממנים', fixed: true, fields: [{ key: 'title', label: 'שם' }, { key: 'text', t: 'area', label: 'הסבר' }, { key: 'icon', t: 'icon', label: 'אייקון' }] }),
        strList({ path: 'home.funding_steps', label: 'שלבי התהליך', itemLabel: 'שלב', addLabel: 'הוספת שלב' }),
        F({ path: 'home.funding_note', t: 'area', label: 'הערה בתחתית' })]),
      group(T, 'שאלות נפוצות בדף הבית', false, [
        F({ path: 'home.faq_title', label: 'כותרת' }),
        objList({ path: 'home.faq', label: 'השאלות', itemLabel: 'שאלה', addLabel: 'הוספת שאלה', newItem: { q: '', a: '' }, fields: [{ key: 'q', label: 'השאלה' }, { key: 'a', t: 'area', label: 'התשובה' }] })]),
      group(T, 'יצירת קשר', false, [
        F({ path: 'home.contact_title', label: 'כותרת' }), F({ path: 'home.contact_sub', t: 'area', label: 'משפט הסבר' }), F({ path: 'home.contact_pick', label: 'שאלה מעל הכפתורים' }),
        objList({ path: 'home.contact_chips', label: 'כפתורי וואטסאפ', itemLabel: 'כפתור', addLabel: 'הוספת כפתור', newItem: { label: '', text: '' }, fields: [{ key: 'label', label: 'כיתוב על הכפתור' }, { key: 'text', t: 'area', label: 'ההודעה שתכתב מראש בוואטסאפ' }] })]),
      group(T, 'גוגל והודעת וואטסאפ', false, [
        F({ path: 'home.title', label: 'כותרת הדף בגוגל', hint: 'מופיעה אחרי השם שלך בלשונית הדפדפן ובתוצאות החיפוש.' }),
        F({ path: 'home.meta', t: 'area', label: 'תיאור קצר שמופיע בגוגל', max: 160 }),
        F({ path: 'home.wa_text', t: 'area', label: 'ההודעה המוכנה בכפתורי הוואטסאפ של דף הבית' }),
        F({ path: 'home.testimonials_title', label: 'כותרת אזור ההמלצות' })])
    ];
  }
  function serviceTab(i) {
    var S = P.services[i], T = 'svc' + i, p = 'services.' + i + '.';
    var g = [
      h('h2', { text: S.short }),
      h('p', { class: 'lead', text: 'העמוד הזה והכרטיס שלו בדף הבית.' }),
      photoGroup(T, [S.slug], 'התמונה של העמוד'),
      group(T, 'ראש העמוד', true, [F({ path: p + 'eyebrow', label: 'שורה קטנה מעל הכותרת' }), F({ path: p + 'hero_title', t: 'title', label: 'הכותרת הגדולה', hint: 'לחיצה על Enter יוצרת שורה חדשה.' }), F({ path: p + 'hero_sub', t: 'area', label: 'משפט הסבר' }), strList({ path: p + 'highlights', label: 'שלוש הנקודות מתחת להסבר', itemLabel: 'נקודה', addLabel: 'הוספת נקודה' })]),
      group(T, 'הכרטיס בדף הבית ושם השירות', false, [F({ path: p + 'short', label: 'שם השירות', hint: 'מופיע בתפריטים, בתחתית האתר ובכותרות.' }), F({ path: p + 'card_pain', label: 'שורה על הקושי של ההורים' }), F({ path: p + 'card_promise', t: 'area', label: 'ההבטחה של השירות' })]),
      group(T, 'זה בשבילכם אם', false, [F({ path: p + 'fits_title', label: 'כותרת' }), strList({ path: p + 'fits', label: 'המצבים', itemLabel: 'מצב', addLabel: 'הוספת מצב' })])
    ];
    if (S.approach && S.approach.length) g.push(group(T, 'הגישה', false, [F({ path: p + 'approach_title', label: 'כותרת' }), F({ path: p + 'approach_intro', t: 'area', label: 'פתיחה' }), objList({ path: p + 'approach', label: 'עקרונות הגישה', itemLabel: 'עיקרון', addLabel: 'הוספת עיקרון', newItem: { icon: 'heart', title: '', text: '' }, fields: [{ key: 'title', label: 'כותרת' }, { key: 'text', t: 'area', label: 'הסבר' }, { key: 'icon', t: 'icon', label: 'אייקון' }] })]));
    g.push(
      group(T, 'מה כלול', false, [F({ path: p + 'included_title', label: 'כותרת' }), objList({ path: p + 'included', label: 'הפריטים', itemLabel: 'פריט', addLabel: 'הוספת פריט', newItem: { icon: 'heart', title: '', text: '' }, fields: [{ key: 'title', label: 'כותרת' }, { key: 'text', t: 'area', label: 'הסבר' }, { key: 'icon', t: 'icon', label: 'אייקון' }] })]),
      group(T, 'איך זה עובד', false, [F({ path: p + 'steps_title', label: 'כותרת' }), objList({ path: p + 'steps', label: 'השלבים', itemLabel: 'שלב', addLabel: 'הוספת שלב', newItem: { title: '', text: '' }, fields: [{ key: 'title', label: 'כותרת' }, { key: 'text', t: 'area', label: 'הסבר' }] }), F({ path: p + 'note', t: 'area', label: 'הערה בתחתית האזור' })]),
      group(T, 'קישורים לשירותים אחרים', false, [objList({ path: p + 'related', label: 'משפט לכל שירות קשור', fixed: true, itemTitle: function (it) { var o = P.services.filter(function (x) { return x.slug === it.slug; })[0]; return 'קישור אל: ' + (o ? o.short : it.slug); }, fields: [{ key: 'text', t: 'area', label: 'המשפט' }] })]),
      group(T, 'שאלות נפוצות', false, [objList({ path: p + 'faq', label: 'השאלות', itemLabel: 'שאלה', addLabel: 'הוספת שאלה', newItem: { q: '', a: '' }, fields: [{ key: 'q', label: 'השאלה' }, { key: 'a', t: 'area', label: 'התשובה' }] })]),
      group(T, 'גוגל והודעת וואטסאפ', false, [F({ path: p + 'meta', t: 'area', label: 'תיאור קצר שמופיע בגוגל', max: 160 }), F({ path: p + 'wa_text', t: 'area', label: 'ההודעה המוכנה בכפתורי הוואטסאפ של העמוד הזה' })])
    );
    return g;
  }
  function reviewsTab() {
    var out = [h('h2', { text: 'המלצות' }), h('p', { class: 'lead', text: 'המלצות שמופיעות בעמוד של כל שירות. סמני "להציג גם בדף הבית" כדי שיופיעו גם שם.' }),
      h('div', { class: 'notice' }, h('b', { text: 'חשוב: ' }), 'הוסיפי רק המלצות אמיתיות, עם אישור מהמלצה. מומלץ לא לכתוב שם משפחה או שם מלא של ילד. המלצות שמסומנות "דוגמה" יוצגו באתר עם תגית "דוגמה", ולכן צריך להחליף אותן לפני פרסום.')];
    P.services.forEach(function (s, i) {
      out.push(group('reviews', s.short, true, [objList({ path: 'services.' + i + '.testimonials', label: 'המלצות', itemLabel: 'המלצה', addLabel: 'הוספת המלצה', newItem: { text: '', who: '', sample: false, home: false },
        fields: [{ key: 'text', t: 'area', label: 'נוסח ההמלצה', rows: 3 }, { key: 'who', label: 'מי כתבה', hint: 'למשל: אמא של נועם, 9 חודשים' }, { key: 'sample', t: 'check', label: 'זו המלצה לדוגמה (תסומן באתר כדוגמה)' }, { key: 'home', t: 'check', label: 'להציג גם בדף הבית' }] })]));
    });
    return out;
  }

  /* ---------- תמונות ---------- */
  var HEIC_RE = /\.(heic|heif)$/i;
  function sniffHeic(file) {
    if (HEIC_RE.test(file.name) || /image\/hei[cf]/i.test(file.type)) return Promise.resolve(true);
    return file.slice(0, 16).arrayBuffer().then(function (buf) {
      var t = String.fromCharCode.apply(null, new Uint8Array(buf).slice(4, 12));
      return /ftyp(heic|heix|hevc|hevx|mif1|msf1)/.test(t);
    }).catch(function () { return false; });
  }
  function decodeImage(file) {
    function viaImg(src, revoke) { return new Promise(function (res, rej) { var im = new Image(); im.onload = function () { if (revoke) URL.revokeObjectURL(src); res(im); }; im.onerror = function () { if (revoke) URL.revokeObjectURL(src); rej(new Error('decode')); }; im.src = src; }); }
    function viaReader() { return new Promise(function (res, rej) { var r = new FileReader(); r.onload = function () { viaImg(r.result, false).then(res, rej); }; r.onerror = function () { rej(new Error('read')); }; r.readAsDataURL(file); }); }
    var steps = [];
    if (window.createImageBitmap) steps.push(function () { return createImageBitmap(file); });
    steps.push(function () { return viaImg(URL.createObjectURL(file), true); });
    steps.push(viaReader);
    return steps.reduce(function (p, step) { return p.catch(function () { return step(); }); }, Promise.reject(new Error('start')));
  }
  function imgError(code, file) { var e = new Error(code); e.code = code; e.file = file; return e; }
  function processImage(file, maxDim) {
    if (!file.size) return Promise.reject(imgError('empty', file));
    if (file.size > 30 * 1024 * 1024) return Promise.reject(imgError('toobig', file));
    return decodeImage(file).then(function (b) {
      var w = b.width || b.naturalWidth, hh = b.height || b.naturalHeight;
      if (!w || !hh) throw imgError('decode', file);
      var s = Math.min(1, maxDim / Math.max(w, hh));
      var cw = Math.max(1, Math.round(w * s)), ch = Math.max(1, Math.round(hh * s));
      var c = document.createElement('canvas'); c.width = cw; c.height = ch;
      var x = c.getContext('2d'); x.fillStyle = '#fff'; x.fillRect(0, 0, cw, ch); x.drawImage(b, 0, 0, cw, ch);
      var data = c.toDataURL('image/jpeg', 0.86);
      if (!data || data.length < 100) throw imgError('canvas', file);
      return { data: data, w: cw, h: ch };
    }, function () {
      return sniffHeic(file).then(function (heic) { throw imgError(heic ? 'heic' : 'decode', file); });
    });
  }
  var IMG_MSG = {
    heic: 'זו תמונה בפורמט HEIC (הפורמט של אייפון), והדפדפן הזה לא יודע לקרוא אותה. הפתרון הקל: לשלוח את התמונה לעצמך בוואטסאפ ולהוריד אותה משם, כי וואטסאפ בדרך כלל ממיר ל-JPG. אפשר גם לפתוח אותה במחשב ולשמור כ-JPG, או באייפון: הגדרות, מצלמה, פורמטים, ולבחור "תאימות מרבית".',
    decode: 'הדפדפן לא הצליח לקרוא את הקובץ הזה. הסוגים שנתמכים הם JPG, PNG, WebP ו-GIF. אם זה צילום מסך או תמונה שנשמרה בתוכנה אחרת, אפשר לשמור אותה מחדש כ-JPG.',
    empty: 'הקובץ ריק (0 בייטים), כנראה שההעתקה שלו לא הסתיימה. נסי לבחור אותו שוב.',
    toobig: 'הקובץ גדול מדי (מעל 30MB). הקטיני אותו או בחרי תמונה אחרת.',
    canvas: 'הדפדפן לא הצליח להקטין את התמונה, כנראה כי היא גדולה מאוד. נסי תמונה קטנה יותר.'
  };
  function makeOg(dataUrl) {
    return new Promise(function (res) {
      var im = new Image();
      im.onload = function () {
        var c = document.createElement('canvas'); c.width = 1200; c.height = 630; var x = c.getContext('2d');
        x.fillStyle = '#f6ecdf'; x.fillRect(0, 0, 1200, 630);
        var s = Math.max(1200 / im.width, 630 / im.height), w = im.width * s, hh = im.height * s;
        x.drawImage(im, (1200 - w) / 2, (630 - hh) * 0.3, w, hh);
        res({ data: c.toDataURL('image/jpeg', 0.85), w: 1200, h: 630, alt: '' });
      };
      im.onerror = function () { res(null); }; im.src = dataUrl;
    });
  }
  function kb(dataUrl) { return Math.round((dataUrl.length * 0.75) / 1024); }
  var slotMsg = {}, slotBusy = {};
  function setSlotImage(slot, file) {
    if (!file) return;
    slotMsg[slot.key] = null; slotBusy[slot.key] = true; renderContent();
    processImage(file, slot.key === 'hero' ? 1400 : 1200).then(function (r) {
      var cur = P.images[slot.key]; pushUndo();
      P.images[slot.key] = { data: r.data, w: r.w, h: r.h, alt: (cur && cur.alt) || '', focus: (cur && cur.focus) || 'center' };
      slotBusy[slot.key] = false; changed(); renderContent(); toast('התמונה נוספה');
    }).catch(function (e) {
      slotBusy[slot.key] = false; slotMsg[slot.key] = IMG_MSG[e.code] || IMG_MSG.decode; renderContent();
      toast('לא הצלחתי להוסיף את התמונה. ההסבר מופיע מתחתיה.');
    });
  }
  function imageSlot(slot) {
    var cur = P.images[slot.key], busy = slotBusy[slot.key], msg = slotMsg[slot.key];
    var fileIn = h('input', { type: 'file', accept: 'image/*,.heic,.heif,.jpg,.jpeg,.png,.webp,.gif', id: 'file-' + slot.key, disabled: busy ? true : null });
    fileIn.addEventListener('change', function () { var f = fileIn.files && fileIn.files[0]; if (f) setSlotImage(slot, f); });
    var thumb = h('div', { class: 'thumb', 'data-drop': slot.key, tabindex: '-1' }, busy ? 'מעבדת את התמונה…' : (cur && cur.data ? h('img', { src: cur.data, alt: '' }) : 'גרירה לכאן'));
    ['dragenter', 'dragover'].forEach(function (ev) { thumb.addEventListener(ev, function (e) { e.preventDefault(); thumb.classList.add('over'); }); });
    thumb.addEventListener('dragleave', function () { thumb.classList.remove('over'); });
    thumb.addEventListener('drop', function (e) { e.preventDefault(); thumb.classList.remove('over'); var f = e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files[0]; if (f) setSlotImage(slot, f); });
    /* תווית עם שדה קובץ אמיתי מעליה: לחיצה עליה פותחת את בחירת הקבצים ישירות, בלי JavaScript באמצע */
    var pick = h('label', { class: 'btn small filebtn' + (busy ? ' disabled' : '') }, cur && cur.data ? 'החלפת תמונה' : 'בחירת תמונה מהמחשב', fileIn);
    var acts = h('div', { class: 'acts' }, pick,
      cur && cur.data ? h('button', { type: 'button', class: 'btn small ghost danger', onclick: function () { pushUndo(); delete P.images[slot.key]; slotMsg[slot.key] = null; changed(); renderContent(); } }, 'הסרה') : null);
    var extra = [];
    if (cur && cur.data) {
      extra.push(renderOne({ label: 'תיאור קצר של התמונה', hint: 'למי שלא רואה את התמונה, ולגוגל. למשל: "נועה מחזיקה תינוק".' }, function () { return cur.alt; }, function (v) { cur.alt = v; }));
      var fid = uid(), sel = h('select', { id: fid }, h('option', { value: 'top', text: 'החלק העליון (מומלץ לפנים)' }), h('option', { value: 'center', text: 'האמצע' }), h('option', { value: 'bottom', text: 'החלק התחתון' }));
      sel.value = cur.focus || 'center'; sel.addEventListener('change', function () { cur.focus = sel.value; changed(); });
      extra.push(h('div', { class: 'field' }, h('label', { for: fid, text: 'איזה חלק יישאר כשהתמונה נחתכת' }), sel));
    }
    return h('div', { class: 'img-slot', 'data-slot': slot.key }, thumb, h('div', {}, h('h3', { text: slot.label }), slot.hint ? h('div', { class: 'hint', style: 'font-size:.88rem;color:var(--muted)', text: slot.hint }) : null,
      cur && cur.data ? h('div', { class: 'hint', style: 'font-size:.85rem;color:var(--muted)', text: 'גודל התמונה: ⁦' + cur.w + '×' + cur.h + '⁩ פיקסלים, ' + kb(cur.data) + ' קילובייט' }) : null,
      msg ? h('div', { class: 'img-err', role: 'alert', text: msg }) : null, acts, h('div', { style: 'display:grid;gap:10px;margin-top:12px' }, extra)));
  }
  function slotByKey(key) { return Gen.IMAGE_SLOTS.filter(function (x) { return x.key === key; })[0]; }
  function imagesTab() {
    var out = [h('h2', { text: 'תמונות' }), h('p', { class: 'lead', text: 'בחרי תמונה מהמחשב, או גררי אותה לתוך המסגרת. היא תוקטן אוטומטית כדי שהאתר ייטען מהר, ותיחתך לצורת הקשת באתר. אפשר גם ללחוץ על מסגרת התמונה בתצוגה משמאל.' }),
      h('details', { class: 'group', style: 'margin-bottom:14px' }, h('summary', { text: 'לא מצליחה להוסיף תמונה?' }), h('div', { class: 'body' },
        h('ol', { style: 'margin:0 18px;display:grid;gap:6px' },
          h('li', { text: 'ודאי שפתחת את קובץ העורך ישירות מהמחשב, בדפדפן Chrome או Edge, ולא מתוך אפליקציה אחרת או תצוגה מקדימה.' }),
          h('li', { text: 'תמונות מאייפון בפורמט HEIC לא נפתחות בכל דפדפן. שלחי אותן לעצמך בוואטסאפ והורידי, או שמרי כ-JPG.' }),
          h('li', { text: 'אפשר לגרור את הקובץ ישירות לתוך המסגרת הריבועית.' }),
          h('li', { text: 'אם משהו עדיין לא עובד, צלמי את המסך עם ההודעה האדומה שמופיעה מתחת לתמונה.' }))))];
    Gen.IMAGE_SLOTS.forEach(function (slot) { out.push(imageSlot(slot)); });
    return out;
  }
  function photoGroup(tab, keys, title) {
    return group(tab, title, true, [h('p', { class: 'hint', style: 'margin:0', text: 'בתצוגה משמאל התמונה מופיעה בתוך מסגרת. אפשר ללחוץ עליה שם, או לבחור כאן.' })].concat(keys.map(function (k) { return imageSlot(slotByKey(k)); })));
  }

  /* ---------- פרסום ---------- */
  var TAB_OF = { contact: 'contact', images: 'images', reviews: 'reviews' };
  function publishTab() {
    var issues = Gen.findIssues(P), must = issues.filter(function (i) { return i.level === 'must'; }), should = issues.filter(function (i) { return i.level === 'should'; });
    var out = [h('h2', { text: 'הכנה לפרסום' }), h('p', { class: 'lead', text: 'שלושה שלבים: לבדוק שלא נשאר דבר זמני, להוריד את האתר, ולהעלות אותו.' }),
      h('h3', { text: '1. בדיקה אחרונה', style: 'margin:6px 0' })];
    if (!issues.length) out.push(h('div', { class: 'allgood', text: 'הכול מלא. אפשר לפרסם 🎉' }));
    else {
      out.push(h('p', { class: 'lead', style: 'margin-bottom:0', text: must.length ? 'יש דברים שחשוב להחליף לפני שהאתר עולה:' : 'הכול חיוני מולא. נשארו רק המלצות לשיפור:' }));
      out.push(h('ul', { class: 'issues' }, must.concat(should).map(function (i) {
        return h('li', { class: i.level }, h('span', { text: i.text }), h('button', { type: 'button', class: 'btn small ghost', onclick: function () { gotoTab(TAB_OF[i.tab] || 'contact'); } }, 'לתיקון'));
      })));
    }
    out.push(h('h3', { text: '2. הורדת האתר', style: 'margin:6px 0' }),
      h('p', { class: 'lead', text: 'הקובץ שיורד הוא כל האתר, ארוז בקובץ ZIP אחד.' }),
      h('div', { style: 'margin-bottom:20px' }, h('button', { type: 'button', class: 'btn primary', id: 'btnDownload', onclick: downloadSite }, 'הורדת האתר (ZIP)')),
      h('h3', { text: '3. העלאה לאינטרנט', style: 'margin:6px 0' }),
      h('ol', { class: 'steps' },
        h('li', {}, h('div', {}, 'פתחי את הקובץ שירד (קליק ימני ובחירה ב"חילוץ" או "Extract"). תקבלי תיקייה עם כל הקבצים.')),
        h('li', {}, h('div', {}, 'העלי את כל מה שבתוך התיקייה למקום שבו האתר מתארח:', h('ul', {},
          h('li', { style: 'display:block' }, 'ב-Netlify: נכנסים ל-app.netlify.com/drop וגוררים את התיקייה.'),
          h('li', { style: 'display:block' }, 'ב-Cloudflare Pages: Upload assets וגוררים את התיקייה.'),
          h('li', { style: 'display:block' }, 'בחברת אחסון רגילה: מנהל הקבצים של החברה, לתיקייה public_html, ומחליפים את הקבצים הקיימים.')))),
        h('li', {}, h('div', {}, 'פתחי את האתר החי בטלפון ובדקי שהכול נראה כמו בתצוגה המקדימה. כל עדכון עתידי הוא אותם שלושה שלבים.'))),
      h('div', { class: 'notice', style: 'margin-top:18px' }, h('b', { text: 'גיבוי: ' }), 'העבודה נשמרת אוטומטית בדפדפן הזה ובמחשב הזה בלבד. אם תנקי נתוני דפדפן או תעברי מחשב, היא תיעלם. לכן כדאי ללחוץ מדי פעם על "שמירת גיבוי" ולשמור את הקובץ במקום בטוח.'),
      h('div', { style: 'display:flex;gap:10px;flex-wrap:wrap' }, h('button', { type: 'button', class: 'btn', onclick: backup }, 'שמירת גיבוי עכשיו')));
    return out;
  }

  /* ---------- ZIP ---------- */
  var CRC = (function () { var t = new Uint32Array(256); for (var n = 0; n < 256; n++) { var c = n; for (var k = 0; k < 8; k++) c = c & 1 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; } return t; })();
  function crc32(b) { var c = 0xFFFFFFFF; for (var i = 0; i < b.length; i++) c = CRC[(c ^ b[i]) & 255] ^ (c >>> 8); return (c ^ 0xFFFFFFFF) >>> 0; }
  function b64bytes(s) { var bin = atob(s), u = new Uint8Array(bin.length); for (var i = 0; i < bin.length; i++) u[i] = bin.charCodeAt(i); return u; }
  function buildZip(files) {
    var enc = new TextEncoder(), parts = [], central = [], offset = 0, d = new Date();
    var dt = ((d.getHours() << 11) | (d.getMinutes() << 5) | (d.getSeconds() >> 1)), dd = (((d.getFullYear() - 1980) << 9) | ((d.getMonth() + 1) << 5) | d.getDate());
    function u16(v) { return [v & 255, (v >>> 8) & 255]; } function u32(v) { return [v & 255, (v >>> 8) & 255, (v >>> 16) & 255, (v >>> 24) & 255]; }
    files.forEach(function (f) {
      var name = enc.encode(f.path), data = f.base64 ? b64bytes(f.base64) : enc.encode(f.text), crc = crc32(data);
      var head = new Uint8Array([].concat(u32(0x04034b50), u16(20), u16(0x0800), u16(0), u16(dt), u16(dd), u32(crc), u32(data.length), u32(data.length), u16(name.length), u16(0)));
      parts.push(head, name, data);
      central.push(new Uint8Array([].concat(u32(0x02014b50), u16(20), u16(20), u16(0x0800), u16(0), u16(dt), u16(dd), u32(crc), u32(data.length), u32(data.length), u16(name.length), u16(0), u16(0), u16(0), u16(0), u32(0), u32(offset))), name);
      offset += head.length + name.length + data.length;
    });
    var csize = central.reduce(function (a, b) { return a + b.length; }, 0);
    var end = new Uint8Array([].concat(u32(0x06054b50), u16(0), u16(0), u16(files.length), u16(files.length), u32(csize), u32(offset), u16(0)));
    return new Blob(parts.concat(central, [end]), { type: 'application/zip' });
  }
  function downloadSite() {
    var issues = Gen.findIssues(P), must = issues.filter(function (i) { return i.level === 'must'; });
    if (must.length && !window.confirm('יש עדיין ' + must.length + ' דברים חשובים שלא הוחלפו:\n\n' + must.map(function (i) { return '• ' + i.text; }).join('\n') + '\n\nלהוריד את האתר בכל זאת?')) return;
    var btn = $('btnDownload'); if (btn) btn.disabled = true;
    var needOg = Gen.fixUrl(P.biz.siteUrl) && P.images.hero && P.images.hero.data;
    Promise.resolve(needOg ? makeOg(P.images.hero.data) : null).then(function (og) {
      var proj = clone(P); if (og) proj.images.og = og;
      var res = Gen.generate(proj, ASSETS, {});
      var blob = buildZip(res.files);
      download(blob, 'site-' + dateStamp(new Date()) + '.zip');
      toast('האתר ירד כקובץ ZIP (' + res.files.length + ' קבצים)');
    }).catch(function (e) { toast('הייתה בעיה ביצירת הקובץ: ' + e.message); }).then(function () { if (btn) btn.disabled = false; });
  }

  /* ---------- גיבוי ושחזור ---------- */
  function backup() {
    var blob = new Blob([JSON.stringify({ app: 'site-editor', version: 1, savedAt: new Date().toISOString(), project: P })], { type: 'application/json' });
    download(blob, 'site-backup-' + dateStamp(new Date()) + '.json');
    DB.set('lastBackup', Date.now()).catch(function () { });
    toast('קובץ הגיבוי ירד. שמרי אותו במקום בטוח.');
  }
  function restore(file) {
    var r = new FileReader();
    r.onload = function () {
      try {
        var o = JSON.parse(r.result);
        if (!o || o.app !== 'site-editor' || !o.project || !Array.isArray(o.project.services)) throw new Error('bad');
        if (!window.confirm('פתיחת הגיבוי תחליף את מה שיש עכשיו בעורך. אפשר יהיה לבטל בכפתור "ביטול פעולה אחרונה". להמשיך?')) return;
        pushUndo(); P = mergeProject(DEFAULTS, o.project); renderAll(); changed(); toast('הגיבוי נפתח');
      } catch (e) { toast('זה לא קובץ גיבוי של העורך'); }
    };
    r.readAsText(file);
  }

  /* ---------- תצוגה מקדימה ---------- */
  function renderPreview() {
    try {
      var res = Gen.generate(P, ASSETS, { preview: true });
      var f = res.files.filter(function (x) { return x.path === pvPage; })[0] || res.files[0];
      $('frame').srcdoc = f.text;
    } catch (e) { setStatus('שגיאה בתצוגה: ' + e.message, true); }
  }
  function schedulePreview() { clearTimeout(previewT); previewT = setTimeout(renderPreview, 350); }
  function renderPvPages() {
    var box = $('pvPages'); box.textContent = '';
    [{ file: 'index.html', label: 'דף הבית' }].concat(P.services.map(function (s) { return { file: s.file, label: s.short }; })).forEach(function (p) {
      box.append(h('button', { type: 'button', 'aria-pressed': p.file === pvPage ? 'true' : 'false', onclick: function () { pvPage = p.file; renderPvPages(); renderPreview(); } }, p.label));
    });
  }
  window.addEventListener('message', function (e) {
    var fr = $('frame'); if (e.source !== fr.contentWindow || !e.data) return;
    if (e.data.siteScroll != null) pvScroll[pvPage] = e.data.siteScroll;
    else if (e.data.siteReady) fr.contentWindow.postMessage({ restoreScroll: pvScroll[pvPage] || 0 }, '*');
    else if (typeof e.data.sitePhoto === 'string') focusPhoto(e.data.sitePhoto);
    else if (typeof e.data.siteNav === 'string') {
      var f = e.data.siteNav.split('#')[0]; var known = P.services.map(function (s) { return s.file; }).concat(['index.html']);
      if (known.indexOf(f) >= 0) { pvPage = f; pvScroll[f] = 0; renderPvPages(); renderPreview(); }
    }
  });

  function focusPhoto(key) {
    var tab = key === 'hero' || key === 'about' ? 'home' : 'svc' + P.services.map(function (x) { return x.slug; }).indexOf(key);
    if (tab !== tabId) gotoTab(tab);
    setTimeout(function () {
      var slot = document.querySelector('.img-slot[data-slot="' + key + '"]');
      if (!slot) return;
      var d = slot.closest('details'); if (d) d.open = true;
      slot.scrollIntoView({ block: 'center', behavior: 'smooth' }); slot.classList.remove('flash'); void slot.offsetWidth; slot.classList.add('flash');
      var inp = $('file-' + key); if (inp && !inp.disabled) { try { inp.click(); } catch (e) { } }
      toast('בחרי תמונה מהמחשב. אם החלון לא נפתח, לחצי על "בחירת תמונה".');
    }, 120);
  }

  /* ---------- ציור ---------- */
  function gotoTab(id) { tabId = id; var pg = pageForTab(id); if (pg) { pvPage = pg; renderPvPages(); renderPreview(); } renderTabs(); renderContent(); $('panel').scrollTop = 0; }
  function updateTabDots() {
    var issues = Gen.findIssues(P).filter(function (i) { return i.level === 'must'; }), bad = {};
    issues.forEach(function (i) { bad[TAB_OF[i.tab] || 'contact'] = true; });
    document.querySelectorAll('.tab').forEach(function (t) { var d = t.querySelector('.dot'); if (d) d.hidden = !bad[t.dataset.id]; });
  }
  function renderTabs() {
    var box = $('tabs'); box.textContent = '';
    tabsList().forEach(function (t) {
      box.append(h('button', { type: 'button', class: 'tab', role: 'tab', 'data-id': t.id, 'aria-selected': t.id === tabId ? 'true' : 'false', onclick: function () { gotoTab(t.id); } }, t.label, h('span', { class: 'dot', hidden: true, title: 'יש כאן משהו להחליף' })));
    });
    updateTabDots();
  }
  function renderContent() {
    var panel = $('panel'), top = panel.scrollTop, c = $('content'); c.textContent = '';
    var kids;
    if (tabId === 'contact') kids = contactTab(); else if (tabId === 'home') kids = homeTab(); else if (tabId === 'images') kids = imagesTab();
    else if (tabId === 'reviews') kids = reviewsTab(); else if (tabId === 'publish') kids = publishTab(); else kids = serviceTab(+tabId.slice(3));
    kids.forEach(function (k) { c.append(k); });
    requestAnimationFrame(function () { c.querySelectorAll('textarea').forEach(grow); panel.scrollTop = top; });
  }
  function renderAll() { renderTabs(); renderPvPages(); renderContent(); renderPreview(); }

  function dialog(html, acts) {
    var d = $('dlg'); d.textContent = ''; d.append.apply(d, html);
    d.append(h('div', { class: 'acts' }, acts.map(function (a) { return h('button', { type: 'button', class: 'btn' + (a.primary ? ' primary' : ''), onclick: function () { d.close(); if (a.fn) a.fn(); } }, a.label); })));
    d.showModal();
  }
  function welcome() {
    dialog([h('h2', { text: 'ברוכה הבאה לעורך האתר' }),
      h('p', { text: 'כאן אפשר לשנות כל טקסט, תמונה והמלצה באתר, ולראות את התוצאה מיד בצד.' }),
      h('ul', { style: 'margin:10px 18px 0;display:grid;gap:6px' },
        h('li', { text: 'הכול נשמר אוטומטית בדפדפן הזה.' }),
        h('li', { text: 'בסוף נכנסים ל"פרסום", מורידים קובץ אחד ומעלים אותו לאתר.' }),
        h('li', { text: 'מדי פעם כדאי ללחוץ על "שמירת גיבוי" ולשמור את הקובץ.' }),
        h('li', { text: 'טעית? "ביטול פעולה אחרונה" למעלה מחזיר מחיקות ושינויי תמונות.' }))],
      [{ label: 'מתחילות', primary: true, fn: function () { DB.set('seenWelcome', 1).catch(function () { }); } }]);
  }

  /* ---------- אתחול ---------- */
  function embeddedWarning() {
    var emb = false;
    try { emb = window.self !== window.top; } catch (e) { emb = true; }
    if (!/^(file|http|https):$/.test(location.protocol)) emb = true;
    if (!emb) return;
    var b = h('div', { class: 'notice', role: 'alert', style: 'margin:0;border-radius:0;display:flex;gap:12px;align-items:center;justify-content:space-between' },
      h('span', {}, h('b', { text: 'שימי לב: ' }), 'נראה שהעורך פתוח בתוך אפליקציה או חלון מוגבל. בחלון כזה בחירת קבצים, הורדה, גיבוי ושמירה אוטומטית עלולים לא לעבוד. שמרי את הקובץ site-editor.html במחשב ופתחי אותו בלחיצה כפולה ב-Chrome או ב-Edge.'),
      h('button', { type: 'button', class: 'btn small', onclick: function () { b.remove(); } }, 'הבנתי'));
    document.querySelector('.app').insertBefore(b, document.querySelector('.top'));
  }
  function init() {
    embeddedWarning();
    $('btnUndo').addEventListener('click', function () {
      if (!undoStack.length) return; P = JSON.parse(undoStack.pop()); $('btnUndo').disabled = !undoStack.length; renderAll(); scheduleSave(); toast('הפעולה האחרונה בוטלה');
    });
    $('btnBackup').addEventListener('click', backup);
    $('btnRestore').addEventListener('click', function () { $('fileRestore').click(); });
    $('fileRestore').addEventListener('change', function (e) { var f = e.target.files && e.target.files[0]; if (f) restore(f); e.target.value = ''; });
    $('btnPublish').addEventListener('click', function () { gotoTab('publish'); });
    function pv(m) { pvMode = m; $('frameWrap').classList.toggle('mobile', m === 'mobile'); $('pvDesktop').setAttribute('aria-pressed', m === 'desktop'); $('pvMobile').setAttribute('aria-pressed', m === 'mobile'); }
    $('pvDesktop').addEventListener('click', function () { pv('desktop'); }); $('pvMobile').addEventListener('click', function () { pv('mobile'); });
    $('modeSwitch').addEventListener('click', function (e) { var m = e.target.dataset && e.target.dataset.m; if (!m) return; $('main').dataset.mode = m; Array.prototype.forEach.call($('modeSwitch').children, function (b) { b.setAttribute('aria-pressed', b.dataset.m === m); }); });
    window.addEventListener('beforeunload', function (e) { if (saveErr) { e.preventDefault(); e.returnValue = ''; } });
    DB.get('project').then(function (saved) {
      if (saved && saved.services) { P = mergeProject(DEFAULTS, saved); setStatus('העבודה האחרונה שלך נטענה ✓'); } else setStatus('מוכן');
      return DB.get('seenWelcome');
    }).catch(function () { saveErr = true; setStatus('השמירה האוטומטית לא זמינה בדפדפן הזה. שמרי קובץ גיבוי.', true); return 1; })
      .then(function (seen) { renderAll(); if (!seen) welcome(); });
  }
  init();
})();
