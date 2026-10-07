package il.gallerydoctor.core

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface WorkerOutcome {
    data class Done(val result: FileResult) : WorkerOutcome
    /** The worker process died while testing the file. */
    data object Died : WorkerOutcome
    /** The per-file watchdog fired. */
    data object TimedOut : WorkerOutcome
}

/** One isolated worker (a separate OS process in the app, a fake in tests). One file at a time. */
interface WorkerChannel {
    suspend fun run(pk: Long, timeoutMs: Long): WorkerOutcome

    /** Kill and forget the worker process; the next [run] starts a fresh one. */
    suspend fun reset()
}

/** Persistent scan state. Writes of [setTesting] must be durable before the file is handed to a worker. */
interface StateStore {
    fun nextBatch(afterPk: Long, limit: Int): List<WorkItem>
    fun setTesting(worker: Int, pk: Long)
    fun clearTesting(worker: Int)
    fun saveResult(pk: Long, result: FileResult)
    fun markCrasher(pk: Long, reason: Reason)
    fun bumpAttempts(pk: Long): Int

    /** After an unclean stop: files left marked as "currently testing" get an attempt charged. Returns how many. */
    fun recoverInterrupted(): Int
}

/**
 * Drives the integrity scan across [channels] (one file per worker at a time, so a crash is always
 * attributable to exactly one file).
 *
 * Policy:
 *  - worker dies on a file: retry once; a second death marks the file CRASHER
 *  - watchdog fires (default 5s): retry once with a longer deadline; finishing late is SLOW_DECODE,
 *    a second timeout marks the file CRASHER (hang)
 */
class ScanCoordinator(
    private val store: StateStore,
    private val channels: List<WorkerChannel>,
    private val hangTimeoutMs: Long = 5_000,
    private val retryTimeoutMs: Long = 20_000,
    private val gate: suspend () -> Unit = {},
    private val onFileDone: (pk: Long, outcome: Outcome) -> Unit = { _, _ -> },
    private val onWorkerRestart: () -> Unit = {},
) {
    enum class Outcome { SAVED, CRASHER }

    suspend fun run() = coroutineScope {
        store.recoverInterrupted()
        val queue = Channel<WorkItem>(capacity = channels.size * 2)

        launch {
            var after = 0L
            while (true) {
                val batch = store.nextBatch(after, 200)
                if (batch.isEmpty()) break
                for (item in batch) queue.send(item)
                after = batch.last().pk
            }
            queue.close()
        }

        channels.forEachIndexed { index, channel ->
            launch {
                for (item in queue) {
                    gate()
                    process(index, channel, item)
                }
            }
        }
    }

    private suspend fun process(worker: Int, ch: WorkerChannel, item: WorkItem) {
        var timeout = hangTimeoutMs
        var diedOnce = item.attempts > 0
        var retriedAfterTimeout = false
        try {
            while (true) {
                store.setTesting(worker, item.pk)
                val out = ch.run(item.pk, timeout)
                when (out) {
                    is WorkerOutcome.Done -> {
                        val extra = buildList {
                            if (retriedAfterTimeout) add(Reason.SLOW_DECODE)
                            if (diedOnce) add(Reason.WORKER_DIED_ONCE)
                        }
                        store.saveResult(item.pk, out.result.copy(reasons = (out.result.reasons + extra).distinct()))
                        store.clearTesting(worker)
                        onFileDone(item.pk, Outcome.SAVED)
                        return
                    }

                    WorkerOutcome.Died -> {
                        ch.reset()
                        onWorkerRestart()
                        val attempts = store.bumpAttempts(item.pk)
                        diedOnce = true
                        if (attempts >= 2) {
                            store.markCrasher(item.pk, Reason.DECODER_CRASH)
                            store.clearTesting(worker)
                            onFileDone(item.pk, Outcome.CRASHER)
                            return
                        }
                    }

                    WorkerOutcome.TimedOut -> {
                        ch.reset()
                        onWorkerRestart()
                        if (!retriedAfterTimeout) {
                            retriedAfterTimeout = true
                            timeout = retryTimeoutMs
                        } else {
                            store.markCrasher(item.pk, Reason.HANG_TIMEOUT)
                            store.clearTesting(worker)
                            onFileDone(item.pk, Outcome.CRASHER)
                            return
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            // A user cancel, or the worker process failing to start, is not the file's fault:
            // drop the marker without charging an attempt.
            withContext(NonCancellable) { store.clearTesting(worker) }
            throw e
        }
    }
}
