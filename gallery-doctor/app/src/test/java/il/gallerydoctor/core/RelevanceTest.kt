package il.gallerydoctor.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lessons from a real report (79,997 files on a 479GB phone) where one broken check flagged 99.9% of images as corrupt. */
class RelevanceTest {
    private val GB = 1024L * 1024 * 1024

    // ---------- a failing check must never turn a library into "77,000 corrupt files" ----------

    @Test fun aCheckThatFailsOnNearlyEveryImageIsNotCountedAsCorruption() {
        val systemic = SystemicFailure.detect(
            mapOf(Reason.BOUNDS_FAIL to 77_671, Reason.MAGIC_MISMATCH to 38, Reason.ZERO_TAIL to 3, Reason.FRAME_MID_FAIL to 1),
            images = 77_696, videos = 2_301,
        )
        assertEquals(setOf(Reason.BOUNDS_FAIL), systemic.keys)
        assertEquals(77_671, systemic[Reason.BOUNDS_FAIL])
    }

    @Test fun scatteredRealCorruptionIsKept() {
        // 40 truncated JPEGs among 10,000 photos is plausible damage, not a broken check
        assertTrue(SystemicFailure.detect(mapOf(Reason.JPEG_NO_EOI to 40), 10_000, 100).isEmpty())
        // too few files to judge
        assertTrue(SystemicFailure.detect(mapOf(Reason.BOUNDS_FAIL to 30), 40, 0).isEmpty())
    }

    @Test fun realStorageAndCrashFindingsAreNeverNeutralised() {
        val all = mapOf(
            Reason.IO_READ_ERROR to 900, Reason.INTERMITTENT_IO to 900, Reason.ZERO_BYTES to 900,
            Reason.DECODER_CRASH to 900, Reason.HANG_TIMEOUT to 900, Reason.OPEN_FAILED to 900,
        )
        assertTrue(SystemicFailure.detect(all, 1_000, 1_000).isEmpty())
    }

    @Test fun videoReasonsAreJudgedAgainstVideosOnly() {
        // every one of 120 videos fails frame extraction, although videos are 1% of the library
        assertEquals(setOf(Reason.FRAME_MID_FAIL), SystemicFailure.detect(mapOf(Reason.FRAME_MID_FAIL to 120), 11_000, 120).keys)
        // 1 failing video of 2,301 is a real finding
        assertTrue(SystemicFailure.detect(mapOf(Reason.FRAME_MID_FAIL to 1), 77_696, 2_301).isEmpty())
    }

    @Test fun metaRoundTrip() {
        val m = mapOf(Reason.BOUNDS_FAIL to 77_671, Reason.EXIF_ERROR to 400)
        assertEquals(m, SystemicFailure.fromMeta(SystemicFailure.toMeta(m)))
        assertTrue(SystemicFailure.fromMeta(null).isEmpty())
        assertTrue(SystemicFailure.fromMeta("garbage;X=1;BOUNDS_FAIL=x").isEmpty())
    }

    @Test fun reportExplainsWhatWasNotCounted() {
        val a = Analyzer.analyze(ReportInput(systemicReasons = mapOf(Reason.BOUNDS_FAIL to 77_671)))
        val line = a.limits.first { it.contains("77,671") }
        assertTrue(line.contains("לא נספר כקבצים פגומים"))
    }

    // ---------- motion photos ----------

    @Test fun motionPhotosAreNotHeavyImages() {
        assertTrue(AnomalyDetector.isMotionPhotoName("MVIMG_20260922_155341.jpg"))
        assertTrue(AnomalyDetector.isMotionPhotoName("PXL_20240101_123456789.MP.jpg"))
        assertFalse(AnomalyDetector.isMotionPhotoName("IMG_20240101.jpg"))
        val facts = MediaFacts(false, 33_000_000, 2160, 3840, -1, 0f, 0, motionPhoto = true)
        assertFalse(Anomaly.IMAGE_OVER_30MB in AnomalyDetector.detect(facts))
        assertTrue(Anomaly.IMAGE_OVER_30MB in AnomalyDetector.detect(facts.copy(motionPhoto = false)))
    }

    // ---------- relevance of the advice ----------

    private val fullPhone = ReportInput(
        totalFiles = 80_000, imageCount = 77_700, videoCount = 2_300,
        volumes = listOf(VolumeState("x", "mounted", false).let { VolumeStat("אחסון פנימי", 479 * GB, 718L * 1024 * 1024, false) }),
        imageBytes = 118 * GB, videoBytes = 305 * GB,
        apps = listOf(AppInfo("com.google.android.apps.photos", "Photos", "7.9", AppRole.GALLERY)),
    )

    @Test fun freeSpaceTargetScalesWithTheStorageAndPhotosFreeUpIsOffered() {
        val a = Analyzer.analyze(fullPhone)
        val free = a.actions.first { it.key == "FREE_SPACE" }
        assertTrue(free.text, free.text.contains("23.9 GB")) // 5% of 479GB, not "2GB"
        val photos = a.actions.indexOfFirst { it.key == "PHOTOS_FREE_UP" }
        assertTrue(photos >= 0 && photos < a.actions.indexOfFirst { it.key == "FREE_SPACE" })
        assertTrue(a.hypotheses.first { it.id == "STORAGE_FULL" }.evidence.joinToString().contains("סרטונים"))
    }

    @Test fun smallPhoneStillGetsTheTwoGigabyteTarget() {
        val a = Analyzer.analyze(ReportInput(volumes = listOf(VolumeStat("פנימי", 32 * GB, 1 * GB, false))))
        assertTrue(a.actions.first { it.key == "FREE_SPACE" }.text.contains("2.0 GB"))
    }

    private fun row(folder: String, name: String = "a.jpg") =
        ProblemRow(1, "u", name, folder, 10, "image/jpeg", false, Status.OK, emptyList())

    @Test fun albumCopiesAreNamedAsTheSourceOfDuplicates() {
        val groups = (1..10).map { DupGroup(listOf(row("DCIM/Camera"), row("Pictures/Gallery/owner/אלבום")), 1_000_000) }
        val a = Analyzer.analyze(fullPhone.copy(dupGroups = groups, dupGroupCount = 10, reclaimableBytes = 5 * GB))
        assertTrue(a.hypotheses.first { it.id == "STORAGE_FULL" }.evidence.joinToString().contains("Pictures/Gallery/owner"))
    }

    @Test fun cameraFolderGetsATrimAdviceNotASplitAdvice() {
        val i = ReportInput(heavyFolders = listOf(FolderStat("DCIM/Camera", 26_737, 361 * GB)))
        val a = Analyzer.analyze(i)
        assertTrue(a.hypotheses.any { it.id == "HEAVY_FOLDERS" })
        assertTrue(a.actions.any { it.key == "TRIM_CAMERA" })
        assertFalse(a.actions.any { it.key == "SPLIT_FOLDERS" })
    }

    @Test fun extensionMismatchAloneIsHarmlessAndRankedLow() {
        val i = ReportInput(
            totalFiles = 1000, suspectCount = 38, reasonHistogram = mapOf(Reason.MAGIC_MISMATCH to 38),
            suspect = listOf(ProblemRow(1, "u", "x.jpg", "Pictures/Instagram", 1, "image/jpeg", false, Status.SUSPECT, listOf(Reason.MAGIC_MISMATCH))),
        )
        val a = Analyzer.analyze(i)
        val h = a.hypotheses.first { it.id == "EXTENSION_MISMATCH" }
        assertEquals(Confidence.LOW, h.confidence)
        assertEquals(Light.GREEN, a.light)
        assertFalse(a.hypotheses.any { it.id == "BROKEN_FILES" })
        assertTrue(h.evidence.joinToString().contains("לא מזיק"))
    }

    @Test fun realSuspectsStillCountWhenMixedWithMismatches() {
        val i = ReportInput(
            totalFiles = 1000, suspectCount = 41, reasonHistogram = mapOf(Reason.MAGIC_MISMATCH to 38, Reason.ZERO_TAIL to 3),
        )
        val h = Analyzer.analyze(i).hypotheses.first { it.id == "BROKEN_FILES" }
        assertTrue(h.evidence.joinToString().contains("3 קבצים חשודים"))
    }

    @Test fun cleanResultsAreStatedInsteadOfOmitted() {
        val i = ReportInput(
            totalFiles = 79_997, deepScan = true, scanComplete = true, crasherCount = 0, ghostCount = 0,
            indexChanged = 0, indexVanished = 0, indexAppeared = 0, probedFiles = 380,
            volumes = listOf(VolumeStat("פנימי", 128 * GB, 90 * GB, false)),
        )
        val lines = EnvLines.healthyLines(i).joinToString("\n")
        for (s in listOf("אף קובץ לא הקריס", "אין רשומות רפאים", "אינדקס המדיה יציב", "אין קבצי מדיה שחסרים", "שגיאות קריאה", "מספיק מקום")) {
            assertTrue("missing: $s", lines.contains(s))
        }
        assertFalse(EnvLines.healthyLines(i.copy(crasherCount = 2)).joinToString().contains("אף קובץ לא הקריס"))
        assertFalse(EnvLines.healthyLines(i.copy(ghostCount = 5)).joinToString().contains("אין רשומות רפאים"))
        assertTrue(EnvLines.healthyLines(ReportInput()).isEmpty())
    }

    @Test fun healthySectionIsInTheTextReport() {
        val i = ReportInput(totalFiles = 10, ghostCount = 0)
        val text = ReportDocument.toPlainText(ReportDocument.build(ReportData(i, Analyzer.analyze(i))))
        assertTrue(text.contains("מה נבדק ונמצא תקין"))
    }
}
