package il.gallerydoctor.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ======================================================================================
// Everything around the media files that can make a gallery misbehave: the apps, the phone,
// the crash log, odd file names. Pure Kotlin so it is unit-testable on the JVM.
// ======================================================================================

enum class SuspectCategory(val he: String) {
    CLEANER("אפליקציית ניקוי או שיפור ביצועים"),
    BACKUP_SYNC("אפליקציית גיבוי וסנכרון"),
    FILE_MANAGER("מנהל קבצים"),
    ALL_FILES_ACCESS("אפליקציה עם גישה לכל הקבצים"),
}

data class SuspectApp(val pkg: String, val label: String, val category: SuspectCategory)

/** An app that wrote rows to the media index during the 60-second stability window. */
data class OwnerChurn(val pkg: String, val label: String, val count: Int)

data class DeviceInfo(
    val manufacturer: String = "",
    val model: String = "",
    val androidApi: Int = 0,
    val androidRelease: String = "",
    val securityPatch: String? = null, // yyyy-MM-dd
    val ramTotalMb: Long = 0,
    val ramAvailMb: Long = 0,
    val lowRamDevice: Boolean = false,
    val lowMemoryNow: Boolean = false,
    val powerSave: Boolean = false,
    val alwaysFinishActivities: Boolean = false,
    val developerOptionsOn: Boolean = false,
    val playServicesVersion: String? = null,
    /** Label of the app that opens photos by default, or null when Android asks every time. */
    val defaultImageViewer: String? = null,
) {
    /** Xiaomi, Redmi, POCO (HyperOS / MIUI): aggressive background limits and a built-in cleaner. */
    val isXiaomiFamily: Boolean
        get() = manufacturer.lowercase().let { it == "xiaomi" || it == "redmi" || it == "poco" || it == "blackshark" }
}

data class VolumeState(val label: String, val state: String, val removable: Boolean)

enum class NameIssue(val he: String) {
    TOO_LONG("שם ארוך מדי"),
    INVISIBLE_CHARS("תווים בלתי נראים או תווי כיוון בשם"),
    EDGE_SPACE_DOT("רווח או נקודה בתחילת או בסוף השם"),
    BROKEN_ENCODING("תווים פגומים בשם"),
}

data class IssueSummary(val count: Int = 0, val samples: List<String> = emptyList(), val byKind: Map<String, Int> = emptyMap())

enum class CrashKind { JAVA, NATIVE }

data class CrashLogEntry(
    val pkg: String,
    val time: String,
    val kind: CrashKind,
    val headline: String,
    val frames: List<String> = emptyList(),
    val rootCause: String? = null,
)

/** What the system crash log says. [permissionGranted] false means the optional READ_LOGS grant is missing. */
data class CrashLogInfo(
    val permissionGranted: Boolean = false,
    val readOk: Boolean = false,
    /** Crashes of gallery apps / Media Storage only. */
    val related: List<CrashLogEntry> = emptyList(),
    /** Crashes of any other app, for context. */
    val otherCrashCount: Int = 0,
)

/** Why this app's own :scanner processes died (Android 11+). */
data class OwnExitInfo(
    val nativeCrashes: Int = 0,
    val signals: Map<String, Int> = emptyMap(),
    val lowMemoryKills: Int = 0,
    val javaCrashes: Int = 0,
    val anrs: Int = 0,
)

// -------------------------------------------------------------------------------------

object NameChecker {
    /** Gallery apps and file systems start to misbehave around 200+ bytes. The hard limit is 255. */
    private const val MAX_BYTES = 200

    fun issues(name: String): Set<NameIssue> {
        val out = LinkedHashSet<NameIssue>()
        if (name.toByteArray(Charsets.UTF_8).size > MAX_BYTES) out += NameIssue.TOO_LONG
        for (c in name) {
            if (Character.isISOControl(c) || c in '\u200B'..'\u200F' || c in '\u202A'..'\u202E' || c in '\u2066'..'\u2069' || c == '\uFEFF') {
                out += NameIssue.INVISIBLE_CHARS
                break
            }
        }
        if (name.startsWith(' ') || name.endsWith(' ') || name.endsWith('.')) out += NameIssue.EDGE_SPACE_DOT
        if ('\uFFFD' in name || hasLoneSurrogate(name)) out += NameIssue.BROKEN_ENCODING
        return out
    }

    private fun hasLoneSurrogate(s: String): Boolean {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= s.length || !Character.isLowSurrogate(s[i + 1])) return true
                i += 2
                continue
            }
            if (Character.isLowSurrogate(c)) return true
            i++
        }
        return false
    }
}

object DateChecker {
    private const val YEAR_1990 = 631_152_000L

    /** Returns a short Hebrew reason, or null when the dates look sane. Values are seconds since the epoch. */
    fun issue(addedSec: Long, modifiedSec: Long, nowSec: Long): String? = when {
        addedSec <= 0 && modifiedSec <= 0 -> "אין תאריך"
        addedSec > nowSec + 2 * 86_400 || modifiedSec > nowSec + 2 * 86_400 -> "תאריך בעתיד"
        (addedSec in 1 until YEAR_1990) || (modifiedSec in 1 until YEAR_1990) -> "תאריך לפני 1990"
        else -> null
    }
}

object AppClassifier {
    private val cleaners = setOf(
        "com.cleanmaster.mguard", "com.cleanmaster.mguard_cn", "com.piriform.ccleaner", "eu.thedarken.sdm",
        "com.avast.android.cleaner", "com.miui.cleanmaster", "com.miui.cleaner", "com.coloros.phonemanager",
        "com.oppo.cleaner", "com.huawei.systemmanager", "com.samsung.android.lool", "com.iobit.mobilecare",
        "com.ludashi.security", "com.lionmobi.powerclean", "com.apusapps.fastcleaner", "com.vivo.abe",
        "com.vivo.upslide", "com.android.cleaner",
    )
    private val sync = setOf(
        "com.dropbox.android", "com.microsoft.skydrive", "com.google.android.apps.docs", "mega.privacy.android.app",
        "com.amazon.clouddrive.photos", "com.nextcloud.client", "com.owncloud.android", "com.ttxapps.dropsync",
        "dk.tacit.android.foldersync.lite", "dk.tacit.android.foldersync.full", "com.resilio.sync",
        "com.synology.dscloud", "com.syncthing.android", "com.miui.cloudservice", "com.miui.cloudbackup",
    )
    private val fileManagers = setOf(
        "com.google.android.apps.nbu.files", "com.alphainventor.filemanager", "nextapp.fx", "com.lonelycatgames.Xplore",
        "pl.solidexplorer2", "com.ghisler.android.TotalCommander", "com.speedsoftware.rootexplorer",
        "com.estrongs.android.pop", "com.cxinventor.file.explorer", "com.mi.android.globalFileexplorer",
        "com.sec.android.app.myfiles", "com.coloros.filemanager", "com.oplus.filemanager",
    )
    private val cleanerWords = Regex("(clean|booster|junk|optimi[sz]|speed ?up|ניקוי|מנקה|אופטימיזציה)", RegexOption.IGNORE_CASE)

    /** Null when the app is not interesting. System apps count only if they are well-known cleaners / managers. */
    fun classify(pkg: String, label: String, hasAllFilesAccess: Boolean, isSystem: Boolean): SuspectCategory? {
        if (pkg in cleaners) return SuspectCategory.CLEANER
        if (pkg in sync) return SuspectCategory.BACKUP_SYNC
        if (pkg in fileManagers) return SuspectCategory.FILE_MANAGER
        if (isSystem) return null
        if (cleanerWords.containsMatchIn(label) || cleanerWords.containsMatchIn(pkg)) return SuspectCategory.CLEANER
        if (hasAllFilesAccess) return SuspectCategory.ALL_FILES_ACCESS
        return null
    }
}

/** What a crash log line means for a gallery, in plain words. */
enum class CrashInsight { OUT_OF_MEMORY, DATABASE, MISSING_FILE, PERMISSION, NATIVE_DECODER, OTHER }

object CrashInsights {
    private val decoderLibs = listOf(
        "libhwui", "libjpeg", "libpng", "libwebp", "libheif", "libstagefright", "libmedia", "libcodec2",
        "mediacodec", "libskia", "libavcodec", "libgralloc", "libimage", "libandroid_runtime",
    )

    fun classify(e: CrashLogEntry): CrashInsight {
        val text = (e.headline + " " + (e.rootCause ?: "") + " " + e.frames.joinToString(" ")).lowercase()
        return when {
            "outofmemoryerror" in text -> CrashInsight.OUT_OF_MEMORY
            "sqlite" in text || "database disk image is malformed" in text || "disk i/o error" in text -> CrashInsight.DATABASE
            "filenotfoundexception" in text || "enoent" in text || "no such file" in text -> CrashInsight.MISSING_FILE
            "securityexception" in text || "permission denial" in text || "permission denied" in text -> CrashInsight.PERMISSION
            e.kind == CrashKind.NATIVE || decoderLibs.any { it in text } -> CrashInsight.NATIVE_DECODER
            else -> CrashInsight.OTHER
        }
    }
}

/**
 * Parses `logcat -b crash -v threadtime` output into crash entries (Java "FATAL EXCEPTION" blocks and
 * native "Fatal signal" / debuggerd tombstone headers). Lines of other formats are ignored.
 */
object CrashLogParser {
    private val line = Regex("""^(\d\d-\d\d \d\d:\d\d:\d\d)\.\d+\s+(\d+)\s+(\d+)\s+([VDIWEFA])\s+(.+?)\s*:\s?(.*)$""")
    private val nativeHeader = Regex("""pid: \d+, tid: \d+, name: .*>>>\s*(\S+)\s*<<<""")
    private val signalText = Regex("""signal (\d+) \((\w+)\)""")
    private val libcFatal = Regex("""Fatal signal (\d+) \((\w+)\).*pid \d+ \(([^)]+)\)""")
    private val nativeFrame = Regex("""#\d+\s+pc\s+[0-9a-fA-F]+\s+(\S+)(?:\s+\((.*?)\))?""")

    private class Builder(var pkg: String, val time: String, val kind: CrashKind) {
        var headline: String? = null
        var rootCause: String? = null
        val frames = ArrayList<String>()
        fun build() = CrashLogEntry(
            pkg.substringBefore(':'), time, kind, headline ?: "(ללא פרטים)", frames.take(6), rootCause,
        )
    }

    fun parse(lines: List<String>): List<CrashLogEntry> {
        val done = ArrayList<CrashLogEntry>()
        val javaByPid = HashMap<String, Builder>()
        var nativeCurrent: Builder? = null

        fun flushNative() {
            nativeCurrent?.let { done += it.build() }
            nativeCurrent = null
        }

        for (raw in lines) {
            val m = line.matchEntire(raw) ?: continue
            val (time, pid, _, _, tag, msg) = m.destructured
            when {
                tag == "AndroidRuntime" -> {
                    if (msg.startsWith("FATAL EXCEPTION")) {
                        javaByPid[pid]?.let { done += it.build() }
                        javaByPid[pid] = Builder("", time, CrashKind.JAVA)
                        continue
                    }
                    val b = javaByPid[pid] ?: continue
                    val t = msg.trimStart('\t', ' ')
                    when {
                        msg.startsWith("Process: ") -> b.pkg = msg.removePrefix("Process: ").substringBefore(',').trim()
                        t.startsWith("at ") -> if (b.frames.size < 6) b.frames += t.removePrefix("at ")
                        t.startsWith("Caused by: ") -> b.rootCause = t.removePrefix("Caused by: ")
                        t.isNotBlank() && b.headline == null -> b.headline = t
                    }
                }

                tag == "DEBUG" || tag == "libc" -> {
                    val header = nativeHeader.find(msg)
                    val fatal = libcFatal.find(msg)
                    when {
                        header != null -> {
                            flushNative()
                            nativeCurrent = Builder(header.groupValues[1], time, CrashKind.NATIVE)
                        }

                        fatal != null -> {
                            flushNative()
                            nativeCurrent = Builder(fatal.groupValues[3], time, CrashKind.NATIVE).also {
                                it.headline = "signal ${fatal.groupValues[1]} (${fatal.groupValues[2]})"
                            }
                        }

                        nativeCurrent != null -> {
                            val b = nativeCurrent!!
                            val sig = signalText.find(msg)
                            if (sig != null && b.headline == null) b.headline = "signal ${sig.groupValues[1]} (${sig.groupValues[2]})"
                            val f = nativeFrame.find(msg)
                            if (f != null && b.frames.size < 6) {
                                val lib = f.groupValues[1].substringAfterLast('/')
                                val sym = f.groupValues.getOrNull(2)?.substringBefore('+').orEmpty()
                                b.frames += if (sym.isNotBlank()) "$lib ($sym)" else lib
                            }
                        }
                    }
                }
            }
        }
        flushNative()
        javaByPid.values.forEach { done += it.build() }
        // the same crash can appear from both the libc line and the tombstone (a second apart): keep the richer one
        return done.filter { it.pkg.isNotBlank() }
            .groupBy { Triple(it.pkg, it.kind, it.time.take(11)) } // "MM-dd HH:mm"
            .map { (_, same) -> same.maxByOrNull { it.frames.size }!! }
            .sortedBy { it.time }
    }
}

/** Plain-Hebrew lines describing the phone, the apps and the crash log. Shared by the screen, the text and the PDF. */
object EnvLines {
    private fun date(ms: Long) = if (ms <= 0) "-" else SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date(ms))

    fun installerHe(installer: String?, system: Boolean): String = when {
        installer == "com.android.vending" -> "Google Play"
        installer == "com.google.android.packageinstaller" || installer == "com.android.packageinstaller" -> "התקנה ידנית (קובץ APK)"
        installer == "com.sec.android.app.samsungapps" -> "Galaxy Store"
        installer == "com.xiaomi.mipicks" || installer == "com.xiaomi.market" -> "חנות Xiaomi"
        installer == null && system -> "המערכת"
        installer == null -> "לא ידוע"
        else -> installer
    }

    fun appLines(apps: List<AppInfo>): List<String> = apps.map { a ->
        buildList {
            add("${a.label} (${a.pkg})")
            a.version?.let { add("גרסה $it") }
            if (a.installedAtMillis > 0) add("הותקנה ${date(a.installedAtMillis)}")
            if (a.updatedAtMillis > 0) add("עודכנה ${date(a.updatedAtMillis)}")
            add("מקור: ${installerHe(a.installer, a.system)}")
            if (a.system) add("אפליקציית מערכת")
            if (!a.enabled) add("מושבתת!")
            if (a.stopped) add("נעצרה בכוח")
            if (a.defaultViewer) add("ברירת המחדל לפתיחת תמונות")
        }.joinToString(" | ")
    }

    private fun stateHe(state: String) = when (state) {
        "mounted" -> "תקין"
        "mounted_ro" -> "לקריאה בלבד"
        "checking" -> "בבדיקה"
        "unmounted" -> "לא מחובר"
        "ejecting" -> "בהוצאה"
        "unmountable" -> "לא ניתן לקריאה (פגום)"
        "bad_removal" -> "הוצא בצורה לא תקינה"
        "nofs" -> "ללא מערכת קבצים"
        else -> state
    }

    fun isVolumeProblem(v: VolumeState) = v.state != "mounted"

    fun volumeStateHe(v: VolumeState) = stateHe(v.state)

    fun deviceLines(i: ReportInput): List<String> {
        val d = i.device ?: return emptyList()
        val now = if (i.generatedAtMillis > 0) i.generatedAtMillis else System.currentTimeMillis()
        return buildList {
            add("יצרן ודגם: ${d.manufacturer} ${d.model}".trim())
            val patch = d.securityPatch?.let { " | עדכון אבטחה: $it" + (patchAgeMonths(it, now)?.let { m -> " (לפני $m חודשים)" } ?: "") } ?: ""
            add("אנדרואיד ${d.androidRelease} (API ${d.androidApi})$patch")
            if (d.ramTotalMb > 0) add("זיכרון RAM: ${He.num(d.ramTotalMb)}MB, פנוי עכשיו ${He.num(d.ramAvailMb)}MB" + if (d.lowMemoryNow) " (הטלפון במצוקת זיכרון)" else "")
            add("חיסכון בסוללה: " + if (d.powerSave) "פעיל" else "כבוי")
            add("\"אל תשמור פעילויות\": " + if (d.alwaysFinishActivities) "פעיל (גורם לגלריה להיטען מחדש)" else "כבוי")
            add("אפשרויות מפתח: " + if (d.developerOptionsOn) "מופעלות" else "כבויות")
            d.playServicesVersion?.let { add("Google Play services: גרסה $it") }
            add("אפליקציה שפותחת תמונות כברירת מחדל: " + (d.defaultImageViewer ?: "לא נבחרה, אנדרואיד שואל בכל פעם"))
            for (v in i.volumeStates) add("אחסון \"${v.label}\"${if (v.removable) " (נשלף)" else ""}: ${stateHe(v.state)}")
        }
    }

    /** What the scan looked at and found in order, so a clean result is stated instead of silently omitted. */
    fun healthyLines(i: ReportInput): List<String> = buildList {
        if (i.totalFiles <= 0) return@buildList
        if (i.deepScan && i.crasherCount == 0 && i.scanComplete) add("אף קובץ לא הקריס או תקע את מפענח התמונות והסרטונים (נבדקו ${He.files(i.totalFiles)}).")
        if (i.ghostCount == 0) add("אין רשומות רפאים: כל קובץ שאינדקס המדיה מכיר קיים בפועל.")
        if (i.orphanCount == 0 && i.safOnlyCount == 0) add("אין קבצי מדיה שחסרים באינדקס (בתיקיות המדיה הרגילות).")
        val drift = (i.indexChanged ?: -1) + (i.indexVanished ?: 0) + (i.indexAppeared ?: 0)
        if (i.indexChanged != null && drift == 0) add("אינדקס המדיה יציב: לא השתנה כלל בדקה שנבדקה.")
        if (i.pendingStuckCount == 0) add("אין רשומות תקועות באמצע כתיבה.")
        if (i.probedFiles > 0 && i.ioErrors == 0 && i.intermittentCount == 0) add("לא נמצאו שגיאות קריאה באחסון (${He.files(i.probedFiles)} נקראו פעמיים ונבדקו).")
        if (i.volumes.isNotEmpty() && i.volumes.none { it.critical }) add("יש מספיק מקום פנוי באחסון.")
    }

    fun suspectLines(i: ReportInput): List<String> =
        i.suspectApps.map { "${it.label} (${it.pkg}) – ${it.category.he}" }

    fun crashLogLines(i: ReportInput): List<String> {
        val c = i.crashLog
        if (!c.permissionGranted) return listOf(
            "לא ניתנה הרשאה לקרוא את יומן הקריסות של המערכת. כדי לראות את הקריסה האמיתית של הגלריה, חברו את הטלפון למחשב והריצו פעם אחת: adb shell pm grant il.gallerydoctor android.permission.READ_LOGS",
        )
        if (!c.readOk) return listOf("ניסינו לקרוא את יומן הקריסות ולא הצלחנו.")
        if (c.related.isEmpty()) return listOf(
            "יומן הקריסות נקרא. לא נמצאה בו קריסה של אפליקציית גלריה או של אחסון המדיה" +
                (if (c.otherCrashCount > 0) " (נמצאו ${c.otherCrashCount} קריסות של אפליקציות אחרות)" else "") +
                ". היומן שומר רק את הקריסות האחרונות: פתחו את הגלריה עד שהיא קורסת ואז הריצו סריקה מיד.",
        )
        return c.related.map { e ->
            val where = if (e.kind == CrashKind.NATIVE) "קריסה נייטיב" else "קריסת אפליקציה"
            "${e.time} – ${e.pkg} – $where: ${e.headline}" + (e.rootCause?.let { " (סיבה: $it)" } ?: "") +
                (if (e.frames.isNotEmpty()) " | ${e.frames.take(3).joinToString(" ← ")}" else "")
        }
    }

    /** Months between a yyyy-MM-dd patch date and [nowMillis], or null if unparsable. */
    fun patchAgeMonths(patch: String, nowMillis: Long): Int? = try {
        val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(patch)!!
        ((nowMillis - d.time) / (30.4 * 86_400_000)).toInt().coerceAtLeast(0)
    } catch (_: Exception) {
        null
    }
}
