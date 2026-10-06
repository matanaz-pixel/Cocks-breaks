# האתר המורחב

חמישה עמודים: דף בית (`index.html`) וארבעה עמודי שירות (`newborn.html`, `sleep.html`, `toddlers.html`, `development.html`).
כל עמוד עצמאי: העיצוב, הגופנים והאייקונים נמצאים בתוכו, ואין בקשות חיצוניות.

## החלפת תוכן
כל הטקסטים, הטלפון, הקישורים, ההמלצות והשאלות נמצאים ב-`src/content.py`.
אחרי עריכה: `python3 site/src/build.py` (דורש `pip install jinja2`).
המלצות מסומנות "דוגמה" כל עוד יש בהן `"sample": True`. מוחקים את השדה כשמחליפים בהמלצה אמיתית.

## קבצים
- `src/style.css`: העיצוב המשותף. צבע ההדגשה של כל שירות מוגדר ב-`content.py` (`accent`, `pastel`).
- `src/base.html`, `home.html`, `service.html`, `macros.html`: התבניות.
- `src/fonts.css`: גופנים (עברית ולטינית) מוטמעים. נוצר על ידי `sleep-landing/src/fetch_fonts.py`.

## מה עדיין לא נכלל
תמונות אמיתיות (יש ממלאי מקום), גלריה, טופס יצירת קשר, תמונת שיתוף והצהרת נגישות.
