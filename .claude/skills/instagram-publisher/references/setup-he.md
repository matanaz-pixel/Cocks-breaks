# חיבור אינסטגרם – הגדרה חד־פעמית (כ-15 דקות)

> עקרון: הסקיל מפרסם דרך ה-API **הרשמי** של Meta. זה בטוח לחשבון (בניגוד לבוטים שמתחברים עם סיסמה, שעלולים להוביל לחסימה).
> הערה: ממשקי Meta משתנים לעיתים; אם שם כפתור שונה – הכוונה זהה.

## שלב 1 – חשבון מקצועי מקושר לדף פייסבוק
1. באינסטגרם: הגדרות ← **סוג חשבון וכלים** ← מעבר לחשבון מקצועי (Business או Creator).
2. חבר **דף פייסבוק** (גם דף ריק מספיק): עריכת פרופיל ← קישורים/דף, או בדף הפייסבוק: הגדרות ← חשבונות מקושרים ← Instagram.
3. ודא שאתה **מנהל (Admin)** בדף.

## שלב 2 – אפליקציית מפתחים (חינם)
1. היכנס ל-[developers.facebook.com](https://developers.facebook.com) ← **My Apps** ← **Create App**.
2. סוג: **Business** (או "Other" ← Business). שם חופשי.
3. הוסף את המוצר **Instagram** (Instagram API with Facebook Login) / "Instagram Graph API".
4. **App settings ← Basic**: העתק **App ID** ו-**App Secret** (הסיסמה של האפליקציה – אל תשתף!).
5. השאר את האפליקציה במצב **Development**. כל עוד אתה מנהל האפליקציה והחשבון שלך, **אין צורך באישור (App Review)** כדי לפרסם בחשבון שלך.

## שלב 3 – טוקן קצר־טווח
1. פתח את [Graph API Explorer](https://developers.facebook.com/tools/explorer/), בחר את האפליקציה שלך.
2. **Get User Access Token**, סמן הרשאות:
   `instagram_basic`, `instagram_content_publish`, `pages_show_list`, `pages_read_engagement`
   (אופציונלי: `instagram_manage_comments` לתגובה ראשונה, `business_management` אם הדף שייך ל-Business Portfolio).
3. אשר, והעתק את הטוקן.

## שלב 4 – חיבור
הרץ **בעצמך** (כדי שהסודות לא יעברו בצ'אט), מתוך תיקיית הסקיל:
```bash
cd .claude/skills/instagram-publisher
pip install pillow
python3 scripts/ig.py auth-setup --app-id <APP_ID> --app-secret <APP_SECRET> --user-token <SHORT_TOKEN>
python3 scripts/ig.py auth-check
```
הסקריפט מחליף ל-long-lived, מוצא את חשבון האינסטגרם, ושומר טוקן **דף** (Page token) שלא פג תוקף ב-`.env` (הרשאות 600, מוחרג מ-git).
אם יש לך כמה חשבונות – הוסף `--ig-username שם_המשתמש`.

**סביבת ענן (Claude Code on the web):** `.env` לא נשמר בין סשנים. הגדר את אותם ערכים כ-**Environment secrets**: `IG_ACCESS_TOKEN`, `IG_USER_ID`, `IG_USERNAME`, `IG_MEDIA_HOST` וערכי המארח (`CLOUDINARY_URL`).

## שלב 5 – אחסון מדיה (חובה לפרסום)
ה-API של אינסטגרם **שואב** את הקובץ מכתובת ציבורית, ולא מקבל העלאה ישירה. צריך מקום זמני לארח את הקבצים:

**אפשרות מומלצת – Cloudinary (חינם):**
1. הירשם ב-[cloudinary.com](https://cloudinary.com) ← Dashboard ← **API Environment variable** (נראה `cloudinary://KEY:SECRET@CLOUD`).
2. הוסף ל-`.env`:
```
IG_MEDIA_HOST=cloudinary
CLOUDINARY_URL=cloudinary://KEY:SECRET@CLOUD
```
(הגבלת וידאו בחשבון החינמי ~100MB – מספיק לריל רגיל.)

**חלופות:** `IG_MEDIA_HOST=command` עם `IG_MEDIA_UPLOAD_CMD` (למשל `aws s3 cp`/`rclone`), או `static` (תיקייה שמוגשת בשרת שלך).

## בדיקה
```bash
python3 scripts/ig.py auth-check     # אמור להציג שם משתמש, עוקבים, ומארח מדיה
python3 scripts/ig.py learn          # לומד את הסגנון (קורא בלבד – לא מפרסם כלום)
```

## אבטחה
- `.env` מכיל גישה לפרסום בחשבון שלך. אל תעלה אותו ל-git, אל תשלח בצ'אט.
- לביטול גישה: Facebook ← הגדרות ← Apps and Websites ← הסר את האפליקציה, או מחק את האפליקציה במפתחים.
- אם טוקן דלף – בצע ביטול ואז `auth-setup` מחדש.
