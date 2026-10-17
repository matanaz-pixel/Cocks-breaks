# מנוע השורטס

מערכת שמייצרת לבד שורטס לערוץ **Billion Dollar Blunders**: סיפורים אמיתיים על החלטות כסף גרועות במיוחד (Blockbuster ו-Netflix, קודאק, פיצת הביטקוין). הכל רץ בחינם על GitHub Actions.

## איך זה עובד

1. כל יום ב-13:00 UTC המערכת לוקחת את הסיפור הבא מ-`content/bank.json`, מקריינת אותו בקול נוירוני חינמי, מרנדרת סרטון אנכי עם גרפיקה, כתוביות ומוזיקה מקורית, ושולחת אותו אליך לטלגרם עם כפתורי **Approve / Reject**.
2. כל 20 דקות המערכת בודקת אם לחצת. סרטון שאישרת עולה ליוטיוב עם כותרת, תיאור, מקורות ותגיות.
3. כשנשארים פחות מ-7 סיפורים, המערכת כותבת חדשים עם Gemini (חינמי, לא חובה), בהשראת הסרטונים החריגים שמנוע השוק מצא. סיפור שנכתב ככה מסומן בטלגרם כדי שתבדוק את העובדות.
4. מנוע השוק (למטה) ממשיך לרוץ כל יום, כדי לאמת שהנישה נכונה.

## מה צריך ממך (פעם אחת)

| מה | למה | זמן |
|---|---|---|
| בוט טלגרם: ב-@BotFather שולחים `/newbot`, ואז שולחים לבוט החדש הודעה כלשהי | כאן מגיעים הסרטונים לאישור | 2 דקות |
| ערוץ יוטיוב: [youtube.com/create_channel](https://www.youtube.com/create_channel), שם **Billion Dollar Blunders**, ולהעלות את הלוגו, הבאנר והתיאור מענף `shorts-samples` | הערוץ חייב להיות בחשבון הגוגל שלך | 5 דקות |
| פרויקט ב-Google Cloud עם YouTube Data API v3, מפתח API, ו-OAuth client מסוג Desktop. במסך ה-OAuth consent לבחור **In production** | בלי זה ההרשאה פגה כל 7 ימים | 10 דקות |
| להריץ פעם אחת במחשב: `python -m studio auth client_secret.json` ולהתחבר עם החשבון של הערוץ | זה מייצר את טוקן ההעלאה | 2 דקות |
| לשמור ב-GitHub (Settings > Secrets and variables > Actions) את: `YOUTUBE_API_KEY`, `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID`, `YT_CLIENT_ID`, `YT_CLIENT_SECRET`, `YT_REFRESH_TOKEN`, ולא חובה `GEMINI_API_KEY` | כאן המערכת קוראת אותם | 5 דקות |
| למזג את ה-PR לענף `main` | הרצות מתוזמנות עובדות רק מ-`main` | דקה |

**חשוב:** יוטיוב מעלה כפרטי כל סרטון שמגיע מפרויקט API שלא עבר את בדיקת התאימות של גוגל ([טופס audit](https://support.google.com/youtube/contact/yt_api_form)). עד שהבדיקה מאושרת, המערכת מעלה כפרטי ושולחת לך בטלגרם קישור: פותחים את YouTube Studio ומשנים ל-Public, וזה לוקח 10 שניות. אחרי האישור מגדירים ב-GitHub משתנה (Variables) בשם `YT_PRIVACY` עם הערך `public`.

את `TELEGRAM_CHAT_ID` מוצאים ככה: אחרי ששלחת הודעה לבוט, פותחים `https://api.telegram.org/bot<TOKEN>/getUpdates` ומעתיקים את `chat.id`.

## פקודות

```bash
cd shorts-engine
pip install -r requirements.txt          # וגם ffmpeg ו-fonts-inter
python -m studio sample bitcoin-pizza     # מרנדר סרטון ל-out/ בלי לשלוח כלום
python -m studio branding                 # לוגו, באנר ותיאור ל-out/branding
python -m studio make                     # מרנדר את הסיפור הבא ושולח לטלגרם
python -m studio poll                     # קורא אישורים ומעלה ליוטיוב
```

## שלב 1: מנוע ניתוח השוק (עלות: $0)

פעם ביום המנוע סורק כ-30 נישות מועמדות באנגלית, אוסף את השורטס הכי נצפים מ-7 הימים האחרונים בכל נישה, ומדרג את הנישות לפי:

| רכיב | משקל | מאיפה |
|---|---|---|
| רווח לכל צפייה | 40% | הערכה בקובץ `market/niches.json` |
| ביקוש (חציון צפיות לשעה) | 25% | נתוני יוטיוב, כל יום |
| הזדמנות (ערוצים קטנים שמצליחים) | 20% | נתוני יוטיוב, כל יום |
| מתאימה לייצור עם AI | 15% | הערכה בקובץ הנישות |

נישה עם סיכון מדיניות (למשל ילדים, מוטיבציה, פשע) מקבלת הורדה בציון.

הדוח היומי נשמר ב-`reports/latest.md`, עם ממוצע של 7 ימים ורשימת סרטונים חריגים: סרטונים שקיבלו פי 10 ויותר צפיות ממספר המנויים של הערוץ שלהם. אחרי 14 ימים של נתונים הדוח ממליץ על נישה לנעילה.

## הפעלה (פעם אחת, כ-10 דקות)

1. נכנסים ל-[Google Cloud Console](https://console.cloud.google.com/) עם חשבון הגוגל שלך ויוצרים פרויקט חדש בשם `shorts-market`.
2. בתפריט **APIs & Services > Library** מחפשים **YouTube Data API v3** ולוחצים **Enable**.
3. ב-**APIs & Services > Credentials** לוחצים **Create credentials > API key**. כדאי להגביל את המפתח ל-YouTube Data API v3 בלבד.
4. בריפו בגיטהאב: **Settings > Secrets and variables > Actions > New repository secret**, בשם `YOUTUBE_API_KEY`, והערך הוא המפתח.
5. ההרצה היומית מתחילה לפעול רק אחרי שהקוד נמצא בענף הראשי (`main`). אפשר להפעיל ידנית מלשונית **Actions > Shorts market scan > Run workflow**.

המפתח הזה חינמי. המנוע משתמש בכ-3,100 מתוך 10,000 היחידות היומיות של יוטיוב, והשאר נשמר להעלאות בשלבים הבאים.

## הרצה מקומית

```bash
cd shorts-engine
python -m unittest discover -s tests -t .
YOUTUBE_API_KEY=... python -m market --out reports
```

אין תלויות חיצוניות, רק Python 3.10 ומעלה.
