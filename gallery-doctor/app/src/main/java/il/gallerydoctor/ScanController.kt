package il.gallerydoctor

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import il.gallerydoctor.core.ReportData
import il.gallerydoctor.core.ScanCoordinator
import il.gallerydoctor.data.ReportBuilder
import il.gallerydoctor.data.StateDb
import il.gallerydoctor.scan.DuplicateFinder
import il.gallerydoctor.core.ScanSnapshot
import il.gallerydoctor.data.ScanHistory
import il.gallerydoctor.scan.FsWalker
import il.gallerydoctor.scan.IndexStability
import il.gallerydoctor.scan.MediaEnumerator
import il.gallerydoctor.scan.SafEnumerator
import il.gallerydoctor.scan.StorageInfo
import il.gallerydoctor.scan.StorageWalker
import il.gallerydoctor.worker.ScannerClient
import il.gallerydoctor.worker.newScannerPool
import il.gallerydoctor.worker.WorkerUnavailableException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext

enum class Phase { ENUMERATING, FILES, CHECKING, DUPLICATES, FINISHING }

sealed interface ScanState {
    data class Idle(val canResume: Boolean = false, val hasReport: Boolean = false, val filesFound: Int = 0) : ScanState
    data class Running(
        val phase: Phase,
        val done: Long = 0,
        val total: Long = 0,
        val etaSeconds: Long? = null,
        val message: String? = null,
        val workerRestarts: Int = 0,
        val crashers: Int = 0,
    ) : ScanState
    data class Done(val report: ReportData) : ScanState
    data class Failed(val message: String, val canResume: Boolean = false) : ScanState
}

data class ScanOptions(val deep: Boolean, val treeUri: String?, val resume: Boolean)

/**
 * Process-wide scan orchestrator (UI process). Lives outside any Activity so a scan survives rotation
 * and, together with [ScanService], the screen turning off. State is kept in [StateDb], so a killed
 * process resumes where it stopped.
 */
object ScanController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow<ScanState>(ScanState.Idle())
    val state: StateFlow<ScanState> = _state.asStateFlow()
    private var job: Job? = null

    fun isRunning() = job?.isActive == true

    fun refreshIdle(ctx: Context) {
        if (isRunning() || _state.value is ScanState.Done) return
        scope.launch(Dispatchers.IO) {
            val db = StateDb.get(ctx)
            val files = db.count()
            val state = db.meta("state")
            if (isRunning() || _state.value is ScanState.Done) return@launch
            _state.value = ScanState.Idle(
                canResume = files > 0 && state != "DONE" && state != null,
                hasReport = files > 0,
                filesFound = files,
            )
        }
    }

    fun backToStart(ctx: Context) {
        if (isRunning()) return
        _state.value = ScanState.Idle()
        refreshIdle(ctx)
    }

    fun start(ctx: Context, options: ScanOptions) {
        if (isRunning()) return
        val app = ctx.applicationContext
        _state.value = ScanState.Running(Phase.ENUMERATING)
        ContextCompat.startForegroundService(app, Intent(app, ScanService::class.java))
        job = scope.launch { execute(app, options) }
    }

    fun cancel() {
        job?.cancel(CancellationException("user cancelled"))
    }

    fun showLastReport(ctx: Context) {
        if (isRunning()) return
        val app = ctx.applicationContext
        scope.launch(Dispatchers.IO) {
            val db = StateDb.get(app)
            _state.value = ScanState.Done(ReportBuilder.build(app, db, scanComplete = db.meta("state") == "DONE"))
        }
    }

    private fun publish(update: (ScanState.Running) -> ScanState.Running) {
        _state.update { if (it is ScanState.Running) update(it) else it }
    }

    private suspend fun execute(app: Context, opts: ScanOptions) {
        val db = StateDb.get(app)
        val job = coroutineContext[Job]!!
        val active = { job.isActive }
        var stability: Job? = null
        try {
            if (!opts.resume) {
                db.resetForNewScan()
                db.putMeta("deep", if (opts.deep) "1" else "0")
                db.putMeta("started", System.currentTimeMillis().toString())
                opts.treeUri?.let {
                    db.putMeta("saf", it)
                    db.putMeta("saf_name", SafEnumerator.displayName(app, Uri.parse(it)))
                }
            }
            db.putMeta("state", "RUNNING")
            val deep = db.meta("deep") == "1"

            // ---- 1. enumerate MediaStore (+ optional picked folder) ----
            if (db.meta("enumerated") != "1") {
                publish { it.copy(phase = Phase.ENUMERATING, done = 0, total = 0) }
                var n = 0L
                withContext(Dispatchers.IO) {
                    MediaEnumerator.enumerate(app, { rows ->
                        db.insertRows(rows)
                        n += rows.size
                        publish { it.copy(done = n) }
                    }, active)
                    db.putMeta("favorites", MediaEnumerator.countFavorites(app).toString())
                    db.meta("saf")?.let { tree ->
                        SafEnumerator.enumerate(app, Uri.parse(tree), db.mediaStoreKeys(), { rows ->
                            db.insertRows(rows)
                            n += rows.size
                            publish { it.copy(done = n) }
                        }, active)
                    }
                }
                db.putMeta("enumerated", "1")
                db.putMeta("enumerated_at", System.currentTimeMillis().toString())
            }
            if (db.count() == 0) {
                fail(db, "לא נמצאו תמונות או סרטונים. ודאו שאישרתם גישה לכל התמונות והסרטונים (ולא רק לחלק מהם).")
                return
            }

            // ---- 2. index stability: re-read the index 60s after the first read, in parallel with the scan ----
            stability = if (db.meta("idx_done") != "1") scope.launch(Dispatchers.IO) {
                val at = db.metaLong("enumerated_at") ?: System.currentTimeMillis()
                val wait = IndexStability.INTERVAL_MS - (System.currentTimeMillis() - at)
                if (wait > 0) delay(wait)
                val drift = IndexStability.measure(app, db)
                db.putMeta("idx_changed", drift.changed.toString())
                db.putMeta("idx_vanished", drift.vanished.toString())
                db.putMeta("idx_appeared", drift.appeared.toString())
                db.putMeta("idx_owners", drift.byOwner.entries.joinToString(";") { "${it.key}=${it.value}" })
                db.putMeta("idx_done", "1")
            } else null

            // ---- 3. media folders on disk: orphans and leftover temp files ----
            if (db.meta("fswalk") != "1") {
                publish { it.copy(phase = Phase.FILES, done = 0, total = 0) }
                withContext(Dispatchers.IO) {
                    val roots = StorageInfo.volumes(app).mapNotNull { it.root }
                    val r = FsWalker.walk(roots, db, isActive = active)
                    db.putMeta("dirs_inaccessible", r.inaccessibleDirs.toString())
                }
                if (job.isActive) db.putMeta("fswalk", "1")
            }

            // ---- 3b. where the space went, across the whole shared storage ----
            if (db.meta("storagewalk") != "1") {
                withContext(Dispatchers.IO) {
                    StorageInfo.volumes(app).firstOrNull { !it.removable }?.root?.let { StorageWalker.walk(it, db, active) }
                }
                if (job.isActive) db.putMeta("storagewalk", "1")
            }

            // ---- 4. integrity scan in isolated worker processes ----
            val total = db.count("status!=6").toLong()
            val alreadyDone = total - db.remainingToScan()
            val processed = AtomicInteger(0)
            val restarts = AtomicInteger(0)
            val crashers = AtomicInteger(db.count("status=4"))
            val phaseStart = System.currentTimeMillis()
            publish { it.copy(phase = Phase.CHECKING, done = alreadyDone, total = total, etaSeconds = null) }

            val pool = newScannerPool()
            // about 400 files are read twice (speed / unstable reads), however big the library is
            val probeEvery = maxOf(10L, total / 400)
            val clients = (0 until workerCount(app)).map { ScannerClient(app, pool, db, deep, probeEvery) }
            val thermal = ThermalGate(app) { msg -> publish { s -> s.copy(message = msg) } }
            try {
                withContext(Dispatchers.IO) { ScanCoordinator(
                    store = db,
                    channels = clients,
                    gate = { thermal.await() },
                    onFileDone = { _, outcome ->
                        val n = processed.incrementAndGet()
                        if (outcome == ScanCoordinator.Outcome.CRASHER) crashers.incrementAndGet()
                        if (n % 5 == 0 || outcome == ScanCoordinator.Outcome.CRASHER) {
                            val elapsed = (System.currentTimeMillis() - phaseStart) / 1000.0
                            val remaining = total - alreadyDone - n
                            val eta = if (n >= 20 && elapsed > 0) (remaining / (n / elapsed)).toLong() else null
                            publish {
                                it.copy(done = alreadyDone + n, etaSeconds = eta, workerRestarts = restarts.get(), crashers = crashers.get(), message = null)
                            }
                        }
                    },
                    onWorkerRestart = { restarts.incrementAndGet() },
                ).run() }
            } finally {
                clients.forEach { it.close() }
            }

            // ---- 5. duplicates ----
            if (db.meta("dups") != "1") {
                publish { it.copy(phase = Phase.DUPLICATES, done = 0, total = 0, etaSeconds = null) }
                withContext(Dispatchers.IO) {
                    DuplicateFinder.run(app, db, active) { d, t -> publish { s -> s.copy(done = d.toLong(), total = t.toLong()) } }
                }
                if (job.isActive) db.putMeta("dups", "1")
            }

            // ---- 6. report ----
            publish { it.copy(phase = Phase.FINISHING, done = 0, total = 0, etaSeconds = null) }
            stability?.join()
            db.putMeta("state", "DONE")
            db.putMeta("finished", System.currentTimeMillis().toString())
            val report = withContext(Dispatchers.IO) { ReportBuilder.build(app, db, scanComplete = true) }
            ScanHistory.save(
                app,
                ScanSnapshot(
                    startedMillis = db.metaLong("started") ?: System.currentTimeMillis(),
                    totalFiles = report.input.totalFiles, favorites = report.input.favorites,
                    orphans = report.input.orphanCount, bursts = report.input.bursts.size,
                    freeBytes = report.input.volumes.firstOrNull { !it.removable }?.freeBytes ?: 0L,
                ),
            )
            _state.value = ScanState.Done(report)
        } catch (e: CancellationException) {
            stability?.cancel()
            withContext(NonCancellable) {
                db.putMeta("state", "CANCELLED")
                _state.value = ScanState.Idle(canResume = true, hasReport = true, filesFound = db.count())
            }
            throw e
        } catch (e: WorkerUnavailableException) {
            fail(db, e.message ?: "תהליך הבדיקה לא עלה")
        } catch (e: Throwable) {
            fail(db, "הסריקה נכשלה: ${e.javaClass.simpleName}")
        } finally {
            app.stopService(Intent(app, ScanService::class.java))
        }
    }

    private fun fail(db: StateDb, message: String) {
        db.putMeta("state", "FAILED")
        // Everything scanned so far is kept in the database, so a failed scan can be continued.
        _state.value = ScanState.Failed(message, canResume = db.remainingToScan() > 0)
    }

    private fun workerCount(ctx: Context): Int {
        val lowRam = ctx.getSystemService(ActivityManager::class.java).isLowRamDevice
        return if (!lowRam && Runtime.getRuntime().availableProcessors() >= 8) 3 else 2
    }
}

/** Pauses the scan while the phone reports severe heat, so a 50k-file scan does not cook the device. */
private class ThermalGate(ctx: Context, private val say: (String) -> Unit) {
    private val pm = ctx.getSystemService(PowerManager::class.java)
    private val calls = AtomicInteger(0)

    suspend fun await() {
        if (Build.VERSION.SDK_INT < 29 || calls.incrementAndGet() % 25 != 0) return
        var told = false
        while (pm.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE) {
            if (!told) { say("הטלפון התחמם. הסריקה מושהית לכמה שניות כדי שיתקרר."); told = true }
            delay(15_000)
        }
    }
}
