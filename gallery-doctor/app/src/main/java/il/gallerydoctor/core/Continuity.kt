package il.gallerydoctor.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ======================================================================================
// "The gallery shows only part of the files and reloads itself": evidence that the media index was
// rebuilt, changes between two scans, folders the gallery is told to ignore, and the recovery guide.
// Pure Kotlin so it is unit-testable on the JVM.
// ======================================================================================

/** How many files were added to the media index during one clock hour (DATE_ADDED is the index time). */
data class HourBucket(val hour: Long, val count: Int, val aged: Int)

/**
 * A short period in which a large share of all files entered the index at once. A phone transfer, a restore
 * or a full re-scan of the media index looks exactly like this; a normal day of use does not.
 */
data class IndexBurst(val startSec: Long, val endSec: Long, val count: Int, val aged: Int, val sharePercent: Int) {
    /** Share of the burst's files that already existed on disk long before they were indexed. */
    val agedPercent: Int get() = if (count == 0) 0 else aged * 100 / count
}

object IndexBursts {
    /** Buckets smaller than this are never queried: the SQL pre-filter keeps the work bounded. */
    const val BUCKET_MIN = 100
    private const val MIN_COUNT = 500
    private const val MIN_SHARE_PERCENT = 3
    private const val MERGE_GAP_HOURS = 2L
    /** A burst that holds at least this share of all files is "the whole library arrived at once". */
    const val LIBRARY_SHARE_PERCENT = 40

    fun threshold(total: Int) = maxOf(MIN_COUNT, total * MIN_SHARE_PERCENT / 100)

    fun detect(buckets: List<HourBucket>, total: Int): List<IndexBurst> {
        if (total <= 0 || buckets.isEmpty()) return emptyList()
        val merged = ArrayList<IndexBurst>()
        var start = 0L; var end = 0L; var count = 0; var aged = 0; var open = false
        fun flush() {
            if (open) merged += IndexBurst(start * 3600, end * 3600 + 3599, count, aged, (count * 100L / total).toInt())
            open = false
        }
        for (b in buckets.sortedBy { it.hour }) {
            if (open && b.hour - end > MERGE_GAP_HOURS) flush()
            if (!open) { start = b.hour; count = 0; aged = 0; open = true }
            end = b.hour; count += b.count; aged += b.aged
        }
        flush()
        val min = threshold(total)
        return merged.filter { it.count >= min }
    }

    /** The burst that holds most of the library (a transfer or full rebuild), if there is one. */
    fun libraryBurst(bursts: List<IndexBurst>): IndexBurst? =
        bursts.maxByOrNull { it.count }?.takeIf { it.sharePercent >= LIBRARY_SHARE_PERCENT }
}

/** What a previous scan saw, kept in a tiny local history so that the next scan can say what changed. */
data class ScanSnapshot(
    val startedMillis: Long,
    val totalFiles: Int,
    /** Favorites in Android's media index, or null if that was not available. */
    val favorites: Int?,
    val orphans: Int,
    val bursts: Int,
    val freeBytes: Long,
) {
    fun encode() = listOf(startedMillis, totalFiles, favorites ?: -1, orphans, bursts, freeBytes).joinToString("|")

    companion object {
        fun decode(line: String): ScanSnapshot? {
            val p = line.split('|')
            if (p.size != 6) return null
            return try {
                ScanSnapshot(p[0].toLong(), p[1].toInt(), p[2].toInt().takeIf { it >= 0 }, p[3].toInt(), p[4].toInt(), p[5].toLong())
            } catch (_: NumberFormatException) {
                null
            }
        }
    }
}

/** A folder with a ".nomedia" file: every gallery is told to ignore the media files inside it. */
data class NomediaDir(val path: String, val mediaFiles: Int)

/** A hidden (dot) folder holding media files, such as a private album or a recycle bin. */
data class HiddenDir(val path: String, val mediaFiles: Int, val bytes: Long)

object Continuity {
    private fun dateTime(sec: Long) = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date(sec * 1000))
    private fun date(ms: Long) = SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date(ms))

    fun burstWhen(b: IndexBurst): String {
        val hours = (b.endSec - b.startSec) / 3600 + 1
        return if (hours <= 1) "ב-${dateTime(b.startSec)}" else "בין ${dateTime(b.startSec)} ל-${dateTime(b.endSec)}"
    }

    /** Parent folders that every gallery shows: under DCIM or Pictures and not an app's own private folder. */
    fun isMainMediaDir(path: String): Boolean {
        val rel = path.substringAfter("/0/", path)
        val top = rel.substringBefore('/')
        return (top == "DCIM" || top == "Pictures") && !rel.contains("WhatsApp") && !rel.contains("Telegram")
    }

    fun lines(i: ReportInput): List<String> = buildList {
        i.favorites?.let {
            add("מועדפים באינדקס המדיה של אנדרואיד: ${He.num(it)}. גלריית Xiaomi ו-Google Photos שומרות מועדפים במסד נתונים משלהן, ולכן המספר עשוי להיות 0 גם כשיש מועדפים. הוא נשמר להשוואה בסריקה הבאה.")
        }
        val prev = i.previous
        if (prev != null) {
            val dropped = prev.totalFiles - i.totalFiles
            add(
                "סריקה קודמת (${date(prev.startedMillis)}): ${He.num(prev.totalFiles)} קבצים באינדקס" +
                    (prev.favorites?.let { ", $it מועדפים" } ?: "") + ". עכשיו: ${He.num(i.totalFiles)}" +
                    (i.favorites?.let { ", $it מועדפים" } ?: "") + "." +
                    if (dropped > 0) " ירידה של ${He.num(dropped)} קבצים." else "",
            )
        } else add("זו הסריקה הראשונה שנשמרה בטלפון. בסריקה הבאה הדוח יציג מה השתנה (מספר הקבצים והמועדפים באינדקס).")
        val lib = IndexBursts.libraryBurst(i.bursts)
        if (lib != null) add("${lib.sharePercent}% מהקבצים (${He.num(lib.count)}) נכנסו לאינדקס ${burstWhen(lib)}. זה מתאים להעברה מטלפון אחר, לשחזור או לבנייה מלאה של האינדקס.")
        for (b in i.bursts.filter { it != lib }) {
            add("${He.num(b.count)} קבצים (${b.sharePercent}%) נכנסו לאינדקס ${burstWhen(b)}" + if (b.agedPercent >= 50) ", ו-${b.agedPercent}% מהם נוצרו הרבה לפני כן (סריקה מחדש של קבצים קיימים)." else ".")
        }
        if (i.bursts.isEmpty()) add("לא נמצאו אירועים שבהם חלק גדול מהקבצים נכנס לאינדקס בבת אחת.")
        for (d in i.nomediaDirs.take(5)) add("בתיקייה \"${d.path.substringAfter("/0/", d.path)}\" יש קובץ .nomedia והגלריה מתעלמת מ-${He.files(d.mediaFiles)} שבה.")
        for (d in i.hiddenDirs.take(5)) add("תיקייה מוסתרת \"${d.path.substringAfter("/0/", d.path)}\": ${He.files(d.mediaFiles)}, ${He.bytes(d.bytes)}. הגלריה מציגה אותה רק מתוך האפליקציה.")
        i.thumbEntries?.takeIf { it >= 1000 }?.let { add("תיקיית התמונות הממוזערות (.thumbnails) מכילה ${He.num(it)} פריטים. אפשר למחוק אותה בבטחה מנהל קבצים, והגלריה תבנה אותה מחדש.") }
    }
}

data class GuideStep(val title: String, val body: String)

/** The recovery and cleaning guide: the right order, what is safe, and what resets what. */
object RecoveryGuide {
    private fun galleryLabel(i: ReportInput) = i.apps.firstOrNull { it.role == AppRole.GALLERY && it.pkg == "com.miui.gallery" }?.label
        ?: i.apps.firstOrNull { it.role == AppRole.GALLERY }?.label ?: "הגלריה"

    fun steps(i: ReportInput): List<GuideStep> = buildList {
        val gallery = galleryLabel(i)
        val xiaomi = i.device?.isXiaomiFamily == true
        val main = i.volumes.firstOrNull { !it.removable } ?: i.volumes.firstOrNull()
        val target = main?.let { maxOf(5L * 1024 * 1024 * 1024, it.totalBytes / 10) }

        add(
            GuideStep(
                "קודם כל: גיבוי, ורק אז ניקוי",
                "העתיקו למחשב (כבל USB, מצב \"העברת קבצים\") את התיקיות DCIM, Pictures, WhatsApp ו-MIUI אם היא קיימת. כך הקבצים בטוחים גם אם משהו ישתבש בהמשך. " +
                    "מועדפים ואלבומים של $gallery נשמרים במסד הנתונים של האפליקציה ולא בקבצים עצמם, ולמיטב ידיעתי אין ייצוא רשמי שלהם. " +
                    "כדי לא לאבד אותם, העתיקו את תמונות המועדפים לתיקייה רגילה במנהל הקבצים.",
            ),
        )
        add(
            GuideStep(
                "בדיקת הכרעה: האם הבעיה בקבצים או באפליקציית הגלריה?",
                "פתחו את אותן תמונות באפליקציה אחרת שקוראת ישירות מאינדקס המדיה של המערכת: Google Photos, או Fossify Gallery (חינמית וקוד פתוח). " +
                    "אם הכול מוצג שם במלואו ובצורה יציבה, הקבצים והאינדקס של אנדרואיד תקינים והבעיה במסד הנתונים הפרטי של $gallery (ראו \"איפוס הגלריה\"). " +
                    "אם גם שם חסרים קבצים או שהתצוגה נטענת מחדש, הבעיה באינדקס של המערכת או באחסון (ראו \"איפוס אחסון המדיה\").",
            ),
        )
        val tight = main != null && main.usedPercent >= 85
        add(
            GuideStep(
                if (tight) "פנו מקום לפני כל איפוס" else "שמרו על מקום פנוי",
                (if (tight && main != null) "האחסון תפוס ב-${main.usedPercent}% ונותרו ${He.bytes(main.freeBytes)}. " else "") +
                    "היעד: לפחות ${He.bytes(target ?: (5L shl 30))} פנויים (כ-10%). בלי מקום, בניית אינדקס חדש או מטמון חדש נכשלת באמצע, והגלריה חוזרת לטעון מחדש. " +
                    if (i.videoBytes > i.imageBytes && i.videoBytes > 0) "רוב המקום תפוס בסרטונים (${He.bytes(i.videoBytes)}): העבירו למחשב או לענן את הסרטונים הישנים והגדולים, ואז מחקו אותם מהטלפון." else "העבירו למחשב או לענן את הקבצים הגדולים והישנים, ואז מחקו אותם מהטלפון.",
            ),
        )
        add(
            GuideStep(
                "איפוס אחסון המדיה (בטוח לקבצים)",
                "הגדרות ← אפליקציות ← הצג אפליקציות מערכת ← אחסון מדיה (Media Storage) ← אחסון ← \"נקה מטמון\", ואז \"נקה נתונים\". " +
                    "התמונות והסרטונים לא נמחקים: אנדרואיד בונה את רשימת הקבצים מחדש מאפס. עשו זאת כשהטלפון בטעינה ויש מקום פנוי, והמתינו עד שהסריקה מסתיימת. " +
                    "בספרייה של עשרות אלפי קבצים זה יכול לקחת כמה דקות ועד כשעה. שמות התפריטים ב-HyperOS עשויים להיות שונים מעט.",
            ),
        )
        add(
            GuideStep(
                "איפוס הגלריה (רק אחרי גיבוי)",
                "הגדרות ← אפליקציות ← $gallery ← אחסון ← \"נקה מטמון\". אם הבעיה נמשכת, \"נקה נתונים\". " +
                    "הקבצים לא נמחקים, אבל אלבומים שהוגדרו באפליקציה ומועדפים שלא סונכרנו לענן יתאפסו. אם הפעלתם בעבר סנכרון לענן, ייתכן שהם יחזרו משם. " +
                    "אחרי האיפוס פתחו את הגלריה והשאירו אותה פתוחה עד שהיא מסיימת לבנות את הרשימה.",
            ),
        )
        add(
            GuideStep(
                "סנכרון לענן, כרשת ביטחון",
                "סנכרון (${if (xiaomi) "Mi Cloud לגלריית Xiaomi, או " else ""}גיבוי ב-Google Photos) שומר גם אלבומים ומועדפים מחוץ לטלפון. הפעילו אותו רק אחרי שפיניתם מקום, כי סנכרון ראשון של ספרייה גדולה כותב הרבה לאינדקס. " +
                    "האפליקציה הזאת לא יכולה לדעת אם הסנכרון פעיל אצלכם.",
            ),
        )
        add(
            GuideStep(
                "כדי שזה לא יחזור",
                "שמרו תמיד לפחות 10% פנוי. אל תתנו לטלפון להיכבות מסוללה ריקה, כי כיבוי באמצע כתיבה פוגע במסדי הנתונים: טענו אותו כשנשאר 15-20%. " +
                    (if (xiaomi) "הגדרות ← אפליקציות ← ניהול אפליקציות ← $gallery ← חיסכון בסוללה ← \"ללא הגבלות\", והפעילו הפעלה אוטומטית. " else "") +
                    "הימנעו מאפליקציות ניקוי שמוחקות מטמון ותמונות ממוזערות. " +
                    (if (xiaomi) "בהחלפת טלפון בעתיד, העבירו את הקבצים בלבד ולא את הנתונים של הגלריה: ייתכן שהמעבר מ-13T Pro ל-15T Pro העתיק איתו מסד נתונים פגום, וזה מסביר למה התקלה לא נעלמה במכשיר החדש. את זה לא ניתן לאמת מתוך האפליקציה." else ""),
            ),
        )
        add(
            GuideStep(
                "ואחרי זה: סריקה חוזרת",
                "הריצו את הסריקה שוב בעוד יום או יומיים. הדוח ישווה את מספר הקבצים והמועדפים באינדקס למה שהיה, ויראה אם אירוע בנייה מחדש חזר.",
            ),
        )
    }
}
