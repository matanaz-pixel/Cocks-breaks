package il.gallerydoctor.core

import java.util.Locale

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
            galleryCrash(i), devSettings(i), volumeProblems(i), providerDisabled(i), galleryAppState(i),
            suspectApps(i), fileNames(i), lowResources(i), systemOutdated(i), xiaomiBackground(i),
            indexRebuilds(i), recorderEvents(i), indexShrank(i), nomediaHidden(i), hiddenFolders(i),
        ).let { it + listOfNotNull(galleryPrivateDb(i, it)) }
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
            if (i.ownExits.nativeCrashes > 0) {
                val sig = i.ownExits.signals.entries.sortedByDescending { it.value }.joinToString(", ") { "${it.key} (${it.value})" }
                add("אנדרואיד רשם ${He.num(i.ownExits.nativeCrashes)} קריסות נייטיב בתהליכי הבדיקה${if (sig.isNotEmpty()) ": $sig" else ""}. זו תקלה בקוד המפענח של המערכת עצמה, שמופעלת על ידי הקובץ.")
            }
            if (i.ownExits.lowMemoryKills > 0) add("${He.num(i.ownExits.lowMemoryKills)} מהאתחולים נגרמו מחוסר זיכרון בטלפון ולא מקובץ פגום, לכן ייתכן שחלק מהקבצים בטבלה נבדקו בזמן לחץ זיכרון.")
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
            if (i.videoBytes > i.imageBytes * 2 && i.videoBytes > 0) add("רוב המקום (${i.videoBytes * 100 / (i.imageBytes + i.videoBytes)}%) תפוס על ידי סרטונים, ולכן שם הכי קל לפנות.")
            val viaAlbums = i.dupGroups.count { g -> g.files.any { it.folder.startsWith("Pictures/Gallery/owner") } }
            if (i.dupGroups.isNotEmpty() && viaAlbums * 2 >= i.dupGroups.size) add("רוב הכפילויות הן עותקים של תמונות המצלמה בתוך Pictures/Gallery/owner, תיקייה שנראית כאלבומים של גלריית Xiaomi. אלה עותקים נפרדים, והם תופסים מקום פעמיים.")
        }
        val target = maxOf(2L * 1024 * 1024 * 1024, critical.maxOf { it.totalBytes } / 20)
        val actions = buildList {
            if (i.apps.any { it.pkg == "com.google.android.apps.photos" && it.enabled }) add(
                ActionStep(
                    "PHOTOS_FREE_UP",
                    "הדרך הבטוחה ביותר לפנות מקום: ב-Google Photos לחצו על תמונת הפרופיל ← \"פינוי מקום\" (Free up space), אחרי שוידאתם שהגיבוי הסתיים. זה מוחק מהטלפון רק עותקים שכבר גובו לענן.",
                    "יש בטלפון Google Photos, והיא מפנה בבטחה תמונות וסרטונים שכבר גובו, בלי למחוק אותם לגמרי.",
                ),
            )
            add(
                ActionStep(
                    "FREE_SPACE",
                    "פנו לפחות ${He.bytes(target)} (כ-5% מהאחסון): העבירו סרטונים גדולים למחשב או לענן ומחקו אותם מהטלפון." +
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
        val owners = i.churnOwners.sortedByDescending { it.count }
        val ownerTotal = owners.sumOf { it.count }
        val top = owners.firstOrNull()
        val topShare = if (top != null && ownerTotal > 0) top.count * 100 / ownerTotal else 0
        val evidence2 = if (owners.isEmpty() || total < 5) evidence else evidence + "הרשומות ששונו נכתבו על ידי: " +
            owners.take(4).joinToString(", ") { "${it.label} (${He.num(it.count)})" } + "."
        val score2 = if (top != null && total >= 5 && topShare >= 60) minOf(92, score + 10) else score
        val writerStep = if (top != null && total >= 5 && topShare >= 60) listOf(
            ActionStep(
                "STOP_TOP_WRITER",
                "עצרו זמנית את \"${top.label}\": הגדרות ← אפליקציות ← ${top.label} ← עצירה בכוח, ובדקו אם הגלריה מפסיקה להיטען מחדש. אם כן, זו האפליקציה שגורמת לבעיה: בדקו את ההגדרות שלה (גיבוי, סנכרון, ניקוי) או הסירו אותה.",
                "$topShare% מהשינויים באינדקס בדקה הזאת נכתבו על ידי האפליקציה הזאת.",
            ),
        ) else emptyList()
        return Hypothesis(
            "UNSTABLE_INDEX", "אינדקס המדיה לא יציב ומתעדכן כל הזמן", score2, evidence2,
            writerStep + listOf(
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
        // A wrong extension alone is harmless: Android decodes by content, not by name.
        val harmless = i.reasonHistogram[Reason.MAGIC_MISMATCH] ?: 0
        val realSuspect = maxOf(0, i.suspectCount - harmless)
        if (i.brokenCount == 0 && realSuspect == 0) {
            if (harmless == 0) return null
            return Hypothesis(
                "EXTENSION_MISMATCH", "קבצים עם סיומת שלא תואמת לתוכן", 22,
                listOf(
                    "${He.files(harmless)} נקראים בסיומת אחת אבל הם בפועל פורמט אחר, למשל WebP או PNG בשם jpg${i.suspect.firstOrNull { Reason.MAGIC_MISMATCH in it.reasons }?.let { " (למשל ${it.name})" } ?: ""}.",
                    "אנדרואיד מזהה תמונה לפי התוכן ולא לפי השם, ולכן זה כמעט תמיד לא מזיק. בדרך כלל זה קורה בתמונות שהורדו מ-Instagram או מאתרים.",
                ),
                emptyList(),
            )
        }
        val score = when {
            i.brokenCount >= 10 -> 72
            i.brokenCount >= 1 -> 62
            else -> 45
        }
        val topReasons = i.reasonHistogram.entries.sortedByDescending { it.value }.take(3)
            .joinToString("; ") { "${it.key.he} (${He.num(it.value)})" }
        val evidence = buildList {
            if (i.brokenCount > 0) add("${He.files(i.brokenCount)} פגומים${i.broken.takeIf { it.isNotEmpty() }?.let { " (למשל ${He.names(it.map { r -> r.name }, i.brokenCount)})" } ?: ""}.")
            if (realSuspect > 0) add("${He.files(realSuspect)} חשודים – חתוכים או פגומים חלקית${i.suspect.filter { it.reasons.any { r -> r != Reason.MAGIC_MISMATCH } }.takeIf { it.isNotEmpty() }?.let { " (למשל ${He.names(it.map { r -> r.name }, realSuspect)})" } ?: ""}.")
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
                    "${He.files(i.brokenCount + realSuspect)} נמצאו כפגומים, וקבצים כאלה הם בין הגורמים הנפוצים לקריסת גלריה.",
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
        val camera = i.heavyFolders.any { it.path.startsWith("DCIM") }
        return Hypothesis(
            "HEAVY_FOLDERS", "תיקיות עם כמות קיצונית של קבצים", if (biggest >= 20000) 62 else 50, evidence,
            listOf(
                if (camera) ActionStep(
                    "TRIM_CAMERA",
                    "תיקיית המצלמה לא ניתנת לפיצול, אבל אפשר להקטין אותה: גבו את התמונות והסרטונים הישנים (למשל ב-Google Photos) והסירו אותם מהטלפון. ב-Google Photos זה \"פינוי מקום\".",
                    "תיקיית המצלמה מכילה ${He.num(biggest)} קבצים, וגלריה שטוענת כל כך הרבה קבצים בבת אחת נתקעת או קורסת.",
                ) else ActionStep(
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
            if (!i.crashLog.permissionGranted) add("כדי לראות את הקריסה האמיתית של הגלריה, חברו את הטלפון למחשב והריצו פעם אחת: adb shell pm grant il.gallerydoctor android.permission.READ_LOGS, ואז הריצו סריקה מיד אחרי שהגלריה קורסת.")
            else if (i.crashLog.readOk && i.crashLog.related.isEmpty()) add("יומן הקריסות של המערכת נקרא ולא נמצאה בו קריסה של הגלריה. היומן שומר רק את הקריסות האחרונות, ולכן כדאי להריץ סריקה מיד אחרי שהגלריה קורסת.")
            if (score >= 45) add("מכיוון שהבדיקות לא מצאו בעיה חזקה אחרת, זה המקום הטוב ביותר להתחיל בו.")
        }
        return Hypothesis(
            BASELINE_ID, "מטמון פגום של אחסון המדיה או של אפליקציית הגלריה", score, evidence,
            listOf(
                clearGalleryCache("מטמון תמונות ממוזערות פגום גורם לגלריה להיתקע ולבנות אותן שוב ושוב."),
                clearMediaStorage("אינדקס המדיה של אנדרואיד נשמר במקום נפרד מהגלריה, ופגם בו גורם לקבצים להופיע ולהיעלם."),
                updateGallery("קריסות רבות של גלריות מתוקנות בעדכוני גרסה של האפליקציה או של רכיבי המערכת."),
            ),
        )
    }

    // ---------------- environment: crash log, apps, phone ----------------

    private fun label(i: ReportInput, pkg: String) = i.apps.firstOrNull { it.pkg == pkg }?.label ?: pkg

    private fun updateGallery(why: String) = ActionStep(
        K_UPDATE,
        "עדכנו את אפליקציית הגלריה ואת \"Google Play services\" מחנות Play, ואז בדקו אם יש עדכון מערכת בהגדרות.",
        why,
    )

    private fun clearGalleryData(why: String) = ActionStep(
        "CLEAR_GALLERY_DATA",
        "אם הקריסה נמשכת גם אחרי ניקוי המטמון, נקו גם את הנתונים של אפליקציית הגלריה: הגדרות ← אפליקציות ← הגלריה ← אחסון ← \"נקה נתונים\". הגלריה תתאפס (התמונות לא נמחקות, אבל הגדרות ואלבומים שנשמרו רק בתוך האפליקציה נמחקים), ולכן עשו זאת רק אחרי גיבוי.",
        why,
    )

    private fun freeRam(why: String) = ActionStep(
        "FREE_RAM",
        "סגרו את האפליקציות שרצות ברקע (מסך האפליקציות האחרונות ← סגור הכול) והפעילו מחדש את הטלפון. אחרי ההפעלה, פתחו את הגלריה לפני שאר האפליקציות.",
        why,
    )

    private fun galleryCrash(i: ReportInput): Hypothesis? {
        val entries = i.crashLog.related
        if (entries.isEmpty()) return null
        val insights = entries.map { CrashInsights.classify(it) }.distinct()
        val names = entries.map { label(i, it.pkg) }.distinct()
        val last = entries.last()
        val evidence = buildList {
            add("ביומן הקריסות של אנדרואיד נמצאו ${He.num(entries.size)} קריסות של ${names.joinToString(", ")}. האחרונה: ${last.headline} (${last.time}).")
            if (last.frames.isNotEmpty()) add("היא קרסה ב: ${last.frames.take(3).joinToString(" ← ")}.")
            last.rootCause?.let { add("הסיבה העמוקה לפי היומן: $it.") }
            for (ins in insights) add(
                when (ins) {
                    CrashInsight.OUT_OF_MEMORY -> "הגלריה נגמר לה הזיכרון (OutOfMemoryError). זה קורה כשהיא טוענת תמונות או סרטונים גדולים מדי, או כשהטלפון עמוס."
                    CrashInsight.DATABASE -> "הקריסה קשורה לבסיס נתונים (SQLite). בסיס נתונים פגום של הגלריה או של אחסון המדיה גורם לקריסה בכל פתיחה."
                    CrashInsight.MISSING_FILE -> "הגלריה ניסתה לפתוח קובץ שלא קיים. זה קורה כשיש באינדקס רשומות רפאים."
                    CrashInsight.PERMISSION -> "הגלריה נתקלה בבעיית הרשאות לקבצים."
                    CrashInsight.NATIVE_DECODER -> "הקריסה התרחשה בקוד נייטיב של מפענח המערכת, בדרך כלל בגלל קובץ תמונה או סרטון מסוים."
                    CrashInsight.OTHER -> "הקריסה לא מתאימה לאחת מהסיבות הנפוצות. הפרטים המלאים בסעיף יומן הקריסות."
                },
            )
            add("זה הנתון הישיר ביותר בדוח: זו הקריסה האמיתית של הגלריה ולא הערכה.")
        }
        val actions = ArrayList<ActionStep>()
        for (ins in insights) when (ins) {
            CrashInsight.OUT_OF_MEMORY -> {
                actions += freeRam("היומן מראה שהגלריה קרסה מחוסר זיכרון.")
                actions += ActionStep("CHECK_HEAVY", "פתחו את הקבצים מהטבלה \"קבצים חריגים\" ובדקו אם הגלריה נתקעת בהם. את הכבדים ביותר כדאי להעביר למחשב.", "קבצים כבדים הם הסיבה הנפוצה לחוסר זיכרון בגלריה.")
            }
            CrashInsight.DATABASE -> {
                actions += clearMediaStorage("היומן מצביע על בסיס נתונים פגום, וניקוי הנתונים יוצר אחד חדש.")
                actions += clearGalleryData("אם בסיס הנתונים הפגום הוא של הגלריה עצמה, רק ניקוי הנתונים שלה יתקן אותו.")
            }
            CrashInsight.MISSING_FILE -> actions += clearMediaStorage("הגלריה קרסה על קובץ שלא קיים, וניקוי האינדקס מסיר את הרשומות שלהם.")
            CrashInsight.PERMISSION -> actions += ActionStep(
                "GRANT_MEDIA_PERMISSION",
                "ודאו שהגלריה מורשית לראות את כל התמונות והסרטונים: הגדרות ← אפליקציות ← הגלריה ← הרשאות ← תמונות וסרטונים ← \"אפשר גישה לכולם\".",
                "היומן מראה שהקריסה קשורה בהרשאות.",
            )
            CrashInsight.NATIVE_DECODER -> actions += ActionStep(
                "CHECK_CRASHERS",
                "פתחו את הטבלה \"קבצים שקרסו או נתקעו\" ובדקו כל קובץ בכפתור \"פתח\". קובץ שמקריס את הגלריה: גבו אותו ומחקו אותו מהטלפון.",
                "הקריסה בגלריה היא קריסת מפענח, וקובץ מסוים הוא הגורם הסביר.",
            )
            CrashInsight.OTHER -> actions += updateGallery("קריסה שאינה מזוהה נפתרת לעיתים קרובות בעדכון של הגלריה.")
        }
        return Hypothesis("GALLERY_CRASH_LOG", "יומן הקריסות של המערכת מראה קריסה אמיתית של הגלריה", 96, evidence, actions)
    }

    private fun devSettings(i: ReportInput): Hypothesis? {
        val d = i.device ?: return null
        if (!d.alwaysFinishActivities) return null
        return Hypothesis(
            "DONT_KEEP_ACTIVITIES", "ההגדרה \"אל תשמור פעילויות\" פעילה בטלפון", 88,
            listOf(
                "ההגדרה \"אל תשמור פעילויות\" (Don't keep activities) מופעלת באפשרויות המפתח של הטלפון.",
                "כשהיא פעילה, כל אפליקציה, כולל הגלריה, נסגרת ברגע שיוצאים ממנה ונטענת מחדש מההתחלה בכל חזרה. זה נראה בדיוק כמו \"הגלריה נטענת מחדש כל הזמן\".",
            ),
            listOf(
                ActionStep(
                    "DISABLE_DONT_KEEP",
                    "כבו את ההגדרה: הגדרות ← מערכת (או \"אודות הטלפון\") ← אפשרויות מפתח ← \"אל תשמור פעילויות\" ← כבוי.",
                    "ההגדרה פעילה אצלכם עכשיו, והיא גורמת לכל אפליקציה להיטען מחדש בכל פעם שחוזרים אליה.",
                ),
            ),
        )
    }

    private fun volumeProblems(i: ReportInput): Hypothesis? {
        val bad = i.volumeStates.filter { EnvLines.isVolumeProblem(it) }
        if (bad.isEmpty()) return null
        val score = when {
            bad.any { it.state in setOf("unmountable", "bad_removal", "nofs") } -> 82
            bad.any { it.state == "mounted_ro" } -> 62
            else -> 55
        }
        val evidence = buildList {
            for (v in bad) add("האחסון \"${v.label}\" נמצא במצב: ${EnvLines.volumeStateHe(v)}.")
            add("כשכרטיס או אחסון לא יציבים, הקבצים שעליהם מופיעים ונעלמים מהגלריה, והיא מנסה לטעון אותם מחדש שוב ושוב.")
        }
        val actions = if (bad.any { it.removable }) listOf(
            ActionStep(
                "RESEAT_SD",
                "כבו את הטלפון, הוציאו את כרטיס ה-SD, נקו בעדינות את המגעים והכניסו אותו מחדש. אם הבעיה נמשכת – העבירו את הקבצים למחשב ובדקו או החליפו את הכרטיס.",
                "מצב הכרטיס אינו תקין, וזה סימן קלאסי לכרטיס שלא יושב טוב או שנפגע.",
            ),
        ) else listOf(
            ActionStep(K_RESTART, "הפעילו מחדש את הטלפון ובדקו שוב. אם המצב נשאר, גבו הכול ופנו לשירות.", "אחסון פנימי שאינו במצב תקין הוא תקלה חמורה."),
        )
        return Hypothesis("VOLUME_STATE", "כרטיס זיכרון או אחסון במצב לא תקין", score, evidence, actions)
    }

    private fun providerDisabled(i: ReportInput): Hypothesis? {
        val p = i.apps.firstOrNull { it.role == AppRole.MEDIA_PROVIDER && !it.enabled } ?: return null
        return Hypothesis(
            "MEDIA_PROVIDER_DISABLED", "\"אחסון מדיה\" מושבת", 93,
            listOf("הרכיב \"${p.label}\" (${p.pkg}) מושבת. בלעדיו אין אינדקס מדיה, והגלריה לא יכולה לראות תמונות, או שהן מופיעות ונעלמות."),
            listOf(
                ActionStep(
                    "ENABLE_MEDIA_PROVIDER",
                    "הפעילו אותו: הגדרות ← אפליקציות ← הצג את כל האפליקציות ← ${p.label} ← הפעל.",
                    "אחסון המדיה מושבת, ובלעדיו אנדרואיד לא מנהל את אינדקס התמונות והסרטונים.",
                ),
            ),
        )
    }

    private fun galleryAppState(i: ReportInput): Hypothesis? {
        val galleries = i.apps.filter { it.role == AppRole.GALLERY }
        if (galleries.isEmpty()) return null
        val now = if (i.generatedAtMillis > 0) i.generatedAtMillis else System.currentTimeMillis()
        val evidence = ArrayList<String>()
        val actions = ArrayList<ActionStep>()
        var score = 0
        fun hit(s: Int) { score = if (score == 0) s else minOf(80, maxOf(score, s) + 3) }

        for (g in galleries) {
            val days = if (g.updatedAtMillis > 0) (now - g.updatedAtMillis) / 86_400_000 else -1
            if (!g.enabled) {
                evidence += "אפליקציית הגלריה ${g.label} מושבתת."
                actions += ActionStep("ENABLE_GALLERY_${g.pkg}", "הפעילו את ${g.label}: הגדרות ← אפליקציות ← ${g.label} ← הפעל.", "אפליקציית גלריה מושבתת לא יכולה להציג את הקבצים.")
                hit(72)
            }
            if (days in 0..21) {
                evidence += "${g.label} עודכנה לפני ${days} ימים. אם הבעיה התחילה בערך אז, העדכון עצמו חשוד."
                actions += ActionStep(
                    "ROLLBACK_GALLERY",
                    "אם הבעיה התחילה אחרי העדכון, נסו להסיר את העדכונים של הגלריה (הגדרות ← אפליקציות ← ${g.label} ← שלוש הנקודות ← הסר עדכונים, באפליקציות מערכת), או חכו לגרסה חדשה.",
                    "הגלריה עודכנה לאחרונה, ועדכון יכול להכניס תקלה.",
                )
                hit(48)
            }
            if (g.targetSdk in 1..29 && i.androidApi >= 30) {
                evidence += "${g.label} נבנתה לאנדרואיד ישן (גרסת יעד ${g.targetSdk}), בעוד שבטלפון אנדרואיד ${i.androidApi}. אפליקציות כאלה מתקשות עם הגישה החדשה לקבצים."
                actions += updateGallery("הגלריה בנויה לגרסת אנדרואיד ישנה, ועדכון מביא גרסה שמתאימה לאחסון החדש.")
                hit(50)
            }
            if (!g.system && days > 730) {
                evidence += "${g.label} לא עודכנה כבר יותר משנתיים (${days / 30} חודשים)."
                actions += updateGallery("גלריה שלא עודכנה שנים מפספסת תיקוני קריסה.")
                hit(40)
            }
            if (!g.system && (g.installer == null || g.installer.contains("packageinstaller"))) {
                evidence += "${g.label} הותקנה מחוץ לחנות האפליקציות, ולכן אינה מתעדכנת אוטומטית."
                hit(33)
            }
        }
        val enabled = galleries.filter { it.enabled }
        if (enabled.size > 1) {
            val def = i.device?.defaultImageViewer
            evidence += "מותקנות ${enabled.size} אפליקציות גלריה (${enabled.joinToString(", ") { it.label }}). " +
                (if (def != null) "ברירת המחדל לפתיחת תמונות: $def." else "לא נבחרה ברירת מחדל, ואנדרואיד שואל בכל פעם.") +
                " כשכמה אפליקציות מאנדקסות ומציגות את אותם קבצים, קורות התנגשויות."
            actions += ActionStep(
                "CHOOSE_DEFAULT_GALLERY",
                "בחרו אפליקציית גלריה אחת לשימוש יומיומי: הגדרות ← אפליקציות ← אפליקציות ברירת מחדל, ובחרו אותה לתמונות. אם אחת מהן מיותרת, השביתו אותה.",
                "יותר מגלריה אחת מותקנת, וזה מגדיל את הסיכוי להתנגשויות.",
            )
            hit(38)
        }
        if (score == 0) return null
        return Hypothesis("GALLERY_APP_STATE", "בעיה במצב של אפליקציית הגלריה", score, evidence, actions)
    }

    private fun suspectApps(i: ReportInput): Hypothesis? {
        if (i.suspectApps.isEmpty()) return null
        val by = i.suspectApps.groupBy { it.category }
        val hasCleaner = SuspectCategory.CLEANER in by
        val unstable = (i.indexChanged ?: 0) + (i.indexVanished ?: 0) + (i.indexAppeared ?: 0) >= 5
        var score = if (hasCleaner) 38 else 30
        if (unstable || i.ghostCount >= 5) score += if (hasCleaner) 12 else 8
        fun names(c: SuspectCategory) = by[c].orEmpty().take(4).joinToString(", ") { it.label }
        val evidence = buildList {
            by[SuspectCategory.CLEANER]?.let { add("אפליקציות ניקוי או שיפור ביצועים: ${names(SuspectCategory.CLEANER)}. אפליקציות כאלה מוחקות תמונות ממוזערות וקבצי מטמון, ולפעמים קבצי מדיה, בלי שהגלריה יודעת, וכך נוצרות רשומות רפאים.") }
            by[SuspectCategory.BACKUP_SYNC]?.let { add("אפליקציות גיבוי וסנכרון: ${names(SuspectCategory.BACKUP_SYNC)}. הן כותבות ומוחקות קבצים ברקע ומשנות את אינדקס המדיה.") }
            by[SuspectCategory.FILE_MANAGER]?.let { add("מנהלי קבצים: ${names(SuspectCategory.FILE_MANAGER)}. הם מאפשרים להזיז ולמחוק קבצי מדיה בלי לעדכן את האינדקס.") }
            by[SuspectCategory.ALL_FILES_ACCESS]?.let { add("אפליקציות עם גישה לכל הקבצים: ${names(SuspectCategory.ALL_FILES_ACCESS)}. כל אחת מהן יכולה לשנות או למחוק קבצי מדיה בלי אישור נוסף.") }
            if (unstable || i.ghostCount >= 5) add("יש גם סימנים לאינדקס לא יציב או לרשומות רפאים, וזה מתאים לפעילות של אפליקציות כאלה.")
        }
        val top = i.suspectApps.sortedBy { it.category.ordinal }.take(3).joinToString(", ") { it.label }
        return Hypothesis(
            "SUSPECT_APPS", "אפליקציות אחרות שיכולות להפריע לגלריה", score, evidence,
            listOf(
                ActionStep(
                    "TEST_WITHOUT_APPS",
                    "בדיקה פשוטה: עצרו זמנית את $top (הגדרות ← אפליקציות ← האפליקציה ← עצירה בכוח, ובמידת האפשר השביתו אותה), ובדקו אם הגלריה מפסיקה לקרוס או להיטען מחדש. אם כן, מצאתם את הגורם.",
                    "אפליקציות כאלה יכולות לשנות קבצי מדיה ואת האינדקס בלי שהגלריה יודעת.",
                ),
            ),
        )
    }

    private fun fileNames(i: ReportInput): Hypothesis? {
        val n = i.nameIssues
        val d = i.dateIssues
        if (n.count == 0 && d.count == 0) return null
        val severe = n.byKind.containsKey(NameIssue.TOO_LONG.name) || n.byKind.containsKey(NameIssue.BROKEN_ENCODING.name)
        val score = if (severe) 52 else if (n.count + d.count >= 20) 42 else 32
        val evidence = buildList {
            if (n.count > 0) {
                val kinds = n.byKind.entries.sortedByDescending { it.value }.joinToString("; ") { (k, v) ->
                    "${runCatching { NameIssue.valueOf(k).he }.getOrDefault(k)} (${He.num(v)})"
                }
                add("${He.files(n.count)} עם שם חריג: $kinds${n.samples.takeIf { it.isNotEmpty() }?.let { " (למשל ${He.names(it, n.count)})" } ?: ""}.")
            }
            if (d.count > 0) {
                val kinds = d.byKind.entries.sortedByDescending { it.value }.joinToString("; ") { "${it.key} (${He.num(it.value)})" }
                add("${He.files(d.count)} עם תאריך חריג: $kinds.")
            }
            add("גלריות ממיינות לפי שם ולפי תאריך. שם ארוך מדי, תווים פגומים או תאריך בעתיד יכולים לשבש את המיון, ולפעמים להפיל את הגלריה.")
        }
        return Hypothesis(
            "FILE_NAMES", "שמות קבצים או תאריכים חריגים", score, evidence,
            listOf(
                ActionStep(
                    "RENAME_FILES",
                    "שנו את שמות הקבצים החריגים לשם קצר ופשוט. אם אי אפשר בטלפון, העבירו אותם למחשב, שנו שם והחזירו.",
                    "${He.files(n.count + d.count)} עם שם או תאריך שגלריות מתקשות איתם.",
                ),
            ),
        )
    }

    private fun lowResources(i: ReportInput): Hypothesis? {
        val d = i.device ?: return null
        val lowRam = d.ramTotalMb in 1..3300 || d.lowRamDevice
        val lmk = i.ownExits.lowMemoryKills
        if (!lowRam && !d.lowMemoryNow && !d.powerSave && lmk == 0) return null
        val severeAnomalies = listOf(Anomaly.IMAGE_OVER_50MP, Anomaly.IMAGE_SIDE_OVER_16384, Anomaly.VIDEO_8K, Anomaly.VIDEO_OVER_2GB)
            .sumOf { i.anomalyCounts[it] ?: 0 }
        var score = if (lowRam) 30 else 28
        if (lowRam && severeAnomalies > 0) score += 25
        if (d.lowMemoryNow || lmk > 0) score += 30
        val evidence = buildList {
            if (lowRam) add("לטלפון יש ${"%.1f".format(Locale.US, d.ramTotalMb / 1024.0)}GB זיכרון RAM בלבד. זה מעט לגלריה שטוענת תמונות וסרטונים גדולים.")
            if (d.lowMemoryNow) add("הטלפון היה במצוקת זיכרון בזמן הסריקה, פנויים רק ${He.num(d.ramAvailMb)}MB.")
            if (lmk > 0) add("אנדרואיד הרג ${He.num(lmk)} פעמים את תהליכי הבדיקה כדי לפנות זיכרון.")
            if (lowRam && severeAnomalies > 0) add("יש ${He.files(severeAnomalies)} כבדים במיוחד, שהם בדיוק מה שמסיים זיכרון בטלפון כזה.")
            if (d.powerSave) add("חיסכון בסוללה פעיל. הוא עוצר סנכרון ורקע, ויכול לסגור את הגלריה באמצע טעינה.")
        }
        val actions = buildList {
            if (lowRam || d.lowMemoryNow || lmk > 0) add(freeRam("הטלפון מוגבל בזיכרון, ואפליקציות ברקע לוקחות ממנו."))
            if (d.powerSave) add(
                ActionStep(
                    "DISABLE_POWER_SAVE",
                    "כבו לבדיקה את חיסכון הסוללה: הגדרות ← סוללה ← מצב חיסכון ← כבוי, ובדקו שהגלריה לא מוגבלת ב\"אופטימיזציית סוללה\" או ב\"הגבלות רקע\".",
                    "חיסכון בסוללה יכול לעצור או לסגור אפליקציות באמצע עבודה.",
                ),
            )
        }
        return Hypothesis("LOW_RESOURCES", "זיכרון או חיסכון בסוללה מגבילים את הגלריה", score, evidence, actions)
    }

    /**
     * HyperOS / MIUI closes background apps aggressively and has its own cleaner. The app cannot read the per-app
     * battery setting of the gallery, so this is advice for the usual culprit, not a finding.
     */
    private fun xiaomiBackground(i: ReportInput): Hypothesis? {
        val d = i.device ?: return null
        if (!d.isXiaomiFamily) return null
        val storageCritical = i.volumes.any { it.critical }
        val kills = i.ownExits.lowMemoryKills
        val evidence = buildList {
            add("הטלפון הוא ${d.manufacturer} ${d.model}. מערכת HyperOS/MIUI חוסמת ומסגרת אפליקציות ברקע בצורה אגרסיבית כדי לחסוך סוללה, וגלריה שנסגרת ברקע נטענת מחדש מההתחלה כשחוזרים אליה.")
            add("האפליקציה לא יכולה לקרוא את הגדרת הסוללה של הגלריה, לכן זו המלצה לבדיקה ולא ממצא.")
            add("לטלפון יש כלי ניקוי מובנה (Security / Cleaner) שמוחק מטמון ותמונות ממוזערות. הגלריה בונה אותם מחדש, וזה נראה כטעינה חוזרת.")
            if (kills > 0) add("אנדרואיד הרג ${He.num(kills)} פעמים את תהליכי הבדיקה כדי לפנות זיכרון, וזה מתאים להגבלות רקע ולחוסר זיכרון.")
            if (storageCritical) add("האחסון כמעט מלא, והרחבת הזיכרון (Memory extension) של Xiaomi משתמשת באחסון כזיכרון נוסף. כשאין מקום, הטלפון מתקשה לפנות זיכרון.")
        }
        val actions = buildList {
            add(
                ActionStep(
                    "XIAOMI_BATTERY",
                    "הגדרות ← אפליקציות ← ניהול אפליקציות ← אפליקציית הגלריה שלכם ← \"חיסכון בסוללה\" ← \"ללא הגבלות\", והפעילו גם \"הפעלה אוטומטית\". עשו אותו הדבר לאפליקציית \"רופא הגלריה\" לפני סריקה ארוכה.",
                    "במכשירי Xiaomi הגבלת סוללה על הגלריה גורמת לה להיסגר ולהיטען מחדש.",
                ),
            )
            if (storageCritical) add(
                ActionStep(
                    "XIAOMI_RAM_EXTENSION",
                    "חפשו בהגדרות \"הרחבת זיכרון\" (Memory extension) וכבו אותה לבדיקה, עד שתפנו מקום באחסון.",
                    "הרחבת הזיכרון תופסת מקום באחסון, והאחסון מלא.",
                ),
            )
        }
        return Hypothesis("XIAOMI_BACKGROUND", "הגבלות רקע של HyperOS/MIUI סוגרות את הגלריה", if (kills > 0) 55 else 40, evidence, actions)
    }

    private fun systemOutdated(i: ReportInput): Hypothesis? {
        val d = i.device ?: return null
        val now = if (i.generatedAtMillis > 0) i.generatedAtMillis else System.currentTimeMillis()
        val months = d.securityPatch?.let { EnvLines.patchAgeMonths(it, now) }
        val oldPatch = months != null && months >= 18
        val oldAndroid = d.androidApi in 1..28
        if (!oldPatch && !oldAndroid) return null
        val evidence = buildList {
            if (oldPatch) add("עדכון האבטחה האחרון בטלפון הוא מ-${d.securityPatch} (לפני $months חודשים).")
            if (oldAndroid) add("גרסת אנדרואיד ${d.androidRelease} ישנה.")
            add("אחסון המדיה (Media Storage) מתעדכן יחד עם עדכוני המערכת, ותיקוני קריסה שלו לא מגיעים לטלפון שלא מתעדכן.")
        }
        return Hypothesis(
            "SYSTEM_OUTDATED", "מערכת ההפעלה לא מעודכנת", if ((months ?: 0) >= 24 || oldAndroid) 45 else 38, evidence,
            listOf(
                ActionStep(
                    "UPDATE_SYSTEM",
                    "בדקו עדכונים: הגדרות ← עדכון תוכנה ← בדוק עדכונים. ובנוסף: הגדרות ← אבטחה ← עדכוני מערכת Google Play.",
                    "המערכת לא מתעדכנת, ותיקונים של אחסון המדיה מגיעים רק דרך עדכונים.",
                ),
            ),
        )
    }

    // ---------------- index continuity: rebuilds, shrinking, folders the gallery ignores ----------------

    private const val K_AB_TEST = "AB_TEST"
    private const val K_HEADROOM = "KEEP_HEADROOM"

    private fun abTest(why: String) = ActionStep(
        K_AB_TEST,
        "בדיקת הכרעה: פתחו את אותן תמונות ב-Google Photos או ב-Fossify Gallery (חינמית). אם הכול מוצג שם במלואו ובצורה יציבה, הקבצים והאינדקס תקינים והבעיה במסד הנתונים הפרטי של אפליקציית הגלריה. אם גם שם חסרים קבצים, הבעיה באינדקס המערכת או באחסון.",
        why,
    )

    private fun keepHeadroom(why: String) = ActionStep(
        K_HEADROOM,
        "שמרו תמיד לפחות 10% פנויים באחסון, ואל תתנו לטלפון להיכבות מסוללה ריקה (טענו אותו כשנשאר 15-20%).",
        why,
    )

    private fun indexRebuilds(i: ReportInput): Hypothesis? {
        if (i.bursts.isEmpty()) return null
        val lib = IndexBursts.libraryBurst(i.bursts)
        val later = i.bursts.filter { it != lib }
        val score = when (later.size) { 0 -> 30; 1 -> 48; else -> 62 }
        val evidence = buildList {
            if (lib != null) add("${lib.sharePercent}% מהקבצים (${He.num(lib.count)}) נכנסו לאינדקס המדיה ${Continuity.burstWhen(lib)}. זה מתאים להעברה מטלפון אחר, לשחזור מגיבוי או לבנייה מלאה של האינדקס.")
            for (b in later.take(4)) add(
                "${He.num(b.count)} קבצים (${b.sharePercent}%) נכנסו לאינדקס בבת אחת ${Continuity.burstWhen(b)}" +
                    (if (b.agedPercent >= 50) ", ו-${b.agedPercent}% מהם נוצרו הרבה לפני כן: קבצים קיימים נסרקו מחדש." else "."),
            )
            if (later.isNotEmpty()) add("יום רגיל של שימוש לא מוסיף אלפי קבצים בשעה. אירוע כזה הוא חתימה של בנייה מחדש של האינדקס, ובזמן הבנייה הגלריה מציגה רשימה חלקית וטוענת את עצמה.")
            add("השוו את התאריכים ליום שבו הטלפון כבה מסוללה ריקה או שהמועדפים נעלמו: אם הם קרובים, זו כנראה הסיבה.")
            add("האפליקציה רואה רק את אינדקס המדיה של אנדרואיד. מועדפים ואלבומים של גלריית Xiaomi נשמרים במסד נתונים פרטי של הגלריה, שאינו נגיש לה. אירוע בנייה באינדקס המערכת מרמז שגם הוא עבר אירוע דומה, אבל לא מוכיח זאת.")
        }
        return Hypothesis(
            "INDEX_REBUILDS", if (later.isEmpty()) "כל הספרייה נכנסה לאינדקס בבת אחת (העברה או בנייה מחדש)" else "האינדקס נבנה מחדש יותר מפעם אחת",
            score, evidence,
            listOf(
                backup("בנייה מחדש של האינדקס היא הרגע שבו הגלריה עלולה לאבד מועדפים ואלבומים, ולכן קודם מגבים."),
                abTest("מבדיל בין אינדקס המערכת לבין מסד הנתונים הפרטי של הגלריה."),
                keepHeadroom("כתיבה שנכשלת באמצע (אחסון מלא או כיבוי פתאומי) היא הסיבה הרגילה לבנייה מחדש של האינדקס."),
            ),
        )
    }

    private fun recorderEvents(i: ReportInput): Hypothesis? {
        val t = i.timeline
        if (!t.hasData || t.samples < Timeline.MIN_SAMPLES || !t.anyEvent) return null
        val recovered = t.countDrops.any { it.recoveredAt != null }
        val score = when {
            t.databaseRecreated -> 92
            recovered -> 72
            t.countDrops.isNotEmpty() -> 60
            else -> 42
        }
        val evidence = buildList {
            add("מקליט הרקע עקב אחרי אינדקס המדיה כ-${t.spanHours} שעות (${He.num(t.samples)} דגימות). אלה תצפיות ישירות ולא הערכות.")
            addAll(Timeline.lines(t).drop(1).filter { !it.startsWith("זה מעט") })
            if (t.databaseRecreated) add("מסד נתונים שנוצר מחדש הוא בדיוק מה שמאפס מועדפים ומציג רשימה חלקית עד שהסריקה מסתיימת.")
        }
        return Hypothesis(
            "RECORDER_EVENTS", if (t.databaseRecreated) "מסד נתוני המדיה נוצר מחדש בזמן ההקלטה" else "האינדקס התכווץ או שהטלפון כבה בצורה לא תקינה בזמן ההקלטה",
            score, evidence,
            listOf(
                backup("אירוע שבו המסד נוצר מחדש יכול לחזור, ובו הגלריה עלולה לאבד מועדפים ואלבומים."),
                abTest("מבדיל בין אינדקס המערכת לבין מסד הנתונים הפרטי של הגלריה."),
                keepHeadroom("אחסון מלא וכיבוי מסוללה ריקה הם הגורמים המוכרים לפגיעה במסד הנתונים."),
            ),
        )
    }

    private fun indexShrank(i: ReportInput): Hypothesis? {
        val prev = i.previous ?: return null
        val drop = prev.totalFiles - i.totalFiles
        val filesDropped = drop >= 100 && prev.totalFiles > 0 && drop * 100 / prev.totalFiles >= 3
        val pf = prev.favorites
        val nf = i.favorites
        val favDropped = pf != null && nf != null && pf >= 5 && nf * 10 <= pf * 7
        if (!filesDropped && !favDropped) return null
        val evidence = buildList {
            if (filesDropped) add("בסריקה הקודמת היו ${He.num(prev.totalFiles)} קבצים באינדקס, ועכשיו ${He.num(i.totalFiles)} (ירידה של ${He.num(drop)}). אם לא מחקתם או העברתם קבצים בינתיים, הם נעלמו מהאינדקס בלי להימחק מהדיסק.")
            if (favDropped) add("מספר המועדפים באינדקס ירד מ-$pf ל-$nf. מועדפים נעלמים כשהרשומה שלהם נבנית מחדש.")
            add("זו השוואה בין שתי סריקות של אותו טלפון, ולכן היא ראיה ישירה לאובדן ולא השערה.")
        }
        return Hypothesis(
            "INDEX_SHRANK", "רשומות נעלמו מהאינדקס מאז הסריקה הקודמת", if (favDropped) 74 else 58, evidence,
            listOf(
                backup("רשומות שנעלמו מהאינדקס עלולות להיעלם גם מהגלריה."),
                abTest("בודק אם הקבצים עצמם עדיין קיימים ונראים לאפליקציה אחרת."),
                keepHeadroom("אחסון מלא וכיבוי פתאומי הם הגורמים הנפוצים לרשומות שנעלמות."),
            ),
        )
    }

    private fun nomediaHidden(i: ReportInput): Hypothesis? {
        if (i.nomediaDirs.isEmpty()) return null
        val main = i.nomediaDirs.filter { Continuity.isMainMediaDir(it.path) }
        val total = i.nomediaDirs.sumOf { it.mediaFiles }
        val shown = (main.ifEmpty { i.nomediaDirs }).take(3)
        val evidence = buildList {
            add("${He.folders(i.nomediaDirs.size)} מכילות קובץ .nomedia: " + shown.joinToString(", ") { "\"${it.path.substringAfter("/0/", it.path)}\" (${He.files(it.mediaFiles)})" } + ".")
            add("קובץ .nomedia אוסר על כל אפליקציית גלריה להציג את התמונות והסרטונים שבתיקייה. ${He.files(total)} לא יופיעו בגלריה בכלל, גם כשהם תקינים.")
            if (main.isEmpty()) add("התיקיות האלה שייכות כנראה לאפליקציות (וואטסאפ, טלגרם ועוד) שמסתירות בכוונה, ולכן זה ממצא חלש.")
        }
        return Hypothesis(
            "NOMEDIA_HIDDEN", "תיקיות שהגלריה מתעלמת מהן בגלל קובץ .nomedia", if (main.isNotEmpty()) 66 else 35, evidence,
            listOf(
                ActionStep(
                    "REMOVE_NOMEDIA",
                    "במנהל קבצים עם \"הצג קבצים מוסתרים\": פתחו את התיקייה (${shown.first().path.substringAfter("/0/", shown.first().path)}) ומחקו את הקובץ .nomedia, אלא אם אתם יודעים שאפליקציה שמה אותו בכוונה. אחר כך הפעילו מחדש את הטלפון.",
                    "הקובץ הזה מסתיר את כל התיקייה מהגלריה.",
                ),
            ),
        )
    }

    private fun hiddenFolders(i: ReportInput): Hypothesis? {
        val big = i.hiddenDirs.filter { it.mediaFiles >= 50 }
        if (big.isEmpty()) return null
        val total = big.sumOf { it.mediaFiles }
        val evidence = listOf(
            "${He.folders(big.size)} מוסתרות מכילות ${He.files(total)} (${He.bytes(big.sumOf { it.bytes })}): " +
                big.take(3).joinToString(", ") { "\"${it.path.substringAfter("/0/", it.path)}\"" } + ".",
            "בגלריות מסוימות, כולל גלריית Xiaomi, אלבום פרטי וסל מחזור נשמרים בתיקיות כאלה. הקבצים תקינים אבל מופיעים רק בתוך האפליקציה, ואם מסד הנתונים שלה מתאפס הם נעלמים מהתצוגה.",
            "זו ראיה עקיפה: האפליקציה לא יכולה לפתוח את התיקיות האלה או לדעת למה הן משמשות.",
        )
        return Hypothesis(
            "HIDDEN_FOLDERS", "קבצים בתיקיות מוסתרות שמוצגות רק מתוך הגלריה", 36, evidence,
            listOf(
                backup("קבצים בתיקיות מוסתרות לא נראים בגלריה אחרת, וקל לפספס אותם בגיבוי."),
            ),
        )
    }

    /**
     * Inference by elimination, labelled as such. When the files, the media index and the storage give no direct
     * cause, what is left is the gallery app's own private database, which no other app can read.
     */
    private fun galleryPrivateDb(i: ReportInput, found: List<Hypothesis>): Hypothesis? {
        val gallery = i.apps.firstOrNull { it.role == AppRole.GALLERY && it.enabled } ?: return null
        if (found.any { it.id == "DECODER_CRASH" || it.id == "STORAGE_FAILING" || it.id == "NOMEDIA_HIDDEN" && it.score >= 60 }) return null
        var score = 40
        var signals = 0
        val evidence = ArrayList<String>()
        evidence += "הקבצים והאינדקס של אנדרואיד לא מראים סיבה ישירה לתצוגה חלקית. מה שנשאר הוא מסד הנתונים הפרטי של ${gallery.label}, שאפליקציה אחרת לא יכולה לקרוא. זו מסקנה מהשלילה ולא ממצא."
        if (gallery.pkg == "com.miui.gallery") { score += 10; evidence += "גלריית Xiaomi שומרת מועדפים, אלבומים ומצב סנכרון במסד נתונים משלה, וכשהוא נפגם היא מציגה חלקית ונטענת מחדש." }
        if (i.volumes.any { it.critical }) { score += 10; signals++; evidence += "האחסון כמעט מלא: כתיבה למסד נתונים שנכשלת באמצע היא סיבה מוכרת לפגיעה בו." }
        if (i.heavyFolders.any { it.count >= 15000 }) { score += 10; signals++; evidence += "יש תיקייה עם יותר מ-15,000 קבצים, וגלריה שטוענת אותה בבת אחת עלולה להיתקע ולהיבנות מחדש." }
        if (i.bursts.isNotEmpty() || i.timeline.databaseRecreated) { score += 10; signals++; evidence += "נמצא אירוע שבו האינדקס נבנה מחדש, ומסד הנתונים של הגלריה עשוי היה לעבור איתו אירוע דומה." }
        // without a single supporting sign this is just the baseline cause, not a finding of its own
        if (signals == 0) return null
        evidence += "אם התקלה המשיכה גם אחרי מעבר לטלפון חדש, זה מחזק את ההסבר: הקבצים והגלריה עברו איתכם, והטלפון החדש לא יכול להיות הסיבה."
        return Hypothesis(
            "GALLERY_PRIVATE_DB", "מסד הנתונים הפרטי של אפליקציית הגלריה פגום או נבנה מחדש", minOf(70, score), evidence,
            listOf(
                backup("איפוס הגלריה מאפס אלבומים ומועדפים שלא סונכרנו."),
                abTest("זו הבדיקה היחידה שמבדילה בין מסד הנתונים של הגלריה לבין אינדקס המערכת."),
                clearGalleryCache("זה הצעד הבטוח הראשון: הוא לא מוחק מועדפים."),
                clearGalleryData("אם הבדיקה הראתה שהבעיה רק באפליקציית הגלריה, איפוס הנתונים בונה את מסד הנתונים שלה מחדש."),
                keepHeadroom("מסד נתונים נפגע בעיקר כשהכתיבה אליו נקטעת."),
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
        if (i.crashLog.permissionGranted && i.crashLog.readOk) {
            add("האפליקציה קראה את יומן הקריסות של המערכת, שמכיל רק את הקריסות האחרונות. קריסה ישנה כבר לא תופיע בו, לכן הריצו סריקה מיד אחרי שהגלריה קורסת. מלבד היומן, הדירוג מבוסס על ראיות שנמצאו בקבצים, באינדקס, באחסון ובמכשיר.")
        } else {
            add("האפליקציה לא יכולה לקרוא את יומני הקריסה של אפליקציית הגלריה בלי הרשאה מיוחדת שאפשר לתת מהמחשב (ראו הוראות בסעיף יומן הקריסות). הדירוג בדוח מבוסס על ראיות שנמצאו בקבצים, באינדקס, באחסון ובמכשיר, ולא על מה שהגלריה עצמה דיווחה.")
        }
        add("הדוח מזהה אפליקציות שיכולות להשפיע על הגלריה, אבל אינו יכול לדעת מה הן עושות בפועל, מלבד מי שכתב רשומות לאינדקס בזמן הבדיקה. גודל המטמון והנתונים של אפליקציות אחרות לא נבדק, כי זה דורש הרשאה מיוחדת נוספת.")
        add("רמת הביטחון מציינת עד כמה הראיות חזקות. היא לא ודאות מוחלטת: ייתכן שהסיבה האמיתית אינה ברשימה.")
        add("האפליקציה פועלת בקריאה בלבד. היא לא מוחקת, לא מזיזה ולא משנה אף קובץ. כל פעולה בדוח היא המלצה שאתם מבצעים בעצמכם.")
        for ((reason, n) in i.systemicReasons) {
            add("הבדיקה \"${reason.he}\" נכשלה ב-${He.num(n)} קבצים, כמעט בכולם. כשל כזה בכמעט כל הקבצים מצביע על בעיה בבדיקה עצמה או במפענח של הטלפון, ולא על קבצים פגומים, ולכן הוא לא נספר כקבצים פגומים בדוח.")
        }
        if (!i.scanComplete) add("הסריקה הופסקה לפני שהסתיימה, לכן חלק מהקבצים לא נבדקו והתוצאות חלקיות.")
        if (!i.deepScan) add("בוצעה סריקה מהירה בלי בדיקת פענוח, ולכן לא ניתן היה לזהות קבצים שקורסים את המפענח.")
        if (i.dirsInaccessible > 0) add("${He.folders(i.dirsInaccessible)} לא היו נגישות לאפליקציה, ולכן חיפוש הקבצים שחסרים באינדקס לא כיסה אותן.")
        add("הבדיקה מחפשת קבצים שחסרים באינדקס רק בתיקיות המדיה הרגילות (DCIM, Pictures, Movies, Download, WhatsApp, Telegram, MIUI) ובתיקייה שנבחרה, ולא בכל האחסון.")
        add("מועדפים ואלבומים של גלריית Xiaomi ושל Google Photos נשמרים במסד נתונים פרטי של האפליקציה. האפליקציה לא יכולה לקרוא אותו, לשחזר ממנו או לדעת מה הוא מכיל. מה שהיא רואה הוא אינדקס המדיה של אנדרואיד והקבצים עצמם.")
    }
}
