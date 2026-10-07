package il.gallerydoctor.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyzerTest {
    private fun row(name: String, vararg reasons: Reason, status: Status = Classifier.statusOf(reasons.toList())) =
        ProblemRow(1, "content://x/$name", name, "DCIM/Camera", 1_000_000, "image/jpeg", false, status, reasons.toList())

    private val GB = 1024L * 1024 * 1024

    @Test fun cleanPhoneIsGreenWithBaselineOnly() {
        val a = Analyzer.analyze(ReportInput(totalFiles = 1000, okCount = 1000, imageCount = 900, videoCount = 100))
        assertEquals(Light.GREEN, a.light)
        assertEquals(1, a.hypotheses.size)
        assertEquals("CACHE", a.hypotheses[0].id)
        assertEquals(Confidence.LOW, a.hypotheses[0].confidence) // best remaining guess, honestly labelled low
    }

    @Test fun crasherIsTopRankedAndRed() {
        val input = ReportInput(
            totalFiles = 500, okCount = 498, crasherCount = 2,
            crashers = listOf(row("IMG_1.jpg", Reason.DECODER_CRASH), row("VID_2.mp4", Reason.HANG_TIMEOUT)),
            volumes = listOf(VolumeStat("אחסון פנימי", 64 * GB, 2 * GB, false)),
        )
        val a = Analyzer.analyze(input)
        assertEquals(Light.RED, a.light)
        assertEquals("DECODER_CRASH", a.hypotheses.first().id)
        assertEquals(Confidence.HIGH, a.hypotheses.first().confidence)
        assertTrue(a.hypotheses.first().evidence.joinToString().contains("IMG_1.jpg"))
        assertTrue(a.verdict.contains("קבצים שגורמים למפענח"))
        // backup is the very first step
        assertEquals("BACKUP", a.actions.first().key)
    }

    @Test fun fullStorageIsFlaggedByPercentAndByAbsoluteSpace() {
        val pct = ReportInput(volumes = listOf(VolumeStat("פנימי", 128 * GB, 6 * GB, false)))
        assertTrue(Analyzer.analyze(pct).hypotheses.any { it.id == "STORAGE_FULL" })
        val abs = ReportInput(volumes = listOf(VolumeStat("פנימי", 1000 * GB, 1 * GB, false)))
        assertTrue(Analyzer.analyze(abs).hypotheses.any { it.id == "STORAGE_FULL" })
        val ok = ReportInput(volumes = listOf(VolumeStat("פנימי", 128 * GB, 60 * GB, false)))
        assertFalse(Analyzer.analyze(ok).hypotheses.any { it.id == "STORAGE_FULL" })
    }

    @Test fun failingSdCardRecommendsReseating() {
        val input = ReportInput(
            totalFiles = 1000, ioErrors = 12, intermittentCount = 2, failingVolumes = listOf("כרטיס SD"),
            volumes = listOf(VolumeStat("כרטיס SD", 64 * GB, 30 * GB, true)),
        )
        val a = Analyzer.analyze(input)
        assertEquals("STORAGE_FAILING", a.hypotheses.first().id)
        assertTrue(a.actions.any { it.key == "RESEAT_SD" })
    }

    @Test fun unstableIndexIsHighConfidenceAndSmallChangesAreNot() {
        val big = Analyzer.analyze(ReportInput(indexChanged = 30, indexVanished = 5, indexAppeared = 2))
        assertEquals("UNSTABLE_INDEX", big.hypotheses.first().id)
        assertEquals(Confidence.HIGH, big.hypotheses.first().confidence)
        val small = Analyzer.analyze(ReportInput(indexChanged = 1, indexVanished = 0, indexAppeared = 1))
        assertEquals(Light.GREEN, small.light)
    }

    @Test fun duplicateActionsAreMergedOncePerKey() {
        val input = ReportInput(
            totalFiles = 1000, ghostCount = 150, pendingStuckCount = 3,
            indexChanged = 40, indexVanished = 0, indexAppeared = 0,
        )
        val a = Analyzer.analyze(input)
        assertEquals(1, a.actions.count { it.key == "CLEAR_MEDIA_STORAGE" })
        assertTrue(a.actions.last().key == "RESCAN")
    }

    @Test fun partialScanAndQuickScanAreDisclosed() {
        val a = Analyzer.analyze(ReportInput(scanComplete = false, deepScan = false))
        assertTrue(a.verdict.contains("חלקיות"))
        assertTrue(a.limits.any { it.contains("סריקה מהירה") })
        assertTrue(a.limits.any { it.contains("יומני הקריסה") })
    }

    @Test fun hebrewPluralsAndSizes() {
        assertEquals("קובץ אחד", He.files(1))
        assertEquals("2 קבצים", He.files(2))
        assertEquals("1,234 קבצים", He.files(1234))
        assertEquals("רשומה אחת", He.entries(1))
        assertEquals("1.5 GB", He.bytes((1.5 * GB).toLong()))
        assertEquals("a, b, c ועוד 4", He.names(listOf("a", "b", "c"), 7))
    }

    @Test fun plainTextReportContainsAllSections() {
        val input = ReportInput(totalFiles = 10, okCount = 9, brokenCount = 1, broken = listOf(row("a.jpg", Reason.JPEG_NO_EOI, status = Status.BROKEN)))
        val a = Analyzer.analyze(input)
        val text = ReportDocument.toPlainText(ReportDocument.build(ReportData(input, a)))
        for (s in listOf("דוח בדיקת הגלריה", "הסיבות הסבירות ביותר", "מה לעשות, צעד אחר צעד", "מגבלות הבדיקה", "a.jpg")) {
            assertTrue("missing: $s", text.contains(s))
        }
    }

    @Test fun csvEscapesCommasQuotesAndNewlines() {
        val r = ProblemRow(1, "content://x", "we,ird \"name\".jpg", "DCIM", 5, "image/jpeg", false, Status.BROKEN, listOf(Reason.ZERO_BYTES))
        val line = CsvWriter.row(r)
        assertTrue(line.contains("\"we,ird \"\"name\"\".jpg\""))
        assertEquals(CsvWriter.HEADER.size, CsvWriter.header().split(',').size)
    }
}
