# האתר המורחב

חמישה עמודים (`index.html`, `newborn.html`, `sleep.html`, `toddlers.html`, `development.html`) ועמוד `404.html`.
כל עמוד עצמאי: העיצוב, הגופנים והאייקונים בתוכו, ואין בקשות חיצוניות.

## איך מעדכנים תוכן
משתמשים בעורך: `editor/site-editor.html`. הוא מוריד ZIP שמכיל את האתר המלא, מוכן להעלאה.

## מבנה הקוד
- `src/generator.js`: מחולל האתר. אותו קוד רץ בתוך העורך ובשורת הפקודה.
- `src/content.json`: תוכן ברירת המחדל. העורך נבנה איתו.
- `src/style.css`, `src/fonts.css`, `src/sprite.html`: עיצוב, גופנים ואייקונים.
- `src/build.js`: בונה את האתר (`node src/build.js [--project גיבוי.json] [--out תיקייה] [--issues]`).
- `src/build-editor.js`: בונה את קובץ העורך.
- `src/fetch_fonts.py`: יצר את `fonts.css` (עברית ולטינית מוטמעות). אין צורך להריץ שוב.

## בנייה
```
cd site
npm run build
```
