package il.gallerydoctor.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineTest {
    private val t0 = 1_760_000_000_000L
    private val min = 60_000L

    private fun s(minute: Int, version: String = "v1", gen: Long = 1000, count: Int = 80_000, free: Long = 5L shl 30, battery: Int = 60, charging: Boolean = false) =
        TimelineEvent.Sample(t0 + minute * min, "external_primary", version, gen, count, free, 479L shl 30, battery, charging)

    private fun steady(n: Int) = (0 until n).map { s(it * 10, gen = 1000L + it) }

    @Test
    fun codecRoundTripsEveryEvent() {
        val all = listOf(s(0), TimelineEvent.Boot(t0), TimelineEvent.Started(t0 + 5), TimelineEvent.Changes(t0 + 9, 321))
        for (e in all) assertEquals(e, TimelineCodec.decode(TimelineCodec.encode(e)))
        assertNull(TimelineCodec.decode("garbage"))
        assertNull(TimelineCodec.decode("12|S|only|three"))
        assertNull(TimelineCodec.decode("x|B"))
    }

    @Test
    fun aQuietTimelineReportsNoEvent() {
        val sum = Timeline.summarize(steady(30))
        assertTrue(!sum.anyEvent)
        assertEquals(29 * 10 / 60, sum.spanHours.toInt())
        assertTrue(Timeline.lines(sum).any { it.contains("לא נרשם אירוע") })
    }

    @Test
    fun aRecreatedDatabaseIsSeenFromVersionAndFromGeneration() {
        val ev = steady(12) + s(130, version = "v2", gen = 7) + s(140, version = "v2", gen = 9)
        val sum = Timeline.summarize(ev)
        assertEquals(1, sum.versionChanges.size)
        assertEquals(1, sum.generationDrops.size)
        assertTrue(sum.databaseRecreated)
        val h = Analyzer.analyze(ReportInput(totalFiles = 80_000, timeline = sum)).hypotheses.first { it.id == "RECORDER_EVENTS" }
        assertEquals(Confidence.HIGH, h.confidence)
    }

    @Test
    fun aDipThatRecoversIsARebuildAndOneThatDoesNotIsALoss() {
        val dip = steady(10) + s(100, count = 20_000, gen = 1010) + s(110, count = 55_000, gen = 1011) + s(120, count = 80_000, gen = 1012)
        val d = Timeline.summarize(dip).countDrops
        assertEquals(1, d.size)
        assertTrue(d[0].recoveredAt != null)
        val loss = steady(10) + (10 until 16).map { s(it * 10, count = 60_000, gen = 1010L + it) }
        val l = Timeline.summarize(loss).countDrops
        assertEquals(1, l.size)
        assertNull(l[0].recoveredAt)
    }

    @Test
    fun smallFluctuationsAreNotDrops() {
        val ev = steady(10) + s(100, count = 79_950, gen = 1010) + s(110, count = 80_020, gen = 1011)
        assertTrue(Timeline.summarize(ev).countDrops.isEmpty())
    }

    @Test
    fun rebootAfterAnEmptyBatteryIsFlaggedButAnOrdinaryRebootIsNot() {
        val ev = steady(10) + s(100, battery = 2) + TimelineEvent.Boot(t0 + 400 * min) + s(410, battery = 1, gen = 1200)
        val sum = Timeline.summarize(ev)
        assertEquals(1, sum.emptyBatteryShutdowns.size)
        assertEquals(2, sum.emptyBatteryShutdowns[0].battery)
        val normal = steady(10) + TimelineEvent.Boot(t0 + 200 * min) + s(210)
        assertTrue(Timeline.summarize(normal).emptyBatteryShutdowns.isEmpty())
        val charging = steady(10) + s(100, battery = 2, charging = true) + TimelineEvent.Boot(t0 + 400 * min)
        assertTrue(Timeline.summarize(charging).emptyBatteryShutdowns.isEmpty())
    }

    @Test
    fun tooFewSamplesNeverProduceAFinding() {
        val ev = listOf(s(0), s(10, version = "v2", gen = 5))
        val sum = Timeline.summarize(ev)
        assertTrue(sum.databaseRecreated)
        assertTrue(Analyzer.analyze(ReportInput(timeline = sum)).hypotheses.none { it.id == "RECORDER_EVENTS" })
        assertTrue(Timeline.lines(sum).any { it.contains("מעט מדי") })
        assertTrue(Timeline.lines(TimelineSummary()).single().contains("לא הופעל"))
    }

    @Test
    fun storageMapDoesNotDoubleCountAndAdmitsWhatItCannotSee() {
        val i = ReportInput(
            volumes = listOf(VolumeStat("אחסון פנימי", 479L shl 30, 1L shl 30, false)),
            storageDirs = listOf(
                FolderStat("DCIM", 20_000, 200L shl 30), FolderStat("DCIM/Camera", 20_000, 190L shl 30),
                FolderStat("Movies", 300, 100L shl 30),
            ),
        )
        val lines = Continuity.storageLines(i)
        assertTrue(lines.first().contains("300.0 GB"))
        assertTrue(lines.any { it.contains("↳ Camera") })
        assertTrue(lines.any { it.contains("לא מורשית לראות") })
    }
}
