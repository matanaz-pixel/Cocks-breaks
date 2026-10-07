package il.gallerydoctor.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuityTest {
    private fun hour(day: Int, h: Int) = (day * 24L + h)

    @Test
    fun adjacentHoursMergeIntoOneBurst() {
        val buckets = listOf(HourBucket(hour(100, 10), 3000, 2900), HourBucket(hour(100, 11), 2500, 2400), HourBucket(hour(100, 12), 1800, 1000))
        val bursts = IndexBursts.detect(buckets, total = 10_000)
        assertEquals(1, bursts.size)
        assertEquals(7300, bursts[0].count)
        assertEquals(73, bursts[0].sharePercent)
        assertTrue(bursts[0].agedPercent > 50)
    }

    @Test
    fun separateDaysAreSeparateBursts() {
        val buckets = listOf(HourBucket(hour(100, 10), 6000, 0), HourBucket(hour(140, 3), 900, 800), HourBucket(hour(150, 22), 700, 650))
        val bursts = IndexBursts.detect(buckets, total = 10_000)
        assertEquals(3, bursts.size)
        assertEquals(60, IndexBursts.libraryBurst(bursts)!!.sharePercent)
    }

    @Test
    fun smallActivityIsNotABurst() {
        // 120 files in an hour on a big library is a busy day, not a rebuild
        assertTrue(IndexBursts.detect(listOf(HourBucket(hour(5, 5), 120, 0)), total = 80_000).isEmpty())
        assertNull(IndexBursts.libraryBurst(emptyList()))
    }

    @Test
    fun snapshotRoundTrips() {
        val s = ScanSnapshot(1_700_000_000_000, 79_997, 12, 3, 2, 718L * 1024 * 1024)
        assertEquals(s, ScanSnapshot.decode(s.encode()))
        assertNull(ScanSnapshot.decode("garbage"))
        assertNull(ScanSnapshot.decode(s.copy(favorites = null).encode())!!.favorites)
    }

    private val gallery = AppInfo("com.miui.gallery", "Gallery", "1.0", AppRole.GALLERY)
    private val full = VolumeStat("אחסון פנימי", 479L shl 30, 718L shl 20, false)

    private fun ids(i: ReportInput) = Analyzer.analyze(i).hypotheses.map { it.id }

    @Test
    fun repeatedRebuildsAreReported() {
        val i = ReportInput(
            totalFiles = 80_000, apps = listOf(gallery), volumes = listOf(full),
            bursts = listOf(
                IndexBurst(1_000_000, 1_003_599, 60_000, 10, 75),
                IndexBurst(5_000_000, 5_003_599, 4_000, 3_900, 5),
                IndexBurst(9_000_000, 9_003_599, 3_000, 2_900, 4),
            ),
        )
        val h = Analyzer.analyze(i).hypotheses.first { it.id == "INDEX_REBUILDS" }
        assertEquals(Confidence.MEDIUM, h.confidence)
        assertTrue(h.evidence.any { it.contains("75%") })
        assertTrue(h.actions.any { it.key == "AB_TEST" })
    }

    @Test
    fun aSingleTransferBurstIsOnlyInformation() {
        val i = ReportInput(totalFiles = 80_000, bursts = listOf(IndexBurst(1_000_000, 1_003_599, 70_000, 0, 87)))
        assertEquals(Confidence.LOW, Analyzer.analyze(i).hypotheses.first { it.id == "INDEX_REBUILDS" }.confidence)
    }

    @Test
    fun shrinkingIndexAndLostFavoritesAreEvidence() {
        val prev = ScanSnapshot(1, 80_000, 40, 0, 0, 0)
        val i = ReportInput(totalFiles = 60_000, favorites = 3, previous = prev)
        val h = Analyzer.analyze(i).hypotheses.first { it.id == "INDEX_SHRANK" }
        assertEquals(Confidence.MEDIUM, h.confidence)
        assertTrue(h.evidence.any { it.contains("40") && it.contains("3") })
        // a normal day: nothing lost
        assertTrue("INDEX_SHRANK" !in ids(ReportInput(totalFiles = 79_950, favorites = 40, previous = prev)))
        // tiny library: deleting 10 photos is not "shrinking"
        assertTrue("INDEX_SHRANK" !in ids(ReportInput(totalFiles = 90, previous = ScanSnapshot(1, 100, null, 0, 0, 0))))
    }

    @Test
    fun nomediaInCameraFolderIsStrongButAppFoldersAreWeak() {
        val camera = ReportInput(nomediaDirs = listOf(NomediaDir("/storage/emulated/0/DCIM/Camera", 4_000)))
        assertEquals(Confidence.MEDIUM, Analyzer.analyze(camera).hypotheses.first { it.id == "NOMEDIA_HIDDEN" }.confidence)
        val app = ReportInput(nomediaDirs = listOf(NomediaDir("/storage/emulated/0/WhatsApp/Media/WhatsApp Stickers", 40)))
        assertEquals(Confidence.LOW, Analyzer.analyze(app).hypotheses.first { it.id == "NOMEDIA_HIDDEN" }.confidence)
        assertTrue(Continuity.isMainMediaDir("/storage/emulated/0/DCIM/Camera"))
        assertTrue(!Continuity.isMainMediaDir("/storage/emulated/0/Android/media/com.x"))
    }

    @Test
    fun privateDatabaseIsInferredByEliminationAndNeverFromAFileLevelCause() {
        val i = ReportInput(
            totalFiles = 80_000, apps = listOf(gallery), volumes = listOf(full),
            heavyFolders = listOf(FolderStat("DCIM/Camera", 26_737, 1L shl 30)),
            bursts = listOf(IndexBurst(1, 3600, 60_000, 0, 75)),
        )
        val h = Analyzer.analyze(i).hypotheses.first { it.id == "GALLERY_PRIVATE_DB" }
        assertEquals(70, h.score)
        assertTrue(h.evidence.any { it.contains("מהשלילה") })
        assertTrue(h.actions.any { it.key == "AB_TEST" } && h.actions.any { it.key == "CLEAR_GALLERY_DATA" })
        val crash = i.copy(crasherCount = 2, crashers = emptyList())
        assertTrue("GALLERY_PRIVATE_DB" !in ids(crash))
        assertTrue("GALLERY_PRIVATE_DB" !in ids(ReportInput(totalFiles = 10)))
    }

    @Test
    fun guideComesInTheSafeOrderAndIsHonest() {
        val i = ReportInput(
            apps = listOf(gallery), volumes = listOf(full), videoBytes = 305L shl 30, imageBytes = 50L shl 30,
            device = DeviceInfo(manufacturer = "Xiaomi", model = "24117RK2CG"),
        )
        val steps = RecoveryGuide.steps(i)
        assertTrue(steps.first().title.contains("גיבוי"))
        val order = steps.map { it.title }
        assertTrue(order.indexOfFirst { it.contains("הכרעה") } < order.indexOfFirst { it.contains("איפוס אחסון המדיה") })
        assertTrue(order.indexOfFirst { it.contains("איפוס אחסון המדיה") } < order.indexOfFirst { it.contains("איפוס הגלריה") })
        assertTrue(steps.joinToString { it.body }.contains("ייתכן") && steps.joinToString { it.body }.contains("לא ניתן לאמת"))
        assertNotNull(steps.firstOrNull { it.body.contains("סרטונים") })
    }

    @Test
    fun documentAndLinesContainTheNewSections() {
        val i = ReportInput(totalFiles = 5, apps = listOf(gallery), favorites = 0, previous = null)
        val data = ReportData(i, Analyzer.analyze(i))
        val text = ReportDocument.toPlainText(ReportDocument.build(data))
        assertTrue(text.contains("מדריך: שחזור וניקוי"))
        assertTrue(text.contains("שינויים באינדקס ובין סריקות"))
        assertTrue(text.contains("מועדפים באינדקס המדיה"))
    }
}
