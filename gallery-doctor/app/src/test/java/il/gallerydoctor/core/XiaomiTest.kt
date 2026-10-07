package il.gallerydoctor.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XiaomiTest {
    private val GB = 1024L * 1024 * 1024

    private fun xiaomi(model: String = "15T Pro") = DeviceInfo(manufacturer = "Xiaomi", model = model, androidApi = 36, ramTotalMb = 12_000)

    @Test fun xiaomiFamilyIsDetected() {
        for (m in listOf("Xiaomi", "xiaomi", "Redmi", "POCO", "BLACKSHARK")) assertTrue(DeviceInfo(manufacturer = m).isXiaomiFamily)
        for (m in listOf("samsung", "Google", "OnePlus", "")) assertFalse(DeviceInfo(manufacturer = m).isXiaomiFamily)
    }

    @Test fun xiaomiGetsBackgroundLimitAdviceOthersDont() {
        val a = Analyzer.analyze(ReportInput(device = xiaomi()))
        assertTrue(a.hypotheses.any { it.id == "XIAOMI_BACKGROUND" })
        assertTrue(a.actions.any { it.key == "XIAOMI_BATTERY" && it.text.contains("ללא הגבלות") })
        assertFalse(a.actions.any { it.key == "XIAOMI_RAM_EXTENSION" }) // storage is fine here
        val other = Analyzer.analyze(ReportInput(device = DeviceInfo(manufacturer = "Google", model = "Pixel", ramTotalMb = 12_000)))
        assertFalse(other.hypotheses.any { it.id == "XIAOMI_BACKGROUND" })
    }

    @Test fun xiaomiAdviceIsHonestThatItCannotSeeTheSetting() {
        val h = Analyzer.analyze(ReportInput(device = xiaomi())).hypotheses.first { it.id == "XIAOMI_BACKGROUND" }
        assertEquals(Confidence.LOW, h.confidence)
        assertTrue(h.evidence.joinToString().contains("המלצה לבדיקה ולא ממצא"))
    }

    @Test fun memoryExtensionIsMentionedOnlyWhenStorageIsFull() {
        val full = ReportInput(device = xiaomi(), volumes = listOf(VolumeStat("אחסון פנימי", 479 * GB, 700L * 1024 * 1024, false)))
        val a = Analyzer.analyze(full)
        assertTrue(a.actions.any { it.key == "XIAOMI_RAM_EXTENSION" })
        // and it can never outrank the real problem
        assertEquals("STORAGE_FULL", a.hypotheses.first().id)
    }

    @Test fun memoryKillsRaiseTheXiaomiHypothesisToMedium() {
        val h = Analyzer.analyze(ReportInput(device = xiaomi(), ownExits = OwnExitInfo(lowMemoryKills = 4)))
            .hypotheses.first { it.id == "XIAOMI_BACKGROUND" }
        assertEquals(Confidence.MEDIUM, h.confidence)
    }

    // ---------- 50MP sensors ----------

    @Test fun aStandard50MpPhotoIsNotHeavyButA108MpOneIs() {
        fun img(w: Int, h: Int) = MediaFacts(false, 20_000_000, w, h, -1, 0f, 0)
        assertFalse(Anomaly.IMAGE_OVER_50MP in AnomalyDetector.detect(img(8192, 6144)))  // 50.3 MP: normal 50MP sensor
        assertFalse(Anomaly.IMAGE_OVER_50MP in AnomalyDetector.detect(img(8160, 6120)))
        assertTrue(Anomaly.IMAGE_OVER_50MP in AnomalyDetector.detect(img(12000, 9000)))  // 108 MP
        assertTrue(Anomaly.IMAGE_OVER_50MP in AnomalyDetector.detect(img(9000, 6000)))   // 54 MP
    }

    @Test fun miCloudIsKnownAsASyncApp() {
        assertEquals(SuspectCategory.BACKUP_SYNC, AppClassifier.classify("com.miui.cloudservice", "Mi Cloud", false, true))
    }
}
