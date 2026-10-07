package il.gallerydoctor.worker

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.Process
import il.gallerydoctor.core.FileResult
import il.gallerydoctor.core.Reason

/**
 * Isolated decoder worker. Each subclass is declared in the manifest with its own android:process,
 * so up to three files are tested in parallel and a crash is always attributable to one file.
 * There are twelve process names, rotated by [il.gallerydoctor.core.ProcessPool], because Android
 * refuses to restart a process that crashed twice within a minute.
 *
 * All work happens on a dedicated thread; if it hangs, the client kills this whole process.
 */
abstract class BaseScannerService : Service() {
    private lateinit var thread: HandlerThread
    private lateinit var messenger: Messenger
    private lateinit var tester: FileTester

    override fun onCreate() {
        super.onCreate()
        tester = FileTester(applicationContext)
        thread = HandlerThread("gd-decoder").also { it.start() }
        messenger = Messenger(object : Handler(thread.looper) {
            override fun handleMessage(msg: Message) {
                val reply = msg.replyTo ?: return
                when (msg.what) {
                    WorkerProtocol.MSG_PING ->
                        reply.send(Message.obtain(null, WorkerProtocol.MSG_PONG, Process.myPid(), msg.arg1))

                    WorkerProtocol.MSG_TEST -> {
                        val seq = msg.data.getInt(WorkerProtocol.SEQ)
                        val req = WorkerProtocol.bundleToRequest(msg.data)
                        val result = try {
                            tester.test(req)
                        } catch (e: Throwable) {
                            FileResult(listOf(Reason.DECODE_FAIL))
                        }
                        val out = Message.obtain(null, WorkerProtocol.MSG_RESULT)
                        out.data = WorkerProtocol.resultToBundle(result).apply { putInt(WorkerProtocol.SEQ, seq) }
                        reply.send(out)
                    }
                }
            }
        })
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        thread.quitSafely()
        super.onDestroy()
    }
}

class ScannerService : BaseScannerService()
class ScannerService2 : BaseScannerService()
class ScannerService3 : BaseScannerService()
class ScannerService4 : BaseScannerService()
class ScannerService5 : BaseScannerService()
class ScannerService6 : BaseScannerService()
class ScannerService7 : BaseScannerService()
class ScannerService8 : BaseScannerService()
class ScannerService9 : BaseScannerService()
class ScannerService10 : BaseScannerService()
class ScannerService11 : BaseScannerService()
class ScannerService12 : BaseScannerService()

/** Must match the <service> entries in AndroidManifest.xml, one process each. */
val ScannerServices: List<Class<*>> = listOf(
    ScannerService::class.java, ScannerService2::class.java, ScannerService3::class.java,
    ScannerService4::class.java, ScannerService5::class.java, ScannerService6::class.java,
    ScannerService7::class.java, ScannerService8::class.java, ScannerService9::class.java,
    ScannerService10::class.java, ScannerService11::class.java, ScannerService12::class.java,
)
