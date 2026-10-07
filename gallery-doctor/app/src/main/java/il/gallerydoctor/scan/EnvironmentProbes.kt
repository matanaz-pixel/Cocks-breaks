package il.gallerydoctor.scan

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import il.gallerydoctor.core.AppClassifier
import il.gallerydoctor.core.CrashLogInfo
import il.gallerydoctor.core.CrashLogParser
import il.gallerydoctor.core.DeviceInfo
import il.gallerydoctor.core.OwnExitInfo
import il.gallerydoctor.core.SuspectApp
import java.io.IOException

/** Read-only facts about the phone and the apps around the gallery. Nothing here writes or changes anything. */
object AppProbe {
    fun label(ctx: Context, pkg: String): String = try {
        ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
        pkg
    }

    /** (package, label) of the app Android opens photos with by default, or null when it asks every time. */
    fun defaultImageViewer(ctx: Context): Pair<String, String>? {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://media/external/images/media/1"), "image/*")
        val pkg = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName ?: return null
        if (pkg == "android" || pkg == "com.android.intentresolver") return null // the "choose an app" dialog
        return pkg to label(ctx, pkg)
    }

    /**
     * Installed apps that can change media files behind the gallery's back: cleaners, backup/sync tools,
     * file managers, and apps holding "all files access". Needs QUERY_ALL_PACKAGES; without it the list is just shorter.
     */
    fun suspects(ctx: Context, exclude: Set<String>): List<SuspectApp> {
        val pm = ctx.packageManager
        @Suppress("DEPRECATION")
        val packages: List<PackageInfo> = try {
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        } catch (_: Exception) {
            emptyList()
        }
        val out = ArrayList<SuspectApp>()
        for (p in packages) {
            if (p.packageName in exclude || p.packageName == ctx.packageName) continue
            val ai = p.applicationInfo ?: continue
            val system = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val perms = p.requestedPermissions
            val flags = p.requestedPermissionsFlags
            var allFiles = false
            if (perms != null && flags != null) {
                val idx = perms.indexOf("android.permission.MANAGE_EXTERNAL_STORAGE")
                allFiles = idx >= 0 && idx < flags.size && (flags[idx] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
            }
            val label = pm.getApplicationLabel(ai).toString()
            val category = AppClassifier.classify(p.packageName, label, allFiles, system) ?: continue
            out += SuspectApp(p.packageName, label, category)
        }
        return out.sortedWith(compareBy({ it.category.ordinal }, { it.label })).take(30)
    }
}

object DeviceProbe {
    fun collect(ctx: Context, defaultViewerLabel: String?): DeviceInfo {
        val am = ctx.getSystemService(ActivityManager::class.java)
        val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val power = ctx.getSystemService(PowerManager::class.java)
        fun flag(key: String) = try { Settings.Global.getInt(ctx.contentResolver, key, 0) == 1 } catch (_: Exception) { false }
        val gms = try { ctx.packageManager.getPackageInfo("com.google.android.gms", 0).versionName } catch (_: Exception) { null }
        return DeviceInfo(
            manufacturer = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() },
            model = Build.MODEL.orEmpty(),
            androidApi = Build.VERSION.SDK_INT,
            androidRelease = Build.VERSION.RELEASE.orEmpty(),
            securityPatch = Build.VERSION.SECURITY_PATCH?.takeIf { it.isNotBlank() },
            ramTotalMb = mem.totalMem / (1024 * 1024),
            ramAvailMb = mem.availMem / (1024 * 1024),
            lowRamDevice = am.isLowRamDevice,
            lowMemoryNow = mem.lowMemory,
            powerSave = power.isPowerSaveMode,
            alwaysFinishActivities = flag("always_finish_activities"),
            developerOptionsOn = flag(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED),
            playServicesVersion = gms,
            defaultImageViewer = defaultViewerLabel,
        )
    }
}

/**
 * Reads the system crash log, which shows the REAL crash of the gallery. Android only allows this with the
 * READ_LOGS permission, which the user grants once from a computer:
 * `adb shell pm grant il.gallerydoctor android.permission.READ_LOGS`. Without it this returns "not granted".
 */
object CrashLogReader {
    private const val READ_LOGS = "android.permission.READ_LOGS"
    private const val MAX_LINES = 40_000
    private const val TIMEOUT_MS = 10_000L

    fun read(ctx: Context, relatedPackages: Set<String>): CrashLogInfo {
        if (ctx.checkSelfPermission(READ_LOGS) != PackageManager.PERMISSION_GRANTED) return CrashLogInfo()
        var ok = false
        var entries = dump(listOf("logcat", "-b", "crash", "-d", "-v", "threadtime"))?.also { ok = true }?.let { CrashLogParser.parse(it) }.orEmpty()
        if (entries.isEmpty()) {
            // some phones keep crashes only in the main buffer
            dump(listOf("logcat", "-b", "main", "-d", "-v", "threadtime", "AndroidRuntime:E", "DEBUG:F", "libc:F", "*:S"))
                ?.also { ok = true }?.let { entries = CrashLogParser.parse(it) }
        }
        if (!ok) return CrashLogInfo(permissionGranted = true, readOk = false)
        val others = entries.filter { it.pkg != ctx.packageName }
        val related = others.filter { isRelated(it.pkg, relatedPackages) }
        return CrashLogInfo(true, true, related.takeLast(10), others.size - related.size)
    }

    private fun isRelated(pkg: String, known: Set<String>) =
        pkg in known || pkg.contains("gallery", true) || pkg.contains("photos", true) ||
            pkg.contains("providers.media") || pkg == "com.android.externalstorage"

    /** Runs logcat in dump mode with a hard time limit. Returns null if it could not run. */
    private fun dump(cmd: List<String>): List<String>? {
        val process = try {
            ProcessBuilder(cmd).redirectErrorStream(true).start()
        } catch (_: IOException) {
            return null
        }
        val killer = Thread {
            try { Thread.sleep(TIMEOUT_MS) } catch (_: InterruptedException) { return@Thread }
            process.destroy()
        }.apply { isDaemon = true; start() }
        return try {
            val lines = ArrayList<String>()
            process.inputStream.bufferedReader().useLines { seq -> for (l in seq) { lines += l; if (lines.size >= MAX_LINES) break } }
            lines
        } catch (_: IOException) {
            null
        } finally {
            killer.interrupt()
            process.destroy()
        }
    }
}

/** Why Android says OUR scanner processes died (Android 11+). Shows native crash signals and memory kills. */
object OwnExits {
    fun collect(ctx: Context, sinceMillis: Long): OwnExitInfo {
        if (Build.VERSION.SDK_INT < 30) return OwnExitInfo()
        val am = ctx.getSystemService(ActivityManager::class.java)
        val list = try { am.getHistoricalProcessExitReasons(ctx.packageName, 0, 200) } catch (_: Exception) { return OwnExitInfo() }
        var nativeCrashes = 0
        var lmk = 0
        var java = 0
        var anr = 0
        val signals = HashMap<String, Int>()
        for (e in list) {
            if (e.timestamp < sinceMillis || !e.processName.contains(":scanner")) continue
            when (e.reason) {
                ApplicationExitInfo.REASON_CRASH_NATIVE -> {
                    nativeCrashes++
                    val name = Regex("""signal (\d+) \((\w+)\)""").find(e.description.orEmpty())?.groupValues?.get(2) ?: signalName(e.status)
                    signals[name] = (signals[name] ?: 0) + 1
                }
                ApplicationExitInfo.REASON_CRASH -> java++
                ApplicationExitInfo.REASON_LOW_MEMORY -> lmk++
                ApplicationExitInfo.REASON_ANR -> anr++
            }
        }
        return OwnExitInfo(nativeCrashes, signals, lmk, java, anr)
    }

    private fun signalName(n: Int) = when (n) {
        4 -> "SIGILL"; 6 -> "SIGABRT"; 7 -> "SIGBUS"; 8 -> "SIGFPE"; 11 -> "SIGSEGV"; else -> "signal $n"
    }
}
