package il.gallerydoctor.core

import java.util.Locale

data class ProblemRow(
    val pk: Long,
    val uri: String,
    val name: String,
    val folder: String,
    val sizeBytes: Long,
    val mime: String?,
    val isVideo: Boolean,
    val status: Status,
    val reasons: List<Reason>,
    val anomalies: List<Anomaly> = emptyList(),
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Long = -1,
    val volume: String = "",
) {
    val reasonsHe: String get() = (reasons.filter { it.severity != Severity.INFO || it == Reason.GHOST_ROW }.map { it.he } + anomalies.map { it.he }).joinToString("; ")
}

data class DupGroup(val files: List<ProblemRow>, val eachBytes: Long) {
    val reclaimableBytes: Long get() = eachBytes * (files.size - 1)
}

data class FolderStat(val path: String, val count: Int, val bytes: Long)

data class VolumeStat(val label: String, val totalBytes: Long, val freeBytes: Long, val removable: Boolean) {
    val usedPercent: Int get() = if (totalBytes <= 0) 0 else (100 - freeBytes * 100 / totalBytes).toInt()
    val critical: Boolean get() = totalBytes > 0 && (freeBytes * 10 < totalBytes || freeBytes < 2L * 1024 * 1024 * 1024)
}

enum class AppRole { GALLERY, MEDIA_PROVIDER }
data class AppInfo(
    val pkg: String,
    val label: String,
    val version: String?,
    val role: AppRole,
    val installedAtMillis: Long = 0,
    val updatedAtMillis: Long = 0,
    val installer: String? = null,
    val targetSdk: Int = 0,
    val enabled: Boolean = true,
    val stopped: Boolean = false,
    val system: Boolean = false,
    val defaultViewer: Boolean = false,
)

/** Everything measured by a scan, already aggregated. The analyzer turns this into a ranked diagnosis. */
data class ReportInput(
    val totalFiles: Int = 0,
    val imageCount: Int = 0,
    val videoCount: Int = 0,
    val okCount: Int = 0,
    val suspectCount: Int = 0,
    val brokenCount: Int = 0,
    val crasherCount: Int = 0,
    val ghostCount: Int = 0,
    val skippedCount: Int = 0,
    val crashers: List<ProblemRow> = emptyList(),
    val broken: List<ProblemRow> = emptyList(),
    val suspect: List<ProblemRow> = emptyList(),
    val oversized: List<ProblemRow> = emptyList(),
    val anomalyCounts: Map<Anomaly, Int> = emptyMap(),
    val reasonHistogram: Map<Reason, Int> = emptyMap(),
    val ghostSamples: List<ProblemRow> = emptyList(),
    val orphanCount: Int = 0,
    val orphanSamples: List<String> = emptyList(),
    val safOnlyCount: Int = 0,
    val pendingStuckCount: Int = 0,
    val pendingSamples: List<String> = emptyList(),
    val leftoverTempCount: Int = 0,
    val leftoverSamples: List<String> = emptyList(),
    val heavyFolders: List<FolderStat> = emptyList(),
    val topFolders: List<FolderStat> = emptyList(),
    val folderThreshold: Int = 5000,
    val volumes: List<VolumeStat> = emptyList(),
    val imageBytes: Long = 0,
    val videoBytes: Long = 0,
    val ioErrors: Int = 0,
    val probedFiles: Int = 0,
    val intermittentCount: Int = 0,
    val slowReadCount: Int = 0,
    val medianMbps: Float? = null,
    val failingVolumes: List<String> = emptyList(),
    val indexChanged: Int? = null,
    val indexVanished: Int? = null,
    val indexAppeared: Int? = null,
    val dupGroups: List<DupGroup> = emptyList(),
    val dupGroupCount: Int = 0,
    val reclaimableBytes: Long = 0,
    val apps: List<AppInfo> = emptyList(),
    val device: DeviceInfo? = null,
    val volumeStates: List<VolumeState> = emptyList(),
    val suspectApps: List<SuspectApp> = emptyList(),
    val churnOwners: List<OwnerChurn> = emptyList(),
    val nameIssues: IssueSummary = IssueSummary(),
    val dateIssues: IssueSummary = IssueSummary(),
    /** Moments when a large share of all files entered the media index at once. */
    val bursts: List<IndexBurst> = emptyList(),
    /** Favorites in Android's media index (API 30+), or null when unavailable. */
    val favorites: Int? = null,
    val previous: ScanSnapshot? = null,
    val nomediaDirs: List<NomediaDir> = emptyList(),
    val hiddenDirs: List<HiddenDir> = emptyList(),
    val thumbEntries: Int? = null,
    val timeline: TimelineSummary = TimelineSummary(),
    /** Biggest folders of the whole shared storage (media or not): where the space actually went. */
    val storageDirs: List<FolderStat> = emptyList(),
    val storageWalkComplete: Boolean = true,
    val crashLog: CrashLogInfo = CrashLogInfo(),
    val ownExits: OwnExitInfo = OwnExitInfo(),
    /** Checks that failed on an implausibly large share of files and were therefore NOT counted as corrupt files. */
    val systemicReasons: Map<Reason, Int> = emptyMap(),
    val scanComplete: Boolean = true,
    val deepScan: Boolean = true,
    val dirsInaccessible: Int = 0,
    val androidApi: Int = 0,
    val safFolderName: String? = null,
    val durationSec: Long = 0,
    val generatedAtMillis: Long = 0,
)

data class ActionStep(val key: String, val text: String, val why: String)

data class Hypothesis(
    val id: String,
    val title: String,
    val score: Int,
    val evidence: List<String>,
    val actions: List<ActionStep>,
) {
    val confidence: Confidence
        get() = when {
            score >= 80 -> Confidence.HIGH
            score >= 50 -> Confidence.MEDIUM
            else -> Confidence.LOW
        }
}

data class Analysis(
    val light: Light,
    val verdict: String,
    val hypotheses: List<Hypothesis>,
    val actions: List<ActionStep>,
    val limits: List<String>,
)

data class ReportData(val input: ReportInput, val analysis: Analysis)

/** Small Hebrew formatting helpers (counts with correct singular/plural, sizes). */
object He {
    fun num(n: Int): String = String.format(Locale.US, "%,d", n)
    fun num(n: Long): String = String.format(Locale.US, "%,d", n)

    fun files(n: Int) = if (n == 1) "קובץ אחד" else "${num(n)} קבצים"
    fun entries(n: Int) = if (n == 1) "רשומה אחת" else "${num(n)} רשומות"
    fun folders(n: Int) = if (n == 1) "תיקייה אחת" else "${num(n)} תיקיות"

    fun bytes(b: Long): String {
        val kb = 1024.0
        return when {
            b >= kb * kb * kb -> String.format(Locale.US, "%.1f GB", b / (kb * kb * kb))
            b >= kb * kb -> String.format(Locale.US, "%.1f MB", b / (kb * kb))
            b >= kb -> String.format(Locale.US, "%.0f KB", b / kb)
            else -> "$b B"
        }
    }

    fun duration(ms: Long): String {
        if (ms < 0) return "-"
        val s = ms / 1000
        return String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
    }

    /** "a, b, c ועוד 4" */
    fun names(list: List<String>, total: Int = list.size, max: Int = 3): String {
        val shown = list.take(max).joinToString(", ")
        val more = total - minOf(max, list.size)
        return if (more > 0) "$shown ועוד ${num(more)}" else shown
    }
}
