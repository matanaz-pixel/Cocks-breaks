package il.gallerydoctor.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class MemStore(count: Int) : StateStore {
    val status = HashMap<Long, Status>()
    val reasons = HashMap<Long, List<Reason>>()
    val attempts = HashMap<Long, Int>()
    val testing = HashMap<Int, Long>()

    init { for (i in 1..count) { status[i.toLong()] = Status.TODO; attempts[i.toLong()] = 0 } }

    @Synchronized override fun nextBatch(afterPk: Long, limit: Int) =
        status.filter { it.key > afterPk && it.value == Status.TODO }.keys.sorted().take(limit)
            .map { WorkItem(it, attempts[it] ?: 0) }

    @Synchronized override fun setTesting(worker: Int, pk: Long) { testing[worker] = pk }
    @Synchronized override fun clearTesting(worker: Int) { testing.remove(worker) }
    @Synchronized override fun saveResult(pk: Long, result: FileResult) {
        reasons[pk] = result.reasons
        status[pk] = Classifier.statusOf(result.reasons)
    }
    @Synchronized override fun markCrasher(pk: Long, reason: Reason) { reasons[pk] = listOf(reason); status[pk] = Status.CRASHER }
    @Synchronized override fun bumpAttempts(pk: Long): Int { val n = (attempts[pk] ?: 0) + 1; attempts[pk] = n; return n }
    @Synchronized override fun recoverInterrupted(): Int {
        val left = testing.values.toList()
        testing.clear()
        for (pk in left) {
            if (bumpAttempts(pk) >= 2) markCrasher(pk, Reason.DECODER_CRASH)
        }
        return left.size
    }
}

/** Scripted worker: for each pk, a queue of outcomes; default is a clean result. */
private class FakeWorker(val script: Map<Long, MutableList<WorkerOutcome>>) : WorkerChannel {
    var resets = 0
    val seen = ArrayList<Long>()
    override suspend fun run(pk: Long, timeoutMs: Long): WorkerOutcome {
        synchronized(seen) { seen += pk }
        val q = script[pk]
        return if (q != null && q.isNotEmpty()) q.removeAt(0) else WorkerOutcome.Done(FileResult())
    }
    override suspend fun reset() { resets++ }
}

class ScanCoordinatorTest {
    private fun script(vararg pairs: Pair<Long, List<WorkerOutcome>>) =
        pairs.associate { it.first to it.second.toMutableList() }

    @Test fun crashingFileIsIsolatedAndScanContinues() = runBlocking {
        val store = MemStore(10)
        val worker = FakeWorker(script(4L to listOf(WorkerOutcome.Died, WorkerOutcome.Died)))
        ScanCoordinator(store, listOf(worker)).run()

        assertEquals(Status.CRASHER, store.status[4L])
        assertEquals(listOf(Reason.DECODER_CRASH), store.reasons[4L])
        // every other file was still scanned
        for (pk in (1L..10L) - 4L) assertEquals(Status.OK, store.status[pk])
        assertEquals(2, worker.resets)
        assertTrue(store.testing.isEmpty())
    }

    @Test fun crashOnceThenSucceedsIsOnlyASuspect() = runBlocking {
        val store = MemStore(3)
        val worker = FakeWorker(script(2L to listOf(WorkerOutcome.Died)))
        ScanCoordinator(store, listOf(worker)).run()
        assertEquals(Status.SUSPECT, store.status[2L])
        assertTrue(Reason.WORKER_DIED_ONCE in store.reasons[2L]!!)
    }

    @Test fun hangTwiceIsCrasher() = runBlocking {
        val store = MemStore(3)
        val worker = FakeWorker(script(1L to listOf(WorkerOutcome.TimedOut, WorkerOutcome.TimedOut)))
        ScanCoordinator(store, listOf(worker)).run()
        assertEquals(Status.CRASHER, store.status[1L])
        assertEquals(listOf(Reason.HANG_TIMEOUT), store.reasons[1L])
    }

    @Test fun slowButFinishingIsNotACrasher() = runBlocking {
        val store = MemStore(3)
        val worker = FakeWorker(script(3L to listOf(WorkerOutcome.TimedOut)))
        ScanCoordinator(store, listOf(worker)).run()
        assertEquals(Status.SUSPECT, store.status[3L])
        assertTrue(Reason.SLOW_DECODE in store.reasons[3L]!!)
    }

    @Test fun parallelWorkersAttributeCrashToTheRightFile() = runBlocking {
        val store = MemStore(40)
        // a crash belongs to the file, not to the worker: both workers share one script
        val shared = script(
            7L to listOf(WorkerOutcome.Died, WorkerOutcome.Died),
            22L to listOf(WorkerOutcome.TimedOut, WorkerOutcome.TimedOut),
        )
        val w1 = FakeWorker(shared)
        val w2 = FakeWorker(shared)
        ScanCoordinator(store, listOf(w1, w2)).run()
        val crashers = store.status.filter { it.value == Status.CRASHER }.keys.sorted()
        assertEquals(listOf(7L, 22L), crashers)
        assertTrue(store.status.values.none { it == Status.TODO })
    }

    @Test fun resumeAfterHardKillChargesTheInterruptedFile() = runBlocking {
        val store = MemStore(5)
        store.setTesting(0, 3L) // process was killed while testing file 3
        ScanCoordinator(store, listOf(FakeWorker(emptyMap()))).run()
        // first strike: the file is retried (and passes this time) and flagged as having died once
        assertEquals(Status.SUSPECT, store.status[3L])
        assertTrue(Reason.WORKER_DIED_ONCE in store.reasons[3L]!!)

        // second kill on the same file = crasher without being tested again
        val store2 = MemStore(5)
        store2.attempts[3L] = 1
        store2.setTesting(0, 3L)
        val w = FakeWorker(emptyMap())
        ScanCoordinator(store2, listOf(w)).run()
        assertEquals(Status.CRASHER, store2.status[3L])
        assertTrue(3L !in w.seen)
    }

    @Test fun cancelLeavesNoMarkerAndNoCharge() = runBlocking {
        val store = MemStore(5)
        val started = CompletableDeferred<Unit>()
        val blocker = object : WorkerChannel {
            override suspend fun run(pk: Long, timeoutMs: Long): WorkerOutcome {
                started.complete(Unit)
                CompletableDeferred<Unit>().await() // never returns
                error("unreachable")
            }
            override suspend fun reset() {}
        }
        val job: Job = launch { ScanCoordinator(store, listOf(blocker)).run() }
        started.await()
        job.cancel(CancellationException("user"))
        job.join()
        assertTrue(store.testing.isEmpty())
        assertEquals(0, store.attempts[1L])
        assertEquals(Status.TODO, store.status[1L])
        assertNull(store.reasons[1L])
    }
}
