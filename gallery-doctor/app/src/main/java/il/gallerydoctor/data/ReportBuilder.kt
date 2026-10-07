package il.gallerydoctor.data

import android.content.Context
import android.os.Build
import il.gallerydoctor.core.Analyzer
import il.gallerydoctor.core.ReportData
import il.gallerydoctor.core.ReportInput
import il.gallerydoctor.core.Status
import il.gallerydoctor.core.DateChecker
import il.gallerydoctor.core.IssueSummary
import il.gallerydoctor.core.NameChecker
import il.gallerydoctor.core.HiddenDir
import il.gallerydoctor.core.IndexBursts
import il.gallerydoctor.core.NomediaDir
import il.gallerydoctor.core.OwnerChurn
import il.gallerydoctor.scan.AppProbe
import il.gallerydoctor.scan.CrashLogReader
import il.gallerydoctor.scan.DeviceProbe
import il.gallerydoctor.scan.GalleryApps
import il.gallerydoctor.scan.OwnExits
import il.gallerydoctor.scan.StorageInfo

/** Aggregates the state DB plus live device info into a [ReportInput], then runs the analyzer. */
object ReportBuilder {
    const val FOLDER_THRESHOLD = 5000
    const val SLOW_MBPS = 5f
    private const val TABLE_ROWS = 200

    fun build(ctx: Context, db: StateDb, scanComplete: Boolean): ReportData {
        val systemic = db.neutralizeSystemicReasons() // before any counting: a broken check must not count as broken files
        val counts = db.countByStatus()
        val labels = StorageInfo.labels(ctx)
        val io = db.ioStats(SLOW_MBPS)
        val (imgBytes, vidBytes) = db.sizeByKind()
        val (pendingN, pendingNames) = db.pendingStuck(24 * 3600L, 5)
        val (dupGroups, reclaimable) = db.dupGroups(50)
        val started = db.metaLong("started") ?: 0L
        val now = System.currentTimeMillis()

        // apps around the gallery, the phone, and the real crash log
        val defaultViewer = AppProbe.defaultImageViewer(ctx)
        val apps = GalleryApps.detect(ctx).map { it.copy(defaultViewer = it.pkg == defaultViewer?.first) }
        val knownPackages = apps.map { it.pkg }.toSet()
        val suspects = AppProbe.suspects(ctx, knownPackages + "com.google.android.gms")
        val churn = (db.meta("idx_owners") ?: "").split(';').mapNotNull {
            val (pkg, n) = it.split('=').takeIf { p -> p.size == 2 } ?: return@mapNotNull null
            n.toIntOrNull()?.let { c -> OwnerChurn(pkg, AppProbe.label(ctx, pkg), c) }
        }.sortedByDescending { it.count }
        val (nameIssues, dateIssues) = nameAndDateIssues(db, now / 1000)

        val totalFiles = db.count("status!=6")
        val bursts = IndexBursts.detect(db.hourBuckets(IndexBursts.BUCKET_MIN), totalFiles)

        val input = ReportInput(
            totalFiles = totalFiles,
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
            apps = apps,
            device = DeviceProbe.collect(ctx, defaultViewer?.second),
            volumeStates = StorageInfo.volumeStates(ctx),
            suspectApps = suspects,
            churnOwners = churn,
            nameIssues = nameIssues,
            dateIssues = dateIssues,
            crashLog = CrashLogReader.read(ctx, knownPackages),
            ownExits = OwnExits.collect(ctx, started),
            systemicReasons = systemic,
            bursts = bursts,
            favorites = db.metaLong("favorites")?.toInt()?.takeIf { it >= 0 },
            previous = if (started > 0) ScanHistory.previous(ctx, started) else null,
            nomediaDirs = db.extraRows("NOMEDIA", 30).map { NomediaDir(it.first, it.second.toInt()) },
            hiddenDirs = db.extraRows("HIDDEN_DIR", 30).map { HiddenDir(it.first, it.third?.toIntOrNull() ?: 0, it.second) },
            thumbEntries = db.extraRows("THUMBS", 1).firstOrNull()?.second?.toInt(),
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

    /** Odd file names (too long, invisible or broken characters) and odd dates (none, future, before 1990). */
    private fun nameAndDateIssues(db: StateDb, nowSec: Long): Pair<IssueSummary, IssueSummary> {
        var nameCount = 0
        val nameKinds = HashMap<String, Int>()
        val nameSamples = ArrayList<String>()
        var dateCount = 0
        val dateKinds = HashMap<String, Int>()
        val dateSamples = ArrayList<String>()
        db.forEachNameAndDate { name, _, added, modified ->
            val issues = NameChecker.issues(name)
            if (issues.isNotEmpty()) {
                nameCount++
                issues.forEach { nameKinds[it.name] = (nameKinds[it.name] ?: 0) + 1 }
                if (nameSamples.size < 5) nameSamples += name.take(60)
            }
            DateChecker.issue(added, modified, nowSec)?.let { why ->
                dateCount++
                dateKinds[why] = (dateKinds[why] ?: 0) + 1
                if (dateSamples.size < 5) dateSamples += name.take(60)
            }
        }
        return IssueSummary(nameCount, nameSamples, nameKinds) to IssueSummary(dateCount, dateSamples, dateKinds)
    }
}
