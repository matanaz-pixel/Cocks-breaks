package il.gallerydoctor.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessPoolTest {
    private var now = 1_000L
    private fun pool(size: Int = 4) = ProcessPool(size, { now }, safeGapMs = 65_000)

    @Test fun freshProcessIsUsedAfterACrash() {
        val p = pool()
        val first = p.tryAcquire()
        p.release(first, crashed = true)
        // the retry of a crashing file must not land in the process that just crashed
        assertNotEquals(first, p.tryAcquire())
    }

    @Test fun noProcessCrashesTwiceWithinAMinute() {
        val p = pool(4)
        val crashTimes = HashMap<Int, MutableList<Long>>()
        repeat(40) {
            var idx = p.tryAcquire()
            while (idx < 0) { now += p.msUntilAvailable()!!.coerceAtLeast(200); idx = p.tryAcquire() }
            crashTimes.getOrPut(idx) { ArrayList() } += now
            p.release(idx, crashed = true)
            now += 1_000 // a crashing file every second
        }
        for ((_, times) in crashTimes) for (i in 1 until times.size) {
            assertTrue("process crashed twice within 60s: $times", times[i] - times[i - 1] >= 60_000)
        }
    }

    @Test fun waitsWhenEveryProcessCrashedRecently() {
        val p = pool(2)
        repeat(2) { p.release(p.tryAcquire(), crashed = true) }
        assertEquals(-1, p.tryAcquire())
        val wait = p.msUntilAvailable()!!
        assertTrue(wait in 1..65_000)
        now += wait
        assertTrue(p.tryAcquire() >= 0)
    }

    @Test fun processRefusedBySystemIsNeverUsedAgain() {
        val p = pool(3)
        val a = p.tryAcquire()
        p.markDead(a)
        repeat(10) {
            val i = p.tryAcquire()
            assertNotEquals(a, i)
            p.release(i, crashed = false)
        }
        assertEquals(2, p.usableCount())
    }

    @Test fun allProcessesRefusedMeansNoneAvailable() {
        val p = pool(2)
        p.markDead(p.tryAcquire())
        p.markDead(p.tryAcquire())
        assertEquals(-1, p.tryAcquire())
        assertNull(p.msUntilAvailable())
    }

    @Test fun twoWorkersNeverShareAProcess() {
        val p = pool(4)
        val a = p.tryAcquire()
        val b = p.tryAcquire()
        assertNotEquals(a, b)
    }
}
