package il.gallerydoctor.data

import android.content.Context
import android.os.Build
import il.gallerydoctor.core.Analyzer
import il.gallerydoctor.core.ReportData
import il.gallerydoctor.core.ReportInput
import il.gallerydoctor.core.Status
import il.gallerydoctor.scan.GalleryApps
import il.gallerydoctor.scan.StorageInfo

/** Aggregates the state DB plus live device info into a [ReportInput], then runs the analyzer. */
object ReportBuilder {
    const val FOLDER_THRESHOLD = 5000
    const val SLOW_MBPS = 5f
    private const val TABLE_ROWS = 200

    fun build(ctx: Context, db: StateDb, scanComplete: Boolean): ReportData {
        val counts = db.countByStatus()
        val labels = StorageInfo.labels(ctx)
        val io = db.ioStats(SLOW_MBPS)
        val (imgBytes, vidBytes) = db.sizeByKind()
        val (pendingN, pendingNames) = db.pendingStuck(24 * 3600L, 5)
        val (dupGroups, reclaimable) = db.dupGroups(50)
        val started = db.metaLong("started") ?: 0L

        val input = ReportInput(
            totalFiles = db.count("status!=6"),
            imageCount = db.count("video=0 AND status!=6"),
            videoCount = db.count("video=1 AND status!=6"),
            okCount = counts[Status.OK] ?: 0,
            suspectCount = counts[Status.SUSPECT] ?: 0,
            brokenCount = counts[Status.BROKEN] ?: 0,
            crasherCount = counts[Status.CRASHER] ?: 0,
            ghostCount = counts[Status.GHOST] ?: 0,
            skippedCount = counts[Status.SKIPPED] ?: 0,
            crashers = db.rowsWithStatus(Status.CRASHER, TABLE_ROWS),
            broken = db.rowsWithStatus(Status.BROKEN, TABLE_ROWS),
            suspect = db.rowsWithStatus(Status.SUSPECT, TABLE_ROWS),
            oversized = db.problemRows("anomalies!=0", limit = TABLE_ROWS),
            anomalyCounts = db.anomalyCounts(),
            reasonHistogram = db.reasonHistogram(),
            ghostSamples = db.rowsWithStatus(Status.GHOST, 20),
            orphanCount = db.extraCount("ORPHAN"),
            orphanSamples = db.extraSamples("ORPHAN", 5),
            safOnlyCount = db.count("source=1"),
            pendingStuckCount = pendingN,
            pendingSamples = pendingNames,
            leftoverTempCount = db.extraCount("LEFTOVER"),
            leftoverSamples = db.extraSamples("LEFTOVER", 5),
            heavyFolders = db.folderStats(FOLDER_THRESHOLD),
            topFolders = db.topFoldersBySize(8),
            folderThreshold = FOLDER_THRESHOLD,
            volumes = StorageInfo.volumeStats(ctx),
            imageBytes = imgBytes,
            videoBytes = vidBytes,
            ioErrors = io.ioErrors,
            probedFiles = io.probed,
            intermittentCount = io.intermittent,
            slowReadCount = io.slow,
            medianMbps = io.medianMbps,
            failingVolumes = io.failingVolumes.map { labels[it] ?: it },
            indexChanged = db.metaLong("idx_changed")?.toInt(),
            indexVanished = db.metaLong("idx_vanished")?.toInt(),
            indexAppeared = db.metaLong("idx_appeared")?.toInt(),
            dupGroups = dupGroups,
            dupGroupCount = db.dupGroupCount(),
            reclaimableBytes = reclaimable,
            apps = GalleryApps.detect(ctx),
            scanComplete = scanComplete,
            deepScan = db.meta("deep") == "1",
            dirsInaccessible = db.metaLong("dirs_inaccessible")?.toInt() ?: 0,
            androidApi = Build.VERSION.SDK_INT,
            safFolderName = db.meta("saf_name"),
            durationSec = if (started > 0) (System.currentTimeMillis() - started) / 1000 else 0,
            generatedAtMillis = System.currentTimeMillis(),
        )
        return ReportData(input, Analyzer.analyze(input))
    }
}
