package il.gallerydoctor.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyzerEnvironmentTest {
    private val now = 1_790_000_000_000L // September 2026
    private val day = 86_400_000L

    private fun gallery(
        pkg: String = "com.g", label: String = "Gallery", enabled: Boolean = true, system: Boolean = false,
        updatedDaysAgo: Long = 400, targetSdk: Int = 35, installer: String? = "com.android.vending",
    ) = AppInfo(pkg, label, "1.0", AppRole.GALLERY, now - 900 * day, now - updatedDaysAgo * day, installer, targetSdk, enabled, false, system)

    private fun input(vararg parts: ReportInput.() -> ReportInput): ReportInput =
        parts.fold(ReportInput(generatedAtMillis = now, androidApi = 35)) { acc, f -> acc.f() }

    private fun ids(i: ReportInput) = Analyzer.analyze(i).hypotheses.map { it.id }

    @Test fun cleanEnvironmentAddsNothing() {
        val i = ReportInput(
            generatedAtMillis = now, androidApi = 35,
            apps = listOf(gallery(), AppInfo("com.android.providers.media.module", "Media Storage", "15", AppRole.MEDIA_PROVIDER, system = true)),
            device = DeviceInfo(androidApi = 35, ramTotalMb = 8000, securityPatch = "2026-08-05"),
            volumeStates = listOf(VolumeState("אחסון פנימי", "mounted", false)),
        )
        assertEquals(listOf("CACHE"), ids(i))
    }

    @Test fun realGalleryCrashFromTheLogIsTheTopRankedCause() {
        val entry = CrashLogEntry(
            "com.google.android.apps.photos", "10-07 10:21:33", CrashKind.JAVA,
            "java.lang.OutOfMemoryError: Failed to allocate", listOf("android.graphics.BitmapFactory.nativeDecodeStream(Native Method)"),
        )
        val i = ReportInput(
            generatedAtMillis = now, androidApi = 35, apps = listOf(gallery("com.google.android.apps.photos", "Google Photos")),
            crashLog = CrashLogInfo(permissionGranted = true, readOk = true, related = listOf(entry)),
        )
        val a = Analyzer.analyze(i)
        assertEquals("GALLERY_CRASH_LOG", a.hypotheses.first().id)
        assertEquals(Light.RED, a.light)
        assertTrue(a.hypotheses.first().evidence.joinToString().contains("Google Photos"))
        assertTrue(a.actions.any { it.key == "FREE_RAM" })
    }

    @Test fun crashLogReadButNothingFoundIsStatedHonestly() {
        val i = ReportInput(crashLog = CrashLogInfo(permissionGranted = true, readOk = true))
        val baseline = Analyzer.analyze(i).hypotheses.single()
        assertTrue(baseline.evidence.joinToString().contains("לא נמצאה בו קריסה"))
        assertTrue(Analyzer.analyze(i).limits.first().contains("קראה את יומן הקריסות"))
    }

    @Test fun missingLogPermissionTellsHowToGrantIt() {
        val a = Analyzer.analyze(ReportInput())
        assertTrue(a.hypotheses.single().evidence.joinToString().contains("pm grant il.gallerydoctor"))
    }

    @Test fun dontKeepActivitiesIsRedAndTheFix() {
        val i = ReportInput(device = DeviceInfo(alwaysFinishActivities = true))
        val a = Analyzer.analyze(i)
        assertEquals("DONT_KEEP_ACTIVITIES", a.hypotheses.first().id)
        assertEquals(Light.RED, a.light)
        assertTrue(a.actions.any { it.key == "DISABLE_DONT_KEEP" })
    }

    @Test fun unhealthyRemovableCardIsFlagged() {
        val bad = ReportInput(volumeStates = listOf(VolumeState("כרטיס SD", "unmountable", true)))
        assertEquals("VOLUME_STATE", Analyzer.analyze(bad).hypotheses.first().id)
        assertTrue(Analyzer.analyze(bad).actions.any { it.key == "RESEAT_SD" })
        val ok = ReportInput(volumeStates = listOf(VolumeState("כרטיס SD", "mounted", true)))
        assertFalse("VOLUME_STATE" in ids(ok))
    }

    @Test fun disabledMediaStorageIsCritical() {
        val i = ReportInput(apps = listOf(AppInfo("com.android.providers.media.module", "Media Storage", "15", AppRole.MEDIA_PROVIDER, enabled = false)))
        val a = Analyzer.analyze(i)
        assertEquals("MEDIA_PROVIDER_DISABLED", a.hypotheses.first().id)
        assertEquals(Light.RED, a.light)
    }

    @Test fun galleryAppProblems() {
        assertTrue("GALLERY_APP_STATE" in ids(input({ copy(apps = listOf(gallery(enabled = false))) })))
        assertTrue("GALLERY_APP_STATE" in ids(input({ copy(apps = listOf(gallery(updatedDaysAgo = 3))) })))
        assertTrue("GALLERY_APP_STATE" in ids(input({ copy(apps = listOf(gallery(targetSdk = 28))) })))
        assertTrue("GALLERY_APP_STATE" in ids(input({ copy(apps = listOf(gallery(updatedDaysAgo = 900))) })))
        assertTrue("GALLERY_APP_STATE" in ids(input({ copy(apps = listOf(gallery(installer = null))) })))
        // a healthy, recently-ish updated Play-installed gallery is not an issue
        assertFalse("GALLERY_APP_STATE" in ids(input({ copy(apps = listOf(gallery())) })))
        // system galleries are not blamed for being old or sideloaded
        assertFalse("GALLERY_APP_STATE" in ids(input({ copy(apps = listOf(gallery(system = true, updatedDaysAgo = 900, installer = null))) })))
    }

    @Test fun twoGalleriesMentionTheDefault() {
        val i = input({
            copy(
                apps = listOf(gallery("com.a", "Photos"), gallery("com.b", "OEM Gallery")),
                device = DeviceInfo(defaultImageViewer = "OEM Gallery"),
            )
        })
        val h = Analyzer.analyze(i).hypotheses.first { it.id == "GALLERY_APP_STATE" }
        assertTrue(h.evidence.joinToString().contains("ברירת המחדל לפתיחת תמונות: OEM Gallery"))
    }

    @Test fun backgroundWriterIsNamedWhenItWroteMostOfTheChurn() {
        val i = ReportInput(
            indexChanged = 30, indexVanished = 0, indexAppeared = 10,
            churnOwners = listOf(OwnerChurn("com.cleaner", "Phone Cleaner", 33), OwnerChurn("com.whatsapp", "WhatsApp", 7)),
        )
        val a = Analyzer.analyze(i)
        val h = a.hypotheses.first { it.id == "UNSTABLE_INDEX" }
        assertTrue(h.evidence.joinToString().contains("Phone Cleaner (33)"))
        assertTrue(a.actions.any { it.key == "STOP_TOP_WRITER" && it.text.contains("Phone Cleaner") })
        assertTrue(h.score >= 90)
    }

    @Test fun suspectAppsRankHigherWhenTheIndexMisbehaves() {
        val apps = listOf(SuspectApp("com.cleaner", "Phone Cleaner", SuspectCategory.CLEANER))
        val calm = Analyzer.analyze(ReportInput(suspectApps = apps)).hypotheses.first { it.id == "SUSPECT_APPS" }
        val noisy = Analyzer.analyze(ReportInput(suspectApps = apps, ghostCount = 50)).hypotheses.first { it.id == "SUSPECT_APPS" }
        assertTrue(noisy.score > calm.score)
        assertTrue(calm.evidence.joinToString().contains("Phone Cleaner"))
    }

    @Test fun oddNamesAndDates() {
        val i = ReportInput(
            nameIssues = IssueSummary(3, listOf("a.jpg"), mapOf(NameIssue.TOO_LONG.name to 3)),
            dateIssues = IssueSummary(2, listOf("b.jpg"), mapOf("תאריך בעתיד" to 2)),
        )
        val h = Analyzer.analyze(i).hypotheses.first { it.id == "FILE_NAMES" }
        assertTrue(h.score >= 50)
        assertTrue(h.evidence.joinToString().contains("שם ארוך מדי"))
        assertTrue(h.evidence.joinToString().contains("תאריך בעתיד"))
    }

    @Test fun lowMemoryPhoneWithHeavyFilesAndKills() {
        val i = ReportInput(
            device = DeviceInfo(ramTotalMb = 2800, lowMemoryNow = true),
            anomalyCounts = mapOf(Anomaly.IMAGE_OVER_50MP to 4),
            ownExits = OwnExitInfo(lowMemoryKills = 3),
        )
        val h = Analyzer.analyze(i).hypotheses.first { it.id == "LOW_RESOURCES" }
        assertTrue(h.score >= 80)
        assertTrue(h.evidence.joinToString().contains("הרג 3 פעמים"))
    }

    @Test fun powerSaveAloneIsLow() {
        val h = Analyzer.analyze(ReportInput(device = DeviceInfo(ramTotalMb = 8000, powerSave = true))).hypotheses.first { it.id == "LOW_RESOURCES" }
        assertTrue(h.confidence == Confidence.LOW)
        assertTrue(Analyzer.analyze(ReportInput(device = DeviceInfo(ramTotalMb = 8000, powerSave = true))).actions.any { it.key == "DISABLE_POWER_SAVE" })
    }

    @Test fun outdatedSystem() {
        val old = ReportInput(generatedAtMillis = now, device = DeviceInfo(androidApi = 33, securityPatch = "2024-01-05", ramTotalMb = 8000))
        assertTrue("SYSTEM_OUTDATED" in ids(old))
        val fresh = ReportInput(generatedAtMillis = now, device = DeviceInfo(androidApi = 35, securityPatch = "2026-08-05", ramTotalMb = 8000))
        assertFalse("SYSTEM_OUTDATED" in ids(fresh))
    }

    @Test fun nativeCrashesOfTheWorkersAreExplained() {
        val i = ReportInput(
            crasherCount = 2, crashers = listOf(
                ProblemRow(1, "u", "a.mp4", "x", 1, null, true, Status.CRASHER, listOf(Reason.DECODER_CRASH)),
            ),
            ownExits = OwnExitInfo(nativeCrashes = 4, signals = mapOf("SIGSEGV" to 3, "SIGABRT" to 1)),
        )
        val text = Analyzer.analyze(i).hypotheses.first { it.id == "DECODER_CRASH" }.evidence.joinToString()
        assertTrue(text.contains("4 קריסות נייטיב"))
        assertTrue(text.contains("SIGSEGV (3)"))
    }

    @Test fun reportTextContainsTheNewSections() {
        val i = ReportInput(
            generatedAtMillis = now, apps = listOf(gallery()), suspectApps = listOf(SuspectApp("c", "Cleaner", SuspectCategory.CLEANER)),
            device = DeviceInfo(manufacturer = "Xiaomi", model = "Redmi", androidRelease = "15", androidApi = 35, ramTotalMb = 6000),
        )
        val text = ReportDocument.toPlainText(ReportDocument.build(ReportData(i, Analyzer.analyze(i))))
        for (s in listOf("אפליקציות גלריה ואחסון מדיה", "אפליקציות אחרות שיכולות להשפיע", "המכשיר וההגדרות", "יומן הקריסות של המערכת", "Xiaomi Redmi")) {
            assertTrue("missing: $s", text.contains(s))
        }
    }
}
