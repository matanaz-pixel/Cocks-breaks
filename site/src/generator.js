/* מחולל האתר. רץ גם בדפדפן (בתוך העורך) וגם ב-Node (לבדיקות ולבנייה משורת פקודה).
 * generate(project, assets, opts) -> { files: [{path, text} | {path, base64}], issues }
 *   project: { biz, home, services, images }
 *   assets:  { css, fonts, sprite }
 *   opts:    { preview: boolean }   תצוגה מקדימה: תמונות מוטמעות, קישורים בין עמודים נתפסים על ידי העורך
 */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.SiteGen = factory();
})(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  var ICON_NAMES = ['heart', 'sparkle', 'moon', 'people', 'clock', 'leaf', 'chat', 'book', 'phone', 'sprout', 'blocks', 'baby', 'wallet', 'shield', 'sun', 'star', 'check'];
  var PLACEHOLDERS = { name: 'שם היועצת', phone: '050-000-0000', emailDomain: 'example.com', instagram: 'https://instagram.com/', facebook: 'https://facebook.com/' };
  var IMAGE_SLOTS = [
    { key: 'hero', label: 'תמונה ראשית בדף הבית', hint: 'תמונה שלך או של הילדים, בצורת קשת. עדיף תמונה אנכית.' },
    { key: 'about', label: 'תמונה אישית (נעים להכיר)', hint: 'תמונה שלך, עדיף פנים ברורות ורקע פשוט.' },
    { key: 'newborn', label: 'תמונה לעמוד הדרכת ניובורן', hint: '' },
    { key: 'sleep', label: 'תמונה לעמוד ייעוץ שינה', hint: '' },
    { key: 'toddlers', label: 'תמונה לעמוד הדרכת פעוטות', hint: '' },
    { key: 'development', label: 'תמונה לעמוד ליווי התפתחותי', hint: '' }
  ];

  function esc(s) {
    return String(s == null ? '' : s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }
  function ml(s) { return esc(s).replace(/ *\r?\n */g, ' <br>'); }
  function arr(a) { return Array.isArray(a) ? a : []; }

  /* 050-123-4567 / +972 50 123 4567 -> 972501234567 */
  function normalizePhone(p) {
    var d = String(p || '').replace(/[^\d+]/g, '');
    if (d.charAt(0) === '+') d = d.slice(1);
    d = d.replace(/\D/g, '');
    if (d.indexOf('972') === 0) return d;
    if (d.charAt(0) === '0') return '972' + d.slice(1);
    return d;
  }
  function fixUrl(u) {
    u = String(u || '').trim();
    if (!u) return '';
    if (/^https?:\/\//i.test(u)) return u;
    return 'https://' + u.replace(/^\/+/, '');
  }
  function siteBase(u) {
    u = fixUrl(u);
    return u ? u.replace(/\/+$/, '') : '';
  }

  function icon(n) { return '<svg class="ic" aria-hidden="true"><use href="#i-' + esc(n) + '"/></svg>'; }

  /* ---------- בדיקת פריטים שעדיין לא הוחלפו ---------- */
  function findIssues(p) {
    var out = [], b = p.biz || {}, imgs = p.images || {};
    function add(level, text, tab) { out.push({ level: level, text: text, tab: tab }); }
    if (!b.name || b.name === PLACEHOLDERS.name) add('must', 'השם עדיין "' + PLACEHOLDERS.name + '"', 'contact');
    var ph = normalizePhone(b.phone);
    if (!b.phone || b.phone === PLACEHOLDERS.phone || ph.length < 11 || ph.length > 12) add('must', 'מספר הטלפון והוואטסאפ עדיין לא תקין או לא הוחלף', 'contact');
    if (!b.email || /example\.com/i.test(b.email) || !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(b.email)) add('must', 'כתובת האימייל עדיין לא הוחלפה או אינה תקינה', 'contact');
    if (!b.instagram || fixUrl(b.instagram) === PLACEHOLDERS.instagram) add('should', 'קישור האינסטגרם עדיין כללי', 'contact');
    if (!b.facebook || fixUrl(b.facebook) === PLACEHOLDERS.facebook) add('should', 'קישור הפייסבוק עדיין כללי', 'contact');
    var samples = 0;
    arr(p.services).forEach(function (s) { arr(s.testimonials).forEach(function (t) { if (t.sample) samples++; }); });
    if (samples) add('must', samples + ' המלצות עדיין מסומנות כ"דוגמה". יש להחליף אותן בהמלצות אמיתיות או למחוק', 'reviews');
    IMAGE_SLOTS.forEach(function (s) { if (!imgs[s.key] || !imgs[s.key].data) add('should', 'חסרה ' + s.label, 'images'); });
    Object.keys(imgs).forEach(function (k) { var i = imgs[k]; if (i && i.data && !String(i.alt || '').trim() && k !== 'og') add('should', 'חסר תיאור קצר לתמונה "' + k + '" (נחוץ לנגישות)', 'images'); });
    if (!b.siteUrl) add('should', 'עדיין לא הוזנה כתובת האתר. בלעדיה לא ייווצרו מפת אתר ותמונת שיתוף', 'contact');
    return out;
  }

  /* ---------- יצירת האתר ---------- */
  function generate(project, assets, opts) {
    opts = opts || {};
    var B = project.biz || {}, H = project.home || {}, SV = arr(project.services), IM = project.images || {};
    var phoneIntl = normalizePhone(B.phone);
    var base = siteBase(B.siteUrl);
    var preview = !!opts.preview;
    var SVM = {};
    SV.forEach(function (s) { SVM[s.slug] = s; });

    function wa(text) { return 'https://wa.me/' + phoneIntl + '?text=' + encodeURIComponent(text || ''); }

    /* תמונות */
    function hasImg(key) { return !!(IM[key] && IM[key].data); }
    function imgFile(key) { return 'images/' + key + '.jpg'; }
    function imgTag(key) {
      if (!hasImg(key)) return '';
      var i = IM[key], pos = { top: '50% 20%', center: '50% 50%', bottom: '50% 80%' }[i.focus] || '50% 50%';
      return '<img class="ph" src="' + (preview ? i.data : imgFile(key)) + '" alt="' + esc(i.alt || '') + '"' +
        (i.w && i.h ? ' width="' + (i.w | 0) + '" height="' + (i.h | 0) + '"' : '') + ' style="object-position:' + pos + '"' +
        (key === 'hero' ? '' : ' loading="lazy"') + ' decoding="async">';
    }

    /* מבנה משותף */
    function nav(h, hasReviews) {
      var n = [['שירותים', h + '#services'], ['הגישה ההוליסטית', h + '#holistic'], ['עליי', h + '#about']];
      if (hasReviews) n.push(['המלצות', '#reviews']);
      n.push(['מימון', '#funding'], ['שאלות', '#faq']);
      return n;
    }
    function pageShell(o) {
      var navHtml = o.nav.map(function (x) { return '<li><a href="' + esc(x[1]) + '">' + esc(x[0]) + '</a></li>'; }).join('\n    ');
      var bodyStyle = o.S ? ' style="--accent:' + esc(o.S.accent) + ';--pastel:' + esc(o.S.pastel) + '"' : '';
      var ldJson = JSON.stringify(o.ld).replace(/</g, '\\u003c').replace(/>/g, '\\u003e').replace(/&/g, '\\u0026');
      var canon = o.canonical ? '<link rel="canonical" href="' + esc(o.canonical) + '">\n<meta property="og:url" content="' + esc(o.canonical) + '">\n' : '';
      var og = (base && hasImg('og')) ? '<meta property="og:image" content="' + esc(base + '/images/og.jpg') + '">\n<meta name="twitter:card" content="summary_large_image">\n' : '';
      var footerSv = SV.map(function (s) { return '<li><a href="' + esc(s.file) + '">' + esc(s.short) + '</a></li>'; }).join('');
      var h = o.h;
      var previewScript = preview ? '<script>(function(){var t;addEventListener("scroll",function(){clearTimeout(t);t=setTimeout(function(){parent.postMessage({siteScroll:scrollY},"*")},80)},{passive:true});' +
        'addEventListener("message",function(e){if(e.data&&e.data.restoreScroll!=null){var d=document.documentElement;d.style.scrollBehavior="auto";scrollTo(0,e.data.restoreScroll);d.style.scrollBehavior=""}});' +
        'document.addEventListener("click",function(e){var ph=e.target.closest&&e.target.closest("[data-photo]");if(ph){e.preventDefault();parent.postMessage({sitePhoto:ph.getAttribute("data-photo")},"*");return}var a=e.target.closest&&e.target.closest("a");if(!a)return;var h=a.getAttribute("href")||"";if(/\\.html(#|$)/.test(h)){e.preventDefault();parent.postMessage({siteNav:h},"*")}});' +
        'parent.postMessage({siteReady:true},"*")})()</script>' : '';
      var previewCss = preview ? '[data-photo]{cursor:pointer}[data-photo]:hover{outline:4px dashed #a85a39;outline-offset:-6px}' : '';
      return '<!doctype html>\n<html lang="he" dir="rtl">\n<head>\n<meta charset="utf-8">\n<meta name="viewport" content="width=device-width, initial-scale=1">\n' +
        '<title>' + esc(o.title) + '</title>\n<meta name="description" content="' + esc(o.desc) + '">\n<meta name="theme-color" content="#f6ecdf">\n' +
        '<meta property="og:title" content="' + esc(o.title) + '">\n<meta property="og:description" content="' + esc(o.desc) + '">\n<meta property="og:type" content="website">\n<meta property="og:locale" content="he_IL">\n' + canon + og +
        '<link rel="icon" type="image/svg+xml" href="data:image/svg+xml,%3Csvg xmlns=\'http://www.w3.org/2000/svg\' viewBox=\'0 0 64 64\'%3E%3Crect width=\'64\' height=\'64\' rx=\'32\' fill=\'%23a85a39\'/%3E%3Cpath d=\'M44 36.5A14 14 0 1 1 28.6 19.8a11 11 0 0 0 15.4 16.7z\' fill=\'%23fffaf3\'/%3E%3C/svg%3E">\n' +
        '<script type="application/ld+json">' + ldJson + '</script>\n<script>document.documentElement.classList.add(\'js\')</script>\n<style>\n' + assets.fonts + '\n' + assets.css + '\n' + previewCss + '\n</style>\n</head>\n<body' + bodyStyle + '>\n' +
        '<a class="skip" href="#main">דלגו לתוכן</a>\n' + assets.sprite + '\n<header>\n <div class="wrap nav">\n  <a class="brand" href="' + (h || '#top') + '"><i>' + icon('moon') + '</i>' + esc(B.name) + '</a>\n' +
        '  <button class="burger" id="burger" type="button" aria-label="פתיחת תפריט" aria-expanded="false" aria-controls="menu">' + icon('menu') + '</button>\n' +
        '  <nav aria-label="ראשי">\n   <ul class="menu" id="menu">\n    ' + navHtml + '\n    <li><a href="' + esc(o.pageWa) + '" target="_blank" rel="noopener">בואו נדבר</a></li>\n   </ul>\n  </nav>\n </div>\n</header>\n' +
        '<main id="main">\n' + o.main + '\n</main>\n' +
        '<a class="fab" id="fab" href="' + esc(o.pageWa) + '" target="_blank" rel="noopener" aria-label="שלחו הודעה בוואטסאפ">' + icon('whatsapp') + '</a>\n' +
        '<footer class="site"><div class="wrap">\n <div class="f-grid">\n  <div><h2>' + esc(B.name) + '</h2><p>' + esc(B.role) + '</p></div>\n  <div><h2>שירותים</h2><ul>' + footerSv + '</ul></div>\n' +
        '  <div><h2>קישורים</h2><ul><li><a href="index.html">דף הבית</a></li><li><a href="' + h + '#holistic">הגישה ההוליסטית</a></li><li><a href="' + h + '#about">עליי</a></li><li><a href="' + h + '#funding">השתתפות כספית</a></li></ul></div>\n </div>\n' +
        ' <p class="f-note">© ' + esc(B.name) + ' · ' + esc(B.disclaimer) + '</p>\n</div></footer>\n' + previewScript + '\n<script>\n' + SHARED_JS + '\n</script>\n</body>\n</html>\n';
    }

    var SHARED_JS = "(function(){\nvar b=document.getElementById('burger'),m=document.getElementById('menu');\n" +
      "function set(o){m.classList.toggle('open',o);b.setAttribute('aria-expanded',o);b.setAttribute('aria-label',o?'סגירת תפריט':'פתיחת תפריט')}\n" +
      "b.addEventListener('click',function(){set(!m.classList.contains('open'))});\n" +
      "m.addEventListener('click',function(e){if(e.target.closest('a'))set(false)});\n" +
      "document.querySelector('.brand').addEventListener('click',function(){set(false)});\n" +
      "document.addEventListener('keydown',function(e){if(e.key==='Escape'&&m.classList.contains('open')){set(false);b.focus()}});\n" +
      "if(window.matchMedia){var mq=window.matchMedia('(min-width:761px)');var fn=function(e){if(e.matches)set(false)};mq.addEventListener?mq.addEventListener('change',fn):mq.addListener(fn)}\n" +
      "var f=document.getElementById('fab'),ct=document.getElementById('contact'),inC=false;\n" +
      "function fab(){f.classList.toggle('show',window.scrollY>480&&!inC)}\n" +
      "if('IntersectionObserver' in window&&ct){new IntersectionObserver(function(es){inC=es[0].isIntersecting;fab()},{threshold:.15}).observe(ct)}\n" +
      "window.addEventListener('scroll',fab,{passive:true});fab();\n})();";

    /* רכיבים משותפים */
    function faq(items) {
      items = arr(items);
      if (!items.length) return '';
      return '<div class="faq">' + items.map(function (f) {
        return '\n <details><summary>' + esc(f.q) + icon('chev') + '</summary><p>' + esc(f.a) + '</p></details>';
      }).join('') + '\n</div>';
    }
    function review(t, badge) {
      return '<figure class="review">' + icon('quote') + '<blockquote><p>' + esc(t.text) + '</p></blockquote>\n <figcaption class="who"><span>' + esc(t.who) + '</span>' +
        (badge ? '<span class="badge" style="--c:' + esc(badge.pastel) + '">' + esc(badge.short) + '</span>' : '') +
        (t.sample ? '<span class="sample-tag">דוגמה</span>' : '') + '</figcaption></figure>';
    }
    function funding(compact, pageWa) {
      var cards = arr(H.funding_cards), steps = arr(H.funding_steps);
      var s = '<section id="funding" class="funding' + (compact ? ' compact' : '') + '"' + (compact ? ' style="padding-top:30px"' : '') + '><div class="wrap"><div class="box">\n' +
        ' <div class="head"><span class="tag">השתתפות כספית</span><h2>' + esc(H.funding_title) + '</h2><p>' + esc(H.funding_intro) + '</p></div>\n';
      if (!compact && cards.length) s += ' <div class="fcards">' + cards.map(function (c) {
        return '\n  <div class="fcard"><div class="ico">' + icon(c.icon) + '</div><div><h3>' + esc(c.title) + '</h3><p>' + esc(c.text) + '</p></div></div>';
      }).join('') + '\n </div>\n';
      if (steps.length) s += ' <ol class="fsteps">' + steps.map(function (x) { return '<li>' + esc(x) + '</li>'; }).join('') + '</ol>\n';
      s += ' <p class="fnote">' + esc(H.funding_note) + (compact ? ' <a href="index.html#funding">לפרטים נוספים</a>' : '') + '</p>\n' +
        ' <div class="actions" style="justify-content:center;margin-top:22px"><a class="btn" href="' + esc(pageWa) + '" target="_blank" rel="noopener">' + icon('whatsapp') + 'לבדוק זכאות בשיחת היכרות</a></div>\n</div></div></section>';
      return s;
    }
    function contact(title, sub, chips, pageWa) {
      var s = '<section id="contact" class="contact" style="padding-top:30px"><div class="wrap"><div class="box-dark">\n <h2>' + esc(title) + '</h2>\n <p class="lead">' + esc(sub) + '</p>\n';
      chips = arr(chips);
      if (chips.length) s += ' <p class="pick">' + esc(H.contact_pick) + '</p>\n <ul class="wa-chips">' + chips.map(function (c) {
        return '<li><a href="' + esc(wa(c.text)) + '" target="_blank" rel="noopener">' + icon('whatsapp') + esc(c.label) + '</a></li>';
      }).join('') + '</ul>\n';
      else s += ' <div class="row2"><a class="btn light" href="' + esc(pageWa) + '" target="_blank" rel="noopener">' + icon('whatsapp') + 'שלחו הודעה בוואטסאפ</a></div>\n';
      s += ' <div class="row2"><a class="btn ghost-w" href="tel:+' + phoneIntl + '">' + icon('phone') + esc(B.phone) + '</a></div>\n' +
        ' <a class="mail" href="mailto:' + esc(B.email) + '">' + icon('mail') + esc(B.email) + '</a>\n <div class="social">\n';
      if (fixUrl(B.instagram)) s += '  <a href="' + esc(fixUrl(B.instagram)) + '" target="_blank" rel="noopener" aria-label="אינסטגרם">' + icon('instagram') + '</a>\n';
      if (fixUrl(B.facebook)) s += '  <a href="' + esc(fixUrl(B.facebook)) + '" target="_blank" rel="noopener" aria-label="פייסבוק">' + icon('facebook') + '</a>\n';
      return s + ' </div>\n</div></div></section>';
    }
    function photoAttr(key) { return preview ? ' data-photo="' + esc(key) + '" title="לחצי כאן כדי להוסיף או להחליף תמונה"' : ''; }
    function photoInner(key, iconName, caption) {
      return hasImg(key) ? imgTag(key) : icon(iconName) + '<small>' + esc(preview ? 'לחצי כאן להוספת תמונה' : caption) + '</small>';
    }
    function sectionHead(tag, title, intro) {
      return '<div class="head">' + (tag ? '<span class="tag">' + esc(tag) + '</span>' : '') + '<h2>' + esc(title) + '</h2>' + (intro ? '<p>' + esc(intro) + '</p>' : '') + '</div>';
    }
    function cards4(items) {
      return '<div class="cards4">' + arr(items).map(function (p) {
        return '\n  <div class="card"><div class="ico">' + icon(p.icon) + '</div><h3>' + esc(p.title) + '</h3><p>' + esc(p.text) + '</p></div>';
      }).join('') + '\n </div>';
    }

    /* ---------- דף הבית ---------- */
    function homePage() {
      var pageWa = wa(H.wa_text);
      var homeReviews = [];
      SV.forEach(function (s) { arr(s.testimonials).forEach(function (t) { if (t.home) homeReviews.push([t, s]); }); });
      var aboutParas = arr(H.about).map(function (x) { return String(x).replace(/\{name\}/g, B.name); });
      var m = '';
      m += '<div class="hero" id="top"><div class="wrap">\n <div>\n  <span class="eyebrow">' + esc(H.hero_eyebrow) + '</span>\n  <h1>' + ml(H.hero_title) + '</h1>\n  <p class="sub">' + esc(H.hero_sub) + '</p>\n' +
        '  <div class="actions">\n   <a class="btn" href="' + esc(pageWa) + '" target="_blank" rel="noopener">' + icon('whatsapp') + 'בואו נדבר בוואטסאפ</a>\n   <a class="btn line" href="#services">לכל השירותים</a>\n  </div>\n' +
        (arr(H.chips).length ? '  <ul class="chips">' + arr(H.chips).map(function (c) { return '<li>' + icon('heart') + esc(c) + '</li>'; }).join('') + '</ul>\n' : '') + ' </div>\n' +
        ' <div class="arch"' + photoAttr('hero') + (hasImg('hero') ? '' : ' role="img" aria-label="מקום לתמונה"') + '>\n  <div class="shape b"></div><div class="shape c"></div><div class="shape"></div>\n  <div class="in">' + photoInner('hero', 'sun', 'כאן תהיה התמונה') + '</div>\n </div>\n</div></div>\n\n';

      m += '<section id="services" style="padding-top:30px"><div class="wrap">\n ' + sectionHead('שירותים', H.services_title, H.services_sub) + '\n <div class="svcs">' +
        SV.map(function (s) {
          return '\n  <a class="svc" href="' + esc(s.file) + '" style="--accent:' + esc(s.accent) + '">\n   <div class="ico">' + icon(s.icon) + '</div>\n   <span class="pain">' + esc(s.card_pain) + '</span>\n   <h3>' + esc(s.short) + '</h3>\n   <p>' + esc(s.card_promise) + '</p>\n   <span class="go">לפרטים ולהתאמה ' + icon('arrow') + '</span>\n  </a>';
        }).join('') + '\n </div>\n <div class="helpbar"><p>לא בטוחים מה מתאים? שיחת היכרות קצרה וללא התחייבות.</p><a class="btn" href="' + esc(pageWa) + '" target="_blank" rel="noopener">' + icon('whatsapp') + 'נעזור לכם לבחור</a></div>\n</div></section>\n\n';

      var hats = arr(H.hats), hatColors = ['var(--p-clay)', 'var(--p-amber)', 'var(--p-sage)'];
      m += '<section id="holistic" class="holistic white-bg r-top"><div class="wrap">\n ' + sectionHead('הגישה ההוליסטית', H.holistic_title, H.holistic_intro) + '\n <div class="grid">\n' +
        '  <svg class="venn" viewBox="0 0 400 360" role="img" aria-label="תרשים: הדרכת הורים, התפתחות ושינה נפגשים סביב הילד">\n   <circle cx="200" cy="120" r="108" fill="var(--p-sage)"/>\n   <circle cx="268" cy="238" r="108" fill="var(--p-clay)"/>\n   <circle cx="132" cy="238" r="108" fill="var(--p-amber)"/>\n' +
        '   <text x="200" y="66" text-anchor="middle" font-size="21">שינה</text>\n   <text x="318" y="272" text-anchor="middle" font-size="19">הדרכת</text><text x="318" y="296" text-anchor="middle" font-size="19">הורים</text>\n   <text x="82" y="284" text-anchor="middle" font-size="19">התפתחות</text>\n' +
        '   <circle cx="200" cy="198" r="44" fill="#fffaf3" fill-opacity="1"/>\n   <text x="200" y="195" text-anchor="middle" font-size="16">הילד</text><text x="200" y="215" text-anchor="middle" font-size="16">שלכם</text>\n  </svg>\n  <div>\n   <div class="hats">' +
        hats.map(function (h, i) { return '\n    <div class="hat" style="--c:' + hatColors[i % 3] + '"><div class="ico">' + icon(h.icon) + '</div><div><h3>' + esc(h.title) + '</h3><p>' + esc(h.text) + '</p></div></div>'; }).join('') +
        '\n   </div>\n   <div class="actions"><a class="btn" href="' + esc(pageWa) + '" target="_blank" rel="noopener">' + icon('whatsapp') + 'רוצים מבט שלם? בואו נדבר</a></div>\n  </div>\n </div>\n' +
        (arr(H.why).length ? ' <div class="why">' + arr(H.why).map(function (w) { return '<div><h3>' + esc(w.title) + '</h3><p>' + esc(w.text) + '</p></div>'; }).join('') + '</div>\n' : '') +
        (H.holistic_example ? ' <p class="example" style="max-width:900px;margin-inline:auto;margin-top:26px"><b>לדוגמה: </b>' + esc(H.holistic_example) + '</p>\n' : '') + '</div></section>\n\n';

      m += '<section id="about" class="about white-bg" style="padding-top:20px"><div class="wrap">\n <div class="photo"' + photoAttr('about') + (hasImg('about') ? '' : ' role="img" aria-label="מקום לתמונה אישית"') + '>' + photoInner('about', 'heart', 'תמונה אישית') + '</div>\n <div>\n  <span class="tag">קצת עליי</span>\n  <h2>' + esc(H.about_title) + '</h2>\n  ' +
        aboutParas.map(function (p) { return '<p>' + esc(p) + '</p>'; }).join('') + '\n' +
        (arr(H.credentials).length ? '  <ul class="cred">' + arr(H.credentials).map(function (x) { return '<li>' + icon('check') + esc(x) + '</li>'; }).join('') + '</ul>\n' : '') + ' </div>\n</div></section>\n\n';

      if (homeReviews.length) m += '<section id="reviews" class="band" style="margin-top:80px"><div class="wrap">\n <div class="head"><h2>' + esc(H.testimonials_title) + '</h2></div>\n <div class="reviews-grid' + (homeReviews.length === 1 ? ' one' : '') + '">' +
        homeReviews.map(function (r) { return review(r[0], r[1]); }).join('') + '</div>\n</div></section>\n\n';

      m += funding(false, pageWa) + '\n\n';
      if (arr(H.faq).length) m += '<section id="faq" style="padding-top:20px"><div class="wrap">\n <div class="head"><h2>' + esc(H.faq_title) + '</h2></div>\n ' + faq(H.faq) + '\n</div></section>\n\n';
      m += contact(H.contact_title, H.contact_sub, H.contact_chips, pageWa);

      var ld = { '@context': 'https://schema.org', '@type': 'ProfessionalService', name: B.name, description: H.meta, telephone: '+' + phoneIntl, email: B.email, sameAs: [fixUrl(B.instagram), fixUrl(B.facebook)].filter(Boolean), inLanguage: 'he',
        hasOfferCatalog: { '@type': 'OfferCatalog', name: 'שירותים', itemListElement: SV.map(function (s) { return { '@type': 'Offer', itemOffered: { '@type': 'Service', name: s.short, description: s.meta } }; }) } };
      if (base) ld.url = base + '/';
      return pageShell({ title: B.name + ' | ' + H.title, desc: H.meta, ld: ld, main: m, nav: nav('', homeReviews.length > 0), h: '', S: null, pageWa: pageWa, canonical: base ? base + '/' : '' });
    }

    /* ---------- עמוד שירות ---------- */
    function servicePage(S) {
      var pageWa = wa(S.wa_text);
      var tests = arr(S.testimonials);
      var m = '';
      m += '<div class="s-hero" id="top"><div class="wrap">\n <div>\n  <nav class="crumbs" aria-label="מיקום באתר"><a href="index.html">דף הבית</a><span aria-hidden="true">/</span><a href="index.html#services">שירותים</a><span aria-hidden="true">/</span><span aria-current="page">' + esc(S.short) + '</span></nav>\n' +
        '  <span class="eyebrow">' + esc(S.eyebrow) + '</span>\n  <h1>' + ml(S.hero_title) + '</h1>\n  <p class="sub">' + esc(S.hero_sub) + '</p>\n' +
        (arr(S.highlights).length ? '  <ul class="hl">' + arr(S.highlights).map(function (x) { return '<li>' + icon('check') + esc(x) + '</li>'; }).join('') + '</ul>\n' : '') +
        '  <div class="actions">\n   <a class="btn" href="' + esc(pageWa) + '" target="_blank" rel="noopener">' + icon('whatsapp') + 'לשיחת היכרות בוואטסאפ</a>\n   <a class="btn line" href="#included">' + esc(S.included_title) + '</a>\n  </div>\n' +
        '  <p class="fund-line">' + icon('wallet') + '<span>אפשר לקבל השתתפות כספית דרך סל ההריון או המילואים, לפי זכאות. <a href="#funding">לפרטים</a></span></p>\n </div>\n' +
        ' <div class="s-art"' + photoAttr(S.slug) + (hasImg(S.slug) ? '' : ' role="img" aria-label="מקום לתמונה"') + '>' + photoInner(S.slug, S.icon, 'כאן אפשר להוסיף תמונה') + '</div>\n</div></div>\n\n';

      m += '<div class="switch"><div class="wrap"><nav aria-label="שירותים נוספים"><ul><li>שירותים:</li>\n' + SV.map(function (s) {
        return ' <li><a href="' + esc(s.file) + '"' + (s.slug === S.slug ? ' aria-current="page"' : '') + '>' + icon(s.icon) + esc(s.short) + '</a></li>';
      }).join('\n') + '\n</ul></nav></div></div>\n\n';

      if (arr(S.fits).length) m += '<section id="fits"><div class="wrap">\n <div class="head"><h2>' + esc(S.fits_title) + '</h2></div>\n <ul class="fits">' + arr(S.fits).map(function (x) { return '<li>' + icon('check') + '<span>' + esc(x) + '</span></li>'; }).join('') +
        '</ul>\n <div class="actions" style="justify-content:center;margin-top:30px"><a class="btn" href="' + esc(pageWa) + '" target="_blank" rel="noopener">' + icon('whatsapp') + 'נשמע כמונו? בואו נדבר</a></div>\n</div></section>\n\n';

      var hasApproach = arr(S.approach).length > 0;
      if (hasApproach) m += '<section id="approach" class="white-bg r-top on-white"><div class="wrap">\n ' + sectionHead('הגישה', S.approach_title, S.approach_intro) + '\n ' + cards4(S.approach) + '\n</div></section>\n\n';
      if (arr(S.included).length) m += '<section id="included"' + (hasApproach ? '' : ' class="white-bg r-top on-white"') + '><div class="wrap">\n ' + sectionHead('מה מקבלים', S.included_title, '') + '\n ' + cards4(S.included) + '\n</div></section>\n\n';
      if (arr(S.steps).length) m += '<section id="how"' + (hasApproach ? ' class="white-bg on-white"' : '') + '><div class="wrap">\n ' + sectionHead('התהליך', S.steps_title, '') + '\n <div class="timeline">' +
        arr(S.steps).map(function (s) { return '\n  <div class="tstep"><div><h3>' + esc(s.title) + '</h3><p>' + esc(s.text) + '</p></div></div>'; }).join('') + '\n </div>\n' + (S.note ? ' <p class="note">' + esc(S.note) + '</p>\n' : '') + '</div></section>\n\n';

      var rel = arr(S.related).filter(function (r) { return SVM[r.slug]; });
      if (rel.length) m += '<section id="related"><div class="wrap">\n ' + sectionHead('מבט הוליסטי', 'איך זה מתחבר לשאר השירותים', 'הילד הוא אחד. לכן אפשר להתחיל משירות אחד ולהוסיף עוד כשצריך.') + '\n <div class="related">' + rel.map(function (r) {
        var o = SVM[r.slug];
        return '\n  <a class="rel" href="' + esc(o.file) + '" style="--accent:' + esc(o.accent) + '"><h3>' + icon(o.icon) + esc(o.short) + '</h3><p>' + esc(r.text) + '</p><span class="go">לעמוד השירות ' + icon('arrow') + '</span></a>';
      }).join('') + '\n </div>\n</div></section>\n\n';

      m += funding(true, pageWa) + '\n\n';
      if (tests.length) m += '<section id="reviews" class="band" style="margin-top:50px"><div class="wrap">\n <div class="head"><h2>' + esc(H.testimonials_title) + '</h2></div>\n <div class="reviews-grid' + (tests.length === 1 ? ' one' : '') + '">' + tests.map(function (t) { return review(t, S); }).join('') + '</div>\n</div></section>\n\n';
      if (arr(S.faq).length) m += '<section id="faq" style="padding-top:60px"><div class="wrap">\n <div class="head"><h2>שאלות נפוצות על ' + esc(S.short) + '</h2></div>\n ' + faq(S.faq) + '\n</div></section>\n\n';
      m += contact('רוצים לדבר על ' + S.short + '?', H.contact_sub, null, pageWa);

      var ld = { '@context': 'https://schema.org', '@type': 'Service', name: S.short, description: S.meta, inLanguage: 'he', provider: { '@type': 'ProfessionalService', name: B.name, telephone: '+' + phoneIntl } };
      if (base) ld.url = base + '/' + S.file;
      return pageShell({ title: S.short + ' | ' + B.name, desc: S.meta, ld: ld, main: m, nav: nav('index.html', tests.length > 0), h: 'index.html', S: S, pageWa: pageWa, canonical: base ? base + '/' + S.file : '' });
    }

    function notFoundPage() {
      var pageWa = wa(H.wa_text);
      var m = '<section style="padding-top:90px;padding-bottom:120px"><div class="wrap"><div class="head"><span class="tag">404</span><h1>העמוד לא נמצא</h1><p>ייתכן שהקישור ישן או שהוקלד בטעות. אפשר לחזור לדף הבית ולבחור משם.</p></div>' +
        '<div class="actions" style="justify-content:center"><a class="btn" href="index.html">לדף הבית</a><a class="btn line" href="' + esc(pageWa) + '" target="_blank" rel="noopener">' + icon('whatsapp') + 'לשלוח הודעה</a></div></div></section>\n<span id="contact"></span>';
      return pageShell({ title: 'העמוד לא נמצא | ' + B.name, desc: 'העמוד לא נמצא', ld: { '@context': 'https://schema.org', '@type': 'WebPage', name: '404' }, main: m, nav: nav('index.html', false), h: 'index.html', S: null, pageWa: pageWa, canonical: '' });
    }

    /* ---------- הרכבת הקבצים ---------- */
    var files = [{ path: 'index.html', text: homePage() }];
    SV.forEach(function (s) { files.push({ path: s.file, text: servicePage(s) }); });
    if (!preview) {
      files.push({ path: '404.html', text: notFoundPage() });
      Object.keys(IM).forEach(function (k) {
        var i = IM[k];
        if (!i || !i.data) return;
        if (k === 'og' && !base) return;
        var m = /^data:image\/[a-z+]+;base64,(.*)$/.exec(i.data);
        if (m) files.push({ path: imgFile(k), base64: m[1] });
      });
      if (base) {
        var urls = [base + '/'].concat(SV.map(function (s) { return base + '/' + s.file; }));
        files.push({ path: 'sitemap.xml', text: '<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n' + urls.map(function (u) { return ' <url><loc>' + esc(u) + '</loc></url>'; }).join('\n') + '\n</urlset>\n' });
        files.push({ path: 'robots.txt', text: 'User-agent: *\nAllow: /\nSitemap: ' + base + '/sitemap.xml\n' });
      }
    }
    return { files: files, issues: findIssues(project) };
  }

  return { generate: generate, findIssues: findIssues, normalizePhone: normalizePhone, fixUrl: fixUrl, ICON_NAMES: ICON_NAMES, IMAGE_SLOTS: IMAGE_SLOTS, PLACEHOLDERS: PLACEHOLDERS, esc: esc };
});
