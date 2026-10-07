package il.gallerydoctor.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvironmentTest {
    // ---------- file names and dates ----------

    @Test fun normalNamesAreFine() {
        assertTrue(NameChecker.issues("IMG_20240101_123456.jpg").isEmpty())
        assertTrue(NameChecker.issues("תמונה מהחתונה.jpg").isEmpty())
    }

    @Test fun problematicNames() {
        assertTrue(NameIssue.TOO_LONG in NameChecker.issues("a".repeat(250) + ".jpg"))
        // 100 Hebrew letters = 200 bytes + extension: too long in bytes although short in characters
        assertTrue(NameIssue.TOO_LONG in NameChecker.issues("א".repeat(100) + ".jpg"))
        assertTrue(NameIssue.INVISIBLE_CHARS in NameChecker.issues("photo‮gpj.exe"))
        assertTrue(NameIssue.INVISIBLE_CHARS in NameChecker.issues("pic\u0007.jpg"))
        assertTrue(NameIssue.EDGE_SPACE_DOT in NameChecker.issues("photo.jpg "))
        assertTrue(NameIssue.EDGE_SPACE_DOT in NameChecker.issues("photo."))
        assertTrue(NameIssue.BROKEN_ENCODING in NameChecker.issues("bad�name.jpg"))
        assertTrue(NameIssue.BROKEN_ENCODING in NameChecker.issues("lone\uD83Dsurrogate.jpg"))
        assertTrue(NameChecker.issues("emoji 😀 ok.jpg").isEmpty())
    }

    @Test fun dates() {
        val now = 1_760_000_000L
        assertNull(DateChecker.issue(now - 1000, now - 500, now))
        assertEquals("תאריך בעתיד", DateChecker.issue(now + 10 * 86_400L, now, now))
        assertEquals("אין תאריך", DateChecker.issue(0, 0, now))
        assertEquals("תאריך לפני 1990", DateChecker.issue(100, now, now))
    }

    // ---------- apps ----------

    @Test fun appClassification() {
        assertEquals(SuspectCategory.CLEANER, AppClassifier.classify("com.piriform.ccleaner", "CCleaner", false, false))
        assertEquals(SuspectCategory.CLEANER, AppClassifier.classify("com.some.app", "Super Phone Booster", false, false))
        assertEquals(SuspectCategory.BACKUP_SYNC, AppClassifier.classify("com.dropbox.android", "Dropbox", false, false))
        assertEquals(SuspectCategory.FILE_MANAGER, AppClassifier.classify("com.alphainventor.filemanager", "File Manager", false, false))
        assertEquals(SuspectCategory.ALL_FILES_ACCESS, AppClassifier.classify("com.random.tool", "Random Tool", true, false))
        assertNull(AppClassifier.classify("com.random.tool", "Random Tool", false, false))
        // ordinary system apps are not suspects, even with all-files access
        assertNull(AppClassifier.classify("com.android.settings", "Settings", true, true))
    }

    // ---------- crash log ----------

    private val javaCrash = listOf(
        "10-07 10:21:33.456  4321  4321 E AndroidRuntime: FATAL EXCEPTION: main",
        "10-07 10:21:33.456  4321  4321 E AndroidRuntime: Process: com.google.android.apps.photos, PID: 4321",
        "10-07 10:21:33.456  4321  4321 E AndroidRuntime: java.lang.OutOfMemoryError: Failed to allocate a 33554448 byte allocation",
        "10-07 10:21:33.456  4321  4321 E AndroidRuntime: \tat android.graphics.BitmapFactory.nativeDecodeStream(Native Method)",
        "10-07 10:21:33.456  4321  4321 E AndroidRuntime: \tat android.graphics.BitmapFactory.decodeStream(BitmapFactory.java:700)",
    )
    private val nativeCrash = listOf(
        "10-07 10:25:01.100  5600  5678 F libc    : Fatal signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x0 in tid 5678 (Thread-5), pid 5600 (com.sec.android.gallery3d)",
        "10-07 10:25:02.300  9998  9998 F DEBUG   : pid: 5600, tid: 5678, name: Thread-5  >>> com.sec.android.gallery3d <<<",
        "10-07 10:25:02.300  9998  9998 F DEBUG   : signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x0",
        "10-07 10:25:02.300  9998  9998 F DEBUG   :       #00 pc 0000000000012345  /system/lib64/libhwui.so (SkCodec::getPixels+120)",
        "10-07 10:25:02.300  9998  9998 F DEBUG   :       #01 pc 0000000000054321  /system/lib64/libandroid_runtime.so",
    )
    private val sqliteCrash = listOf(
        "10-07 11:00:00.000  777  777 E AndroidRuntime: FATAL EXCEPTION: ModernMediaScanner",
        "10-07 11:00:00.000  777  777 E AndroidRuntime: Process: com.google.android.providers.media.module, PID: 777",
        "10-07 11:00:00.000  777  777 E AndroidRuntime: android.database.sqlite.SQLiteDatabaseCorruptException: database disk image is malformed",
        "10-07 11:00:00.000  777  777 E AndroidRuntime: Caused by: android.database.sqlite.SQLiteException: no such table",
    )

    @Test fun parsesJavaCrash() {
        val e = CrashLogParser.parse(javaCrash).single()
        assertEquals("com.google.android.apps.photos", e.pkg)
        assertEquals(CrashKind.JAVA, e.kind)
        assertTrue(e.headline, e.headline.startsWith("java.lang.OutOfMemoryError"))
        assertEquals(2, e.frames.size)
        assertEquals("10-07 10:21:33", e.time)
        assertEquals(CrashInsight.OUT_OF_MEMORY, CrashInsights.classify(e))
    }

    @Test fun parsesNativeCrashOnceWithTheRicherEntry() {
        val e = CrashLogParser.parse(nativeCrash).single()
        assertEquals("com.sec.android.gallery3d", e.pkg)
        assertEquals(CrashKind.NATIVE, e.kind)
        assertTrue(e.frames.first().startsWith("libhwui.so"))
        assertEquals(CrashInsight.NATIVE_DECODER, CrashInsights.classify(e))
    }

    @Test fun parsesDatabaseCrashWithCause() {
        val e = CrashLogParser.parse(sqliteCrash).single()
        assertEquals("com.google.android.providers.media.module", e.pkg)
        assertTrue(e.rootCause!!.contains("SQLiteException"))
        assertEquals(CrashInsight.DATABASE, CrashInsights.classify(e))
    }

    @Test fun parsesMixedLogAndIgnoresNoise() {
        val noise = listOf("--------- beginning of crash", "garbage line", "")
        val all = noise + javaCrash + nativeCrash + sqliteCrash
        val entries = CrashLogParser.parse(all)
        assertEquals(3, entries.size)
        assertEquals(listOf("10-07 10:21:33", "10-07 10:25:02", "10-07 11:00:00"), entries.map { it.time })
    }

    @Test fun insightsForMissingFileAndPermission() {
        fun e(h: String) = CrashLogEntry("x", "t", CrashKind.JAVA, h)
        assertEquals(CrashInsight.MISSING_FILE, CrashInsights.classify(e("java.io.FileNotFoundException: /sdcard/a.jpg: open failed: ENOENT")))
        assertEquals(CrashInsight.PERMISSION, CrashInsights.classify(e("java.lang.SecurityException: Permission Denial: reading")))
        assertEquals(CrashInsight.OTHER, CrashInsights.classify(e("java.lang.IllegalStateException: boom")))
    }

    // ---------- plain-Hebrew lines ----------

    @Test fun crashLogLinesExplainTheMissingPermission() {
        val lines = EnvLines.crashLogLines(ReportInput())
        assertTrue(lines.single().contains("adb shell pm grant il.gallerydoctor android.permission.READ_LOGS"))
    }

    @Test fun appLinesShowStateAtAGlance() {
        val line = EnvLines.appLines(
            listOf(AppInfo("com.x.gallery", "Gallery X", "1.2", AppRole.GALLERY, installer = "com.android.vending", enabled = false, defaultViewer = true)),
        ).single()
        assertTrue(line.contains("Google Play"))
        assertTrue(line.contains("מושבתת"))
        assertTrue(line.contains("ברירת המחדל"))
    }

    @Test fun patchAge() {
        val now = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse("2026-10-07")!!.time
        assertEquals(12, EnvLines.patchAgeMonths("2025-10-05", now))
        assertNull(EnvLines.patchAgeMonths("not-a-date", now))
    }
}
