package il.gallerydoctor.core

/** How bad a single finding is. Order matters (max wins). */
enum class Severity { INFO, SUSPECT, BROKEN, CRASHER }

/** Per-file verdict stored in the state DB. [code] is persisted, do not renumber. */
enum class Status(val code: Int) {
    TODO(0), OK(1), SUSPECT(2), BROKEN(3), CRASHER(4), GHOST(5), SKIPPED(6);

    companion object {
        fun fromCode(c: Int): Status = entries.firstOrNull { it.code == c } ?: TODO
    }
}

/** Exact reason codes. [he] is the plain-Hebrew explanation shown in tables and exports. */
enum class Reason(val severity: Severity, val he: String) {
    ZERO_BYTES(Severity.BROKEN, "הקובץ ריק (0 בייטים)"),
    BAD_MAGIC(Severity.BROKEN, "תוכן הקובץ לא תואם לפורמט שלו – הקובץ פגום"),
    MAGIC_MISMATCH(Severity.SUSPECT, "הסיומת של הקובץ לא תואמת לתוכן האמיתי שלו"),
    BOUNDS_FAIL(Severity.BROKEN, "אנדרואיד לא מצליח לקרוא את מידות התמונה"),
    BAD_DIMENSIONS(Severity.BROKEN, "מידות התמונה לא תקינות (אפס או שליליות)"),

    JPEG_NO_SOI(Severity.BROKEN, "קובץ JPEG בלי כותרת התחלה"),
    JPEG_NO_EOI(Severity.SUSPECT, "קובץ JPEG חתוך – חסר סוף קובץ"),
    JPEG_BAD_MARKER(Severity.BROKEN, "מבנה פנימי שבור בקובץ JPEG"),
    JPEG_TRUNCATED_SEGMENT(Severity.SUSPECT, "קובץ JPEG נחתך באמצע קטע נתונים"),
    JPEG_NO_SCAN(Severity.BROKEN, "קובץ JPEG בלי נתוני תמונה"),
    ZERO_TAIL(Severity.SUSPECT, "סוף הקובץ מלא באפסים (העתקה או הורדה שנקטעה)"),

    PNG_BAD_IHDR(Severity.BROKEN, "כותרת PNG שבורה"),
    PNG_NO_IEND(Severity.SUSPECT, "קובץ PNG חתוך – חסר סוף קובץ"),
    PNG_TRUNCATED(Severity.SUSPECT, "קובץ PNG נחתך באמצע"),
    PNG_BAD_CRC(Severity.SUSPECT, "נתונים פגומים בקובץ PNG (בדיקת CRC נכשלה)"),

    WEBP_BAD_HEADER(Severity.BROKEN, "כותרת WebP שבורה"),
    WEBP_TRUNCATED(Severity.SUSPECT, "קובץ WebP חתוך"),
    WEBP_SIZE_MISMATCH(Severity.SUSPECT, "גודל ה-WebP המוצהר לא תואם לגודל הקובץ"),

    ISO_NO_FTYP(Severity.BROKEN, "חסרה כותרת ftyp בקובץ"),
    ISO_BAD_BOX(Severity.BROKEN, "מבנה הקובץ שבור (גודל קטע לא תקין)"),
    ISO_NO_MOOV(Severity.BROKEN, "חסר moov – הקלטה שנקטעה באמצע"),
    ISO_TRUNCATED(Severity.SUSPECT, "הקובץ נחתך – הנתונים המוצהרים גדולים מגודל הקובץ"),
    ISO_NO_TRACKS(Severity.BROKEN, "אין ערוצי וידאו או שמע בקובץ"),
    ISO_NO_META(Severity.BROKEN, "חסר מידע מבני בתמונת HEIC/HEIF"),

    EXIF_ERROR(Severity.SUSPECT, "שגיאה בקריאת נתוני EXIF"),
    DECODE_FAIL(Severity.BROKEN, "פענוח התמונה נכשל"),
    DECODE_PARTIAL(Severity.SUSPECT, "התמונה מפוענחת רק חלקית"),
    DECODE_OOM(Severity.SUSPECT, "פענוח התמונה גורם לחוסר זיכרון"),
    VIDEO_OPEN_FAIL(Severity.BROKEN, "נגן הווידאו לא מצליח לפתוח את הקובץ"),
    FRAME_MID_FAIL(Severity.BROKEN, "אי אפשר לחלץ תמונה מאמצע הסרטון"),
    FRAME_END_FAIL(Severity.SUSPECT, "אי אפשר לחלץ תמונה מסוף הסרטון"),

    IO_READ_ERROR(Severity.SUSPECT, "שגיאת קריאה מהאחסון"),
    OPEN_FAILED(Severity.SUSPECT, "אי אפשר לפתוח את הקובץ"),
    INTERMITTENT_IO(Severity.SUSPECT, "הקובץ נקרא אחרת בקריאה חוזרת (אחסון לא יציב)"),

    GHOST_ROW(Severity.INFO, "הקובץ רשום באינדקס המדיה אבל לא קיים בפועל"),

    DECODER_CRASH(Severity.CRASHER, "הפענוח של הקובץ קרס את תהליך הבדיקה"),
    HANG_TIMEOUT(Severity.CRASHER, "הפענוח של הקובץ נתקע (לא הסתיים בזמן)"),
    SLOW_DECODE(Severity.SUSPECT, "פענוח איטי מאוד (יותר מ-5 שניות)"),
    WORKER_DIED_ONCE(Severity.SUSPECT, "תהליך הבדיקה קרס פעם אחת על הקובץ הזה"),

    UNSUPPORTED_FORMAT(Severity.INFO, "הפורמט לא נתמך במכשיר הזה, פענוח לא נבדק");

    companion object {
        fun parseList(csv: String?): List<Reason> =
            if (csv.isNullOrEmpty()) emptyList()
            else csv.split(',').mapNotNull { n -> entries.firstOrNull { it.name == n } }

        fun toCsv(list: Collection<Reason>): String = list.joinToString(",") { it.name }
    }
}

object Classifier {
    /** Folds a set of reasons into one status. Ghost rows and crashers override everything. */
    fun statusOf(reasons: Collection<Reason>): Status {
        if (Reason.GHOST_ROW in reasons) return Status.GHOST
        return when (reasons.maxOfOrNull { it.severity }) {
            Severity.CRASHER -> Status.CRASHER
            Severity.BROKEN -> Status.BROKEN
            Severity.SUSPECT -> Status.SUSPECT
            else -> Status.OK
        }
    }
}

/** What the isolated worker process reports back for one file. */
data class FileResult(
    val reasons: List<Reason> = emptyList(),
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Long = -1,
    val fps: Float = 0f,
    val bitrate: Long = 0,
    val ioErrors: Int = 0,
    val readMbps: Float = 0f,
    val probed: Boolean = false,
    val decoded: Boolean = false,
    val decodeMs: Int = 0,
)

/** One unit of work for the coordinator. */
data class WorkItem(val pk: Long, val attempts: Int)

enum class Confidence(val he: String) { HIGH("גבוהה"), MEDIUM("בינונית"), LOW("נמוכה") }
enum class Light(val he: String) { RED("אדום"), YELLOW("צהוב"), GREEN("ירוק") }
