package il.gallerydoctor.core

/**
 * Turns measurements into a ranked list of likely causes. Every hypothesis carries its own evidence
 * and actions; the score (0-100) is an evidence-strength estimate, not a probability.
 */
object Analyzer {
    private const val BASELINE_ID = "CACHE"

    // Canonical, reused action texts. Keys are used to merge duplicates across hypotheses.
    private const val K_BACKUP = "BACKUP"
    private const val K_CLEAR_MEDIA = "CLEAR_MEDIA_STORAGE"
    private const val K_CLEAR_GALLERY = "CLEAR_GALLERY_CACHE"
    private const val K_UPDATE = "UPDATE_GALLERY"
    private const val K_RESTART = "RESTART"
    private const val K_RESCAN = "RESCAN"

    private fun clearMediaStorage(why: String) = ActionStep(
        K_CLEAR_MEDIA,
        "נקו את המטמון והנתונים של \"אחסון מדיה\" (Media Storage): הגדרות ← אפליקציות ← הצג את כל האפליקציות " +
            "(או \"אפליקציות מערכת\") ← אחסון מדיה ← אחסון ← \"נקה מטמון\" ואז \"נקה נתונים\". " +
            "התמונות והסרטונים לא נמחקים – רק רשימת האינדקס נבנית מחדש, וזה יכול לקחת כמה דקות.",
        why,
    )

    private fun clearGalleryCache(why: String) = ActionStep(
        K_CLEAR_GALLERY,
        "נקו את המטמון של אפליקציית הגלריה: הגדרות ← אפליקציות ← הגלריה שלכם ← אחסון ← \"נקה מטמון\". " +
            "אל תבחרו \"נקה נתונים\" לפני שוידאתם שיש גיבוי לתמונות.",
        why,
    )

    private fun backup(why: String) = ActionStep(
        K_BACKUP,
        "לפני כל פעולה אחרת: גבו את התמונות והסרטונים החשובים (למחשב, לכונן חיצוני או לענן).",
        why,
    )

    fun analyze(i: ReportInput): Analysis {
        val found = listOfNotNull(
            decoderCrash(i), storageFailing(i), storageFull(i), unstableIndex(i), ghostRows(i),
            incompleteFiles(i), orphans(i), brokenFiles(i), heavyFolders(i), stressFiles(i),
        )
        val strongest = found.maxOfOrNull { it.score } ?: 0
        val baseline = baseline(i, if (strongest >= 50) 30 else 45)
        val ranked = (found + baseline).sortedByDescending { it.score }

        val top = ranked.first()
        val light = when {
            found.any { it.score >= 80 } -> Light.RED
            found.any { it.score >= 50 } -> Light.YELLOW
            else -> Light.GREEN
        }
        val verdict = when (light) {
            Light.RED -> "נמצאה סיבה סבירה מאוד לקריסות הגלריה: ${top.title}."
            Light.YELLOW -> "נמצאו בעיות שיכולות לגרום לטעינה חוזרת ולקריסות, אבל אין ודאות מה הסיבה העיקרית. החשודה ביותר: ${top.title}."
            Light.GREEN -> "לא נמצאה בעיה ברורה בקבצים או באחסון. כנראה שהתקלה באפליקציית הגלריה או באינדקס המדיה עצמם, ולכן מומלץ לנקות מטמון ולעדכן."
        }.let { if (i.scanComplete) it else "$it (הסריקה לא הושלמה, התוצאות חלקיות.)" }

        return Analysis(light, verdict, ranked, mergeActions(ranked, light), limits(i))
    }

    // ---------------- hypotheses ----------------

    private fun decoderCrash(i: ReportInput): Hypothesis? {
        if (i.crasherCount == 0) return null
        val names = i.crashers.map { it.name }
        val hangs = i.crashers.count { Reason.HANG_TIMEOUT in it.reasons }
        val crashes = i.crashers.count { Reason.DECODER_CRASH in it.reasons }
        val evidence = buildList {
            add("${He.files(i.crasherCount)} גרמו למפענח התמונות או הסרטונים לקרוס או להיתקע בזמן הבדיקה: ${He.names(names, i.crasherCount)}.")
            if (crashes > 0) add("בבדיקה, התהליך שמפענח את הקובץ נסגר בפתאומיות (קריסה). מפענח אנדרואיד שקורס כך הוא בדיוק מה שמפיל גלריה כשהיא מנסה להציג את הקובץ.")
            if (hangs > 0) add("ב-${He.files(hangs)} הפענוח לא הסתיים גם אחרי ניסיון שני. ייתכן שהאחסון איטי מאוד ולא שהקובץ פגום, לכן כדאי לנסות לפתוח אותם ידנית בכפתור \"פתח\".")
            add("הקבצים האלה הם החשודים העיקריים: גלריה שמנסה להציג אותם, אפילו כתמונה ממוזערת, נופלת, ובפעם הבאה מתחילה לטעון מחדש.")
        }
        return Hypothesis(
            "DECODER_CRASH", "קבצים שגורמים למפענח לקרוס או להיתקע",
            minOf(98, 90 + i.crasherCount), evidence,
            listOf(
                backup("הקבצים החשודים הם הסיבה הסבירה ביותר, וגיבוי מבטיח שלא תאבדו אותם."),
                ActionStep(
                    "DELETE_CRASHERS",
                    "נסו לפתוח כל קובץ מהטבלה \"קבצים שקרסו\" בכפתור \"פתח\". קובץ שלא נפתח או שהטלפון נתקע בו – העבירו למחשב ומחקו אותו מהטלפון.",
                    "בבדיקה ${He.files(i.crasherCount)} קרסו או נתקעו את המפענח, והסרת הקובץ הבעייתי מונעת מהגלריה ליפול עליו שוב.",
                ),
                clearGalleryCache("אחרי הסרת הקבצים החשודים הגלריה צריכה לבנות מחדש את התמונות הממוזערות שלה."),
            ),
        )
    }

    private fun storageFailing(i: ReportInput): Hypothesis? {
        val ioRate = if (i.totalFiles > 0) i.ioErrors * 100.0 / i.totalFiles else 0.0
        val slowShare = if (i.probedFiles > 0) i.slowReadCount * 100.0 / i.probedFiles else 0.0
        val signals = i.intermittentCount > 0 || i.ioErrors >= 3 || ioRate >= 0.5 || (i.probedFiles >= 20 && slowShare >= 20)
        if (!signals) return null

        val score = when {
            i.intermittentCount > 0 -> 88
            i.ioErrors >= 5 || ioRate >= 0.5 -> 82
            else -> 55
        }
        val evidence = buildList {
            if (i.ioErrors > 0) add("הייתה שגיאת קריאה (IOException) ב-${He.files(i.ioErrors)} מתוך ${He.num(i.totalFiles)} שנבדקו.")
            if (i.intermittentCount > 0) add("${He.files(i.intermittentCount)} נקראו אחרת בקריאה שנייה, או נכשלו רק פעם אחת. אחסון תקין תמיד מחזיר אותו תוכן.")
            if (i.probedFiles >= 20 && slowShare >= 20) {
                val med = i.medianMbps?.let { " (חציון ${"%.1f".format(java.util.Locale.US, it)} MB/s)" } ?: ""
                add("${He.files(i.slowReadCount)} מתוך ${He.num(i.probedFiles)} שנבדקו נקראו לאט במיוחד$med.")
            }
            if (i.failingVolumes.isNotEmpty()) add("הבעיות מרוכזות באחסון: ${i.failingVolumes.joinToString(", ")}.")
            add("הסימנים האלה מצביעים על אפשרות לאחסון פגום או לכרטיס SD תקול.")
        }
        val removable = i.failingVolumes.isNotEmpty() && i.volumes.any { it.removable && it.label in i.failingVolumes } ||
            (i.failingVolumes.isEmpty() && i.volumes.any { it.removable })
        val actions = buildList {
            add(backup("קריאה לא אמינה מהאחסון היא סימן שהתמונות עצמן בסיכון."))
            if (removable) add(
                ActionStep(
                    "RESEAT_SD",
                    "כבו את הטלפון, הוציאו את כרטיס ה-SD, נקו בעדינות את המגעים והכניסו אותו מחדש. אם הבעיה נמשכת – העבירו את הקבצים למחשב ובדקו או החליפו את הכרטיס.",
                    "שגיאות קריאה חוזרות ונשנות הן סימן קלאסי לכרטיס SD תקול או לא יושב טוב.",
                ),
            ) else add(
                ActionStep(
                    "SERVICE",
                    "אם השגיאות בזיכרון הפנימי של הטלפון – גבו הכול ופנו ליצרן או לשירות לבדיקת הזיכרון.",
                    "שגיאות קריאה בזיכרון פנימי מצביעות על תקלה בחומרה שלא נפתרת בתוכנה.",
                ),
            )
        }
        return Hypothesis("STORAGE_FAILING", "אחסון פגום או כרטיס זיכרון (SD) לא אמין", score, evidence, actions)
    }

    private fun storageFull(i: ReportInput): Hypothesis? {
        val critical = i.volumes.filter { it.critical }
        if (critical.isEmpty()) return null
        val worst = critical.maxOf { it.usedPercent }
        val evidence = buildList {
            for (v in critical) add("באחסון \"${v.label}\" נותרו ${He.bytes(v.freeBytes)} פנויים מתוך ${He.bytes(v.totalBytes)} (${v.usedPercent}% תפוס).")
            add("כשהאחסון כמעט מלא, מסד הנתונים של המדיה והתמונות הממוזערות לא יכולים להישמר. הגלריה מנסה שוב ושוב לבנות אותם, ונראה שהיא \"נטענת מחדש\".")
            if (i.imageBytes + i.videoBytes > 0) add("תמונות תופסות ${He.bytes(i.imageBytes)} וסרטונים ${He.bytes(i.videoBytes)}.")
        }
        val actions = buildList {
            add(
                ActionStep(
                    "FREE_SPACE",
                    "פנו לפחות 2GB: העבירו סרטונים גדולים למחשב או לענן ומחקו אותם מהטלפון." +
                        (i.topFolders.firstOrNull()?.let { " התיקייה הגדולה ביותר היא \"${it.path}\" (${He.bytes(it.bytes)})." } ?: ""),
                    "האחסון תפוס ב-$worst%, וזה מונע מהגלריה לשמור את האינדקס והתמונות הממוזערות.",
                ),
            )
            if (i.reclaimableBytes > 0) add(
                ActionStep(
                    "DEDUP",
                    "מחקו כפילויות לפי טבלת הכפילויות בדוח – בלי למחוק את כל העותקים של אותו קובץ. כך תפנו כ-${He.bytes(i.reclaimableBytes)}.",
                    "נמצאו ${i.dupGroupCount} קבוצות של קבצים זהים, ומחיקת העותקים המיותרים היא הדרך הקלה ביותר לפנות מקום.",
                ),
            )
        }
        return Hypothesis("STORAGE_FULL", "האחסון כמעט מלא", if (worst >= 95) 80 else 66, evidence, actions)
    }

    private fun unstableIndex(i: ReportInput): Hypothesis? {
        val ch = i.indexChanged ?: return null
        val van = i.indexVanished ?: 0
        val app = i.indexAppeared ?: 0
        val total = ch + van + app
        if (total == 0) return null
        val score = when {
            total >= 20 -> 85
            total >= 5 -> 70
            else -> 35
        }
        val evidence = buildList {
            add("בתוך 60 שניות, בין שתי קריאות של אינדקס המדיה, ${He.entries(ch)} השתנו, ${He.entries(van)} נעלמו ו-${He.entries(app)} נוספו.")
            if (total >= 5) add("אינדקס יציב לא משתנה כשאף אחד לא מצלם או מוריד קבצים. שינוי תמידי הוא בדיוק הסימפטום של \"הגלריה נטענת מחדש\": משהו מאנדקס מחדש את הקבצים כל הזמן.")
            else add("מספר קטן כזה יכול להיות תקין אם צילמתם או הורדתם משהו בזמן הסריקה.")
        }
        return Hypothesis(
            "UNSTABLE_INDEX", "אינדקס המדיה לא יציב ומתעדכן כל הזמן", score, evidence,
            listOf(
                clearMediaStorage("אינדקס שמשתנה כל הזמן הוא סימן לאינדקס פגום או ללולאת סריקה, וניקוי הנתונים בונה אותו מחדש."),
                ActionStep(
                    "STOP_BACKGROUND_WRITERS",
                    "בדקו אם אפליקציית גיבוי, סנכרון או ניקוי כותבת או מוחקת קבצים ברקע, ועצרו אותה לזמן מה כדי לראות אם הטעינה החוזרת נפסקת.",
                    "אפליקציה שכותבת קבצים כל הזמן גורמת לאינדקס להשתנות בלי הפסקה.",
                ),
            ),
        )
    }

    private fun ghostRows(i: ReportInput): Hypothesis? {
        if (i.ghostCount == 0) return null
        val share = if (i.totalFiles > 0) i.ghostCount * 100.0 / i.totalFiles else 0.0
        val score = when {
            i.ghostCount >= 100 || share >= 2.0 -> 76
            i.ghostCount >= 20 -> 66
            i.ghostCount >= 5 -> 52
            else -> 30
        }
        val evidence = listOf(
            "${He.entries(i.ghostCount)} באינדקס המדיה מצביעות על קבצים שכבר לא קיימים${i.ghostSamples.takeIf { it.isNotEmpty() }?.let { " (למשל ${He.names(it.map { r -> r.name }, i.ghostCount)})" } ?: ""}.",
            "גלריה שמנסה לפתוח רשומה כזאת מקבלת שגיאה. זה גורם לקבצים להופיע ולהיעלם, ולפעמים לקריסה.",
        )
        return Hypothesis(
            "GHOST_ROWS", "רשומות \"רפאים\" באינדקס המדיה", score, evidence,
            listOf(clearMediaStorage("ניקוי הנתונים מוחק את האינדקס הישן ובונה אותו מחדש לפי הקבצים שבאמת קיימים, כך ש-${He.entries(i.ghostCount)} הרפאים נעלמות.")),
        )
    }

    private fun incompleteFiles(i: ReportInput): Hypothesis? {
        if (i.pendingStuckCount == 0 && i.leftoverTempCount == 0) return null
        val evidence = buildList {
            if (i.pendingStuckCount > 0) add("${He.entries(i.pendingStuckCount)} תקועות במצב \"בכתיבה\" (IS_PENDING) יותר מ-24 שעות${i.pendingSamples.takeIf { it.isNotEmpty() }?.let { " (למשל ${He.names(it, i.pendingStuckCount)})" } ?: ""}.")
            if (i.leftoverTempCount > 0) add("נמצאו ${He.files(i.leftoverTempCount)} זמניים ישנים (.pending או .trashed) שלא נמחקו${i.leftoverSamples.takeIf { it.isNotEmpty() }?.let { " (למשל ${He.names(it, i.leftoverTempCount)})" } ?: ""}.")
            add("קבצים כאלה נשארים אחרי כתיבה או מחיקה שנקטעו, ויכולים לבלבל את הגלריה.")
        }
        val score = if (i.pendingStuckCount > 0) 62 else 45
        return Hypothesis(
            "INCOMPLETE_FILES", "קבצים תקועים באמצע כתיבה או מחיקה", score, evidence,
            listOf(
                ActionStep(
                    K_RESTART,
                    "הפעילו מחדש את הטלפון ופתחו את הגלריה. המתינו כמה דקות בזמן שהיא בונה מחדש את הרשימה.",
                    "הפעלה מחדש מאפשרת לאנדרואיד לסיים או לנקות כתיבות שנתקעו.",
                ),
                clearMediaStorage("אם הקבצים התקועים נשארו, ניקוי נתוני אחסון המדיה מנקה את הרשומות התקועות."),
            ),
        )
    }

    private fun orphans(i: ReportInput): Hypothesis? {
        val n = maxOf(i.orphanCount, i.safOnlyCount)
        if (n == 0) return null
        val score = if (n >= 50) 58 else if (n >= 10) 45 else 28
        val evidence = buildList {
            add("${He.files(n)} של מדיה נמצאו בתיקיות אבל לא מופיעים באינדקס${i.orphanSamples.takeIf { it.isNotEmpty() }?.let { " (למשל ${He.names(it, i.orphanCount)})" } ?: ""}.")
            add("קבצים כאלה פשוט לא מוצגים בגלריה, וזה יכול להסביר \"קבצים חסרים\". סריקה חוזרת של האינדקס לפעמים נכשלת או קורסת בגללם.")
        }
        return Hypothesis(
            "ORPHANS", "קבצים שקיימים בטלפון אבל חסרים באינדקס", score, evidence,
            listOf(
                clearMediaStorage("בניית האינדקס מחדש תכניס לאינדקס את ${He.files(n)} שחסרים בו."),
                ActionStep(
                    "CHECK_NOMEDIA",
                    "אם הקבצים עדיין לא מופיעים, בדקו אם בתיקייה שלהם יש קובץ בשם .nomedia (מנהל קבצים עם \"הצג קבצים מוסתרים\"). הוא אוסר על הגלריה להציג את התיקייה.",
                    "קובץ .nomedia הוא הסיבה הנפוצה ביותר לתיקייה שהגלריה מתעלמת ממנה.",
                ),
            ),
        )
    }

    private fun brokenFiles(i: ReportInput): Hypothesis? {
        if (i.brokenCount == 0 && i.suspectCount == 0) return null
        val score = when {
            i.brokenCount >= 10 -> 72
            i.brokenCount >= 1 -> 62
            else -> 45
        }
        val topReasons = i.reasonHistogram.entries.sortedByDescending { it.value }.take(3)
            .joinToString("; ") { "${it.key.he} (${He.num(it.value)})" }
        val evidence = buildList {
            if (i.brokenCount > 0) add("${He.files(i.brokenCount)} פגומים${i.broken.takeIf { it.isNotEmpty() }?.let { " (למשל ${He.names(it.map { r -> r.name }, i.brokenCount)})" } ?: ""}.")
            if (i.suspectCount > 0) add("${He.files(i.suspectCount)} חשודים – חתוכים או פגומים חלקית${i.suspect.takeIf { it.isNotEmpty() }?.let { " (למשל ${He.names(it.map { r -> r.name }, i.suspectCount)})" } ?: ""}.")
            if (topReasons.isNotEmpty()) add("הסיבות הנפוצות: $topReasons.")
            add("גלריה בונה תמונה ממוזערת לכל קובץ. קובץ פגום יכול לעצור את הטעינה או להפיל אותה.")
        }
        return Hypothesis(
            "BROKEN_FILES", "קבצי תמונה וסרטון פגומים או חתוכים", score, evidence,
            listOf(
                backup("מחיקת קבצים פגומים אינה הפיכה."),
                ActionStep(
                    "DELETE_BROKEN",
                    "עברו על הטבלאות \"קבצים פגומים\" ו\"קבצים חשודים\", פתחו כל קובץ בכפתור \"פתח\". קובץ שלא נפתח – גבו ומחקו. קובץ חתוך אפשר לנסות לשחזר מהענן או מהמקור.",
                    "${He.files(i.brokenCount + i.suspectCount)} נמצאו כפגומים, וקבצים כאלה הם בין הגורמים הנפוצים לקריסת גלריה.",
                ),
            ),
        )
    }

    private fun heavyFolders(i: ReportInput): Hypothesis? {
        if (i.heavyFolders.isEmpty()) return null
        val biggest = i.heavyFolders.maxOf { it.count }
        val evidence = listOf(
            "${He.folders(i.heavyFolders.size)} עם יותר מ-${He.num(i.folderThreshold)} קבצים: " +
                i.heavyFolders.take(3).joinToString(", ") { "\"${it.path}\" (${He.num(it.count)})" } + ".",
            "גלריה שטוענת תיקייה עם אלפי קבצים בבת אחת צורכת הרבה זיכרון, ויכולה להיתקע או לקרוס.",
        )
        return Hypothesis(
            "HEAVY_FOLDERS", "תיקיות עם כמות קיצונית של קבצים", if (biggest >= 20000) 62 else 50, evidence,
            listOf(
                ActionStep(
                    "SPLIT_FOLDERS",
                    "העבירו חלק מהקבצים מהתיקיות הגדולות לתיקיות חדשות (למשל לפי שנה), או למחשב.",
                    "התיקייה הגדולה ביותר מכילה ${He.num(biggest)} קבצים, וקשה לגלריה לטעון אותה בבת אחת.",
                ),
            ),
        )
    }

    private fun stressFiles(i: ReportInput): Hypothesis? {
        if (i.anomalyCounts.isEmpty()) return null
        val severe = listOf(Anomaly.IMAGE_OVER_50MP, Anomaly.IMAGE_SIDE_OVER_16384, Anomaly.VIDEO_8K, Anomaly.VIDEO_OVER_2GB, Anomaly.VIDEO_ABSURD_DURATION)
            .sumOf { i.anomalyCounts[it] ?: 0 }
        val total = i.anomalyCounts.values.sum()
        val breakdown = i.anomalyCounts.entries.sortedByDescending { it.value }.take(4)
            .joinToString("; ") { "${it.key.he} (${He.num(it.value)})" }
        val evidence = listOf(
            "נמצאו קבצים חריגים בגודל או ברזולוציה: $breakdown.",
            "קבצים כאלה ידועים כמעמיסים על המפענח של הגלריה, במיוחד בטלפונים עם מעט זיכרון.",
        )
        return Hypothesis(
            "STRESS_FILES", "קבצים כבדים או חריגים שמעמיסים על הגלריה",
            if (severe > 0) 55 else 38, evidence,
            listOf(
                ActionStep(
                    "CHECK_HEAVY",
                    "פתחו את הקבצים מהטבלה \"קבצים חריגים\" ובדקו אם הגלריה איטית או נתקעת בהם. את הכבדים ביותר כדאי להעביר למחשב.",
                    "${He.files(total)} חורגים מהמידות שמפענחי אנדרואיד מתמודדים איתן בקלות.",
                ),
            ),
        )
    }

    private fun baseline(i: ReportInput, score: Int): Hypothesis {
        val gallery = i.apps.filter { it.role == AppRole.GALLERY }
        val provider = i.apps.firstOrNull { it.role == AppRole.MEDIA_PROVIDER }
        val evidence = buildList {
            add("האפליקציה הזאת לא יכולה לראות את המטמון של אחסון המדיה ושל הגלריה, לכן אי אפשר לבדוק אותם ישירות. זו הסיבה הנפוצה ביותר לטעינה חוזרת, והתיקון זול ובטוח.")
            if (gallery.isNotEmpty()) add("אפליקציות גלריה שזוהו: " + gallery.joinToString(", ") { "${it.label} (${it.pkg}${it.version?.let { v -> ", גרסה $v" } ?: ""})" } + ".")
            if (gallery.size > 1) add("יש יותר מאפליקציית גלריה אחת. כשכמה אפליקציות מאנדקסות ומציגות את אותם קבצים, קורות התנגשויות.")
            if (provider != null) add("אחסון מדיה: ${provider.label}${provider.version?.let { " (גרסה $it)" } ?: ""}.")
            if (score >= 45) add("מכיוון שהבדיקות לא מצאו בעיה חזקה אחרת, זה המקום הטוב ביותר להתחיל בו.")
        }
        return Hypothesis(
            BASELINE_ID, "מטמון פגום של אחסון המדיה או של אפליקציית הגלריה", score, evidence,
            listOf(
                clearGalleryCache("מטמון תמונות ממוזערות פגום גורם לגלריה להיתקע ולבנות אותן שוב ושוב."),
                clearMediaStorage("אינדקס המדיה של אנדרואיד נשמר במקום נפרד מהגלריה, ופגם בו גורם לקבצים להופיע ולהיעלם."),
                ActionStep(
                    K_UPDATE,
                    "עדכנו את אפליקציית הגלריה ואת \"Google Play services\" מחנות Play, ואז בדקו אם יש עדכון מערכת בהגדרות.",
                    "קריסות רבות של גלריות מתוקנות בעדכוני גרסה של האפליקציה או של רכיבי המערכת.",
                ),
            ),
        )
    }

    // ---------------- actions & limits ----------------

    private fun mergeActions(ranked: List<Hypothesis>, light: Light): List<ActionStep> {
        val byKey = LinkedHashMap<String, ActionStep>()
        // BACKUP always goes first when anything destructive is recommended
        for (h in ranked) for (a in h.actions) {
            val prev = byKey[a.key]
            byKey[a.key] = if (prev == null) a else if (a.why in prev.why) prev else prev.copy(why = prev.why + " " + a.why)
        }
        val ordered = ArrayList<ActionStep>()
        byKey.remove(K_BACKUP)?.let { ordered += it }
        ordered += byKey.values
        ordered += ActionStep(
            K_RESTART_FINAL,
            "בסיום: הפעילו מחדש את הטלפון, פתחו את הגלריה והמתינו כמה דקות בזמן שהיא בונה מחדש את הרשימה.",
            "הפעלה מחדש טוענת את האינדקס והמטמון הנקיים.",
        )
        ordered += ActionStep(
            K_RESCAN,
            if (light == Light.GREEN) "אם הבעיה חוזרת, הריצו סריקה נוספת מיד אחרי שהגלריה קורסת, כדי לתפוס את מה שהשתנה."
            else "הריצו סריקה נוספת בעוד יום או יומיים כדי לוודא שהבעיה נפתרה.",
            "הסריקה היא לקריאה בלבד ובטוחה, והשוואה בין סריקות מראה אם התיקון עבד.",
        )
        return ordered
    }

    private const val K_RESTART_FINAL = "RESTART_FINAL"

    private fun limits(i: ReportInput): List<String> = buildList {
        add("האפליקציה לא יכולה לקרוא את יומני הקריסה של אפליקציית הגלריה, כי אנדרואיד חוסם גישה למידע של אפליקציות אחרות. הדירוג בדוח מבוסס על ראיות שנמצאו בקבצים, באינדקס ובאחסון, ולא על מה שהגלריה עצמה דיווחה.")
        add("רמת הביטחון מציינת עד כמה הראיות חזקות. היא לא ודאות מוחלטת: ייתכן שהסיבה האמיתית אינה ברשימה.")
        add("האפליקציה פועלת בקריאה בלבד. היא לא מוחקת, לא מזיזה ולא משנה אף קובץ. כל פעולה בדוח היא המלצה שאתם מבצעים בעצמכם.")
        if (!i.scanComplete) add("הסריקה הופסקה לפני שהסתיימה, לכן חלק מהקבצים לא נבדקו והתוצאות חלקיות.")
        if (!i.deepScan) add("בוצעה סריקה מהירה בלי בדיקת פענוח, ולכן לא ניתן היה לזהות קבצים שקורסים את המפענח.")
        if (i.dirsInaccessible > 0) add("${He.folders(i.dirsInaccessible)} לא היו נגישות לאפליקציה, ולכן חיפוש הקבצים שחסרים באינדקס לא כיסה אותן.")
        add("הבדיקה מחפשת קבצים שחסרים באינדקס רק בתיקיות המדיה הרגילות (DCIM, Pictures, Movies, Download, WhatsApp, Telegram) ובתיקייה שנבחרה, ולא בכל האחסון.")
    }
}
