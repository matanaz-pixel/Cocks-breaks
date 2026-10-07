package il.gallerydoctor.worker

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import il.gallerydoctor.core.WorkerChannel
import il.gallerydoctor.core.WorkerOutcome
import il.gallerydoctor.data.StateDb
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger

class WorkerUnavailableException(message: String) : Exception(message)

/**
 * One isolated decoder process, seen from the UI process. Implements the watchdog: if the worker
 * does not answer within the deadline it is killed (and the file reported as TimedOut); if the
 * process dies on its own the file is reported as Died.
 */
class ScannerClient(
    private val ctx: Context,
    private val serviceClass: Class<*>,
    private val db: StateDb,
    private val decodeEnabled: Boolean,
) : WorkerChannel {

    private class Pending(val seq: Int, val result: CompletableDeferred<WorkerOutcome>)

    private val seqGen = AtomicInteger(1)
    private val replyHandler = Handler(Looper.getMainLooper()) { msg -> onReply(msg); true }
    private val replyMessenger = Messenger(replyHandler)

    @Volatile private var connection: ServiceConnection? = null
    @Volatile private var service: Messenger? = null
    @Volatile private var workerPid = 0
    @Volatile private var lastKilledPid = 0
    @Volatile private var pending: Pending? = null
    @Volatile private var pongWaiter: CompletableDeferred<Unit>? = null

    private fun onReply(msg: Message) {
        when (msg.what) {
            WorkerProtocol.MSG_PONG -> {
                workerPid = msg.arg1
                pongWaiter?.complete(Unit)
            }
            WorkerProtocol.MSG_RESULT -> {
                val p = pending ?: return
                if (p.seq != msg.data.getInt(WorkerProtocol.SEQ)) return // stale answer from a previous request
                p.result.complete(WorkerOutcome.Done(WorkerProtocol.bundleToResult(msg.data)))
            }
        }
    }

    /** Binds and handshakes with a fresh worker process. Retries a few times, then gives up loudly. */
    private suspend fun ensureReady() {
        if (service != null && connection != null) return
        if (connection != null) teardown() // the process died on its own earlier; drop the stale binding
        var lastError = "no answer"
        repeat(3) {
            val connected = CompletableDeferred<Messenger?>()
            val conn = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                    if (connection !== this) return
                    val m = Messenger(binder)
                    service = m
                    connected.complete(m)
                }

                override fun onServiceDisconnected(name: ComponentName) = died(this)
                override fun onBindingDied(name: ComponentName) = died(this)
                override fun onNullBinding(name: ComponentName) { connected.complete(null) }
            }
            connection = conn
            val ok = ctx.bindService(Intent(ctx, serviceClass), conn, Context.BIND_AUTO_CREATE)
            val m = if (ok) withTimeoutOrNull(10_000) { connected.await() } else null
            if (m != null) {
                val waiter = CompletableDeferred<Unit>()
                pongWaiter = waiter
                try {
                    m.send(Message.obtain(null, WorkerProtocol.MSG_PING, seqGen.getAndIncrement(), 0).also { it.replyTo = replyMessenger })
                    if (withTimeoutOrNull(10_000) { waiter.await() } != null && workerPid != lastKilledPid) return
                } catch (_: Exception) {
                }
            }
            lastError = if (ok) "worker did not start in time" else "bindService refused"
            teardown()
        }
        throw WorkerUnavailableException("לא ניתן להפעיל את תהליך הבדיקה ($lastError)")
    }

    private fun died(source: ServiceConnection) {
        if (connection !== source) return // an old connection we already replaced
        service = null
        workerPid = 0 // the process is gone; never signal a PID that may be reused by another process
        pending?.result?.complete(WorkerOutcome.Died)
    }

    private fun teardown() {
        val conn = connection
        connection = null
        service = null
        if (conn != null) try { ctx.unbindService(conn) } catch (_: Exception) { }
        val pid = workerPid
        workerPid = 0
        if (pid > 0 && pid != Process.myPid()) {
            lastKilledPid = pid
            Process.killProcess(pid)
        }
    }

    override suspend fun run(pk: Long, timeoutMs: Long): WorkerOutcome {
        ensureReady()
        val row = db.getRow(pk) ?: return WorkerOutcome.Done(il.gallerydoctor.core.FileResult())
        val request = TestRequest(
            pk = pk, uri = row.uri, name = row.name, mime = row.mime, isVideo = row.isVideo, size = row.size,
            decode = decodeEnabled,
            decodeBroken = pk % 5L == 0L, // L3 on a 1-in-5 sample of files L2 already condemned
            ioProbe = pk % 10L == 0L,
        )
        val p = Pending(seqGen.getAndIncrement(), CompletableDeferred())
        pending = p
        try {
            val m = service ?: return WorkerOutcome.Died
            val msg = Message.obtain(null, WorkerProtocol.MSG_TEST)
            msg.data = WorkerProtocol.requestToBundle(request).apply { putInt(WorkerProtocol.SEQ, p.seq) }
            msg.replyTo = replyMessenger
            try {
                m.send(msg)
            } catch (_: Exception) {
                return WorkerOutcome.Died
            }
            return withTimeoutOrNull(timeoutMs) { p.result.await() } ?: WorkerOutcome.TimedOut
        } finally {
            pending = null
        }
    }

    override suspend fun reset() = teardown()

    fun close() = teardown()
}
