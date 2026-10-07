package il.gallerydoctor.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import il.gallerydoctor.core.Anomaly
import il.gallerydoctor.core.AnomalyDetector
import il.gallerydoctor.core.Classifier
import il.gallerydoctor.core.DupGroup
import il.gallerydoctor.core.FileResult
import il.gallerydoctor.core.FolderStat
import il.gallerydoctor.core.MediaFacts
import il.gallerydoctor.core.ProblemRow
import il.gallerydoctor.core.Reason
import il.gallerydoctor.core.StateStore
import il.gallerydoctor.core.Status
import il.gallerydoctor.core.WorkItem

/** One media file known to the scan. [source] 0 = MediaStore row, 1 = found only through the picked folder (SAF). */
data class FileRow(
    val pk: Long = 0,
    val source: Int = SOURCE_MEDIASTORE,
    val volume: String,
    val mediaId: Long,
    val uri: String,
    val name: String,
    val path: String?,
    val relPath: String?,
    val size: Long,
    val mime: String?,
    val isVideo: Boolean,
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Long = -1,
    val dateAdded: Long = 0,
    val dateModified: Long = 0,
    val pending: Boolean = false,
    val trashed: Boolean = false,
    val status: Status = Status.TODO,
) {
    companion object {
        const val SOURCE_MEDIASTORE = 0
        const val SOURCE_SAF = 1
    }
}

/**
 * Small local database holding scan state, so scans are cancelable and resumable. Plain SQLite
 * (no Room) to keep the APK small. WAL + synchronous=NORMAL: every write is a completed write()
 * before the call returns, so it survives the death of any process of this app.
 */
class StateDb(context: Context) : SQLiteOpenHelper(context.applicationContext, "gallerydoctor.db", null, 1), StateStore {

    companion object {
        @Volatile private var instance: StateDb? = null
        fun get(ctx: Context): StateDb = instance ?: synchronized(this) { instance ?: StateDb(ctx).also { instance = it } }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(false)
        db.enableWriteAheadLogging()
    }

    override fun onOpen(db: SQLiteDatabase) {
        db.execSQL("PRAGMA synchronous=NORMAL")
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE files(
            pk INTEGER PRIMARY KEY AUTOINCREMENT,
            source INTEGER NOT NULL, vol TEXT NOT NULL, mid INTEGER NOT NULL, uri TEXT NOT NULL, name TEXT NOT NULL,
            path TEXT, rel TEXT, size INTEGER NOT NULL, mime TEXT, video INTEGER NOT NULL,
            w INTEGER NOT NULL DEFAULT 0, h INTEGER NOT NULL DEFAULT 0, dur INTEGER NOT NULL DEFAULT -1,
            added INTEGER NOT NULL DEFAULT 0, modified INTEGER NOT NULL DEFAULT 0,
            pending INTEGER NOT NULL DEFAULT 0, trashed INTEGER NOT NULL DEFAULT 0,
            fps REAL NOT NULL DEFAULT 0, bitrate INTEGER NOT NULL DEFAULT 0,
            status INTEGER NOT NULL DEFAULT 0, reasons TEXT NOT NULL DEFAULT '', attempts INTEGER NOT NULL DEFAULT 0,
            anomalies INTEGER NOT NULL DEFAULT 0, mbps REAL NOT NULL DEFAULT 0, probed INTEGER NOT NULL DEFAULT 0,
            ioerr INTEGER NOT NULL DEFAULT 0, hashp TEXT, hashf TEXT)""",
        )
        db.execSQL("CREATE INDEX idx_files_status ON files(status)")
        db.execSQL("CREATE INDEX idx_files_size ON files(size)")
        db.execSQL("CREATE INDEX idx_files_path ON files(path)")
        db.execSQL("CREATE TABLE testing(worker INTEGER PRIMARY KEY, pk INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE extra(id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL, path TEXT NOT NULL, size INTEGER NOT NULL DEFAULT 0, note TEXT)")
        db.execSQL("CREATE TABLE dups(grp INTEGER NOT NULL, pk INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE meta(k TEXT PRIMARY KEY, v TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    // ---------------------------------------------------------------- meta

    fun meta(key: String): String? = readableDatabase.rawQuery("SELECT v FROM meta WHERE k=?", arrayOf(key)).use {
        if (it.moveToFirst()) it.getString(0) else null
    }

    fun putMeta(key: String, value: String) {
        writableDatabase.execSQL("INSERT OR REPLACE INTO meta(k,v) VALUES(?,?)", arrayOf(key, value))
    }

    fun metaLong(key: String): Long? = meta(key)?.toLongOrNull()

    fun resetForNewScan() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (t in listOf("files", "testing", "extra", "dups", "meta")) db.execSQL("DELETE FROM $t")
            db.execSQL("DELETE FROM sqlite_sequence WHERE name='files'")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ---------------------------------------------------------------- files

    fun insertRows(rows: List<FileRow>) {
        if (rows.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            val stmt = db.compileStatement(
                "INSERT INTO files(source,vol,mid,uri,name,path,rel,size,mime,video,w,h,dur,added,modified,pending,trashed,status) " +
                    "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            )
            for (r in rows) {
                stmt.clearBindings()
                stmt.bindLong(1, r.source.toLong())
                stmt.bindString(2, r.volume)
                stmt.bindLong(3, r.mediaId)
                stmt.bindString(4, r.uri)
                stmt.bindString(5, r.name)
                r.path?.let { stmt.bindString(6, it) }
                r.relPath?.let { stmt.bindString(7, it) }
                stmt.bindLong(8, r.size)
                r.mime?.let { stmt.bindString(9, it) }
                stmt.bindLong(10, if (r.isVideo) 1 else 0)
                stmt.bindLong(11, r.width.toLong())
                stmt.bindLong(12, r.height.toLong())
                stmt.bindLong(13, r.durationMs)
                stmt.bindLong(14, r.dateAdded)
                stmt.bindLong(15, r.dateModified)
                stmt.bindLong(16, if (r.pending) 1 else 0)
                stmt.bindLong(17, if (r.trashed) 1 else 0)
                // Pending / trashed rows belong to other apps' in-flight work; testing them gives false ghosts.
                stmt.bindLong(18, (if (r.pending || r.trashed) Status.SKIPPED else Status.TODO).code.toLong())
                stmt.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun Cursor.toFileRow() = FileRow(
        pk = getLong(getColumnIndexOrThrow("pk")),
        source = getInt(getColumnIndexOrThrow("source")),
        volume = getString(getColumnIndexOrThrow("vol")),
        mediaId = getLong(getColumnIndexOrThrow("mid")),
        uri = getString(getColumnIndexOrThrow("uri")),
        name = getString(getColumnIndexOrThrow("name")),
        path = getString(getColumnIndexOrThrow("path")),
        relPath = getString(getColumnIndexOrThrow("rel")),
        size = getLong(getColumnIndexOrThrow("size")),
        mime = getString(getColumnIndexOrThrow("mime")),
        isVideo = getInt(getColumnIndexOrThrow("video")) == 1,
        width = getInt(getColumnIndexOrThrow("w")),
        height = getInt(getColumnIndexOrThrow("h")),
        durationMs = getLong(getColumnIndexOrThrow("dur")),
        dateAdded = getLong(getColumnIndexOrThrow("added")),
        dateModified = getLong(getColumnIndexOrThrow("modified")),
        pending = getInt(getColumnIndexOrThrow("pending")) == 1,
        trashed = getInt(getColumnIndexOrThrow("trashed")) == 1,
        status = Status.fromCode(getInt(getColumnIndexOrThrow("status"))),
    )

    fun getRow(pk: Long): FileRow? =
        readableDatabase.rawQuery("SELECT * FROM files WHERE pk=?", arrayOf(pk.toString())).use {
            if (it.moveToFirst()) it.toFileRow() else null
        }

    fun count(where: String = "1", args: Array<String> = emptyArray()): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM files WHERE $where", args).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    fun countByStatus(): Map<Status, Int> {
        val out = HashMap<Status, Int>()
        readableDatabase.rawQuery("SELECT status, COUNT(*) FROM files GROUP BY status", null).use {
            while (it.moveToNext()) out[Status.fromCode(it.getInt(0))] = it.getInt(1)
        }
        return out
    }

    fun remainingToScan() = count("status=0")

    /** (name|size) keys of every MediaStore row, used to tell which SAF files are not indexed. */
    fun mediaStoreKeys(): HashSet<String> {
        val set = HashSet<String>()
        readableDatabase.rawQuery("SELECT name, size FROM files WHERE source=0", null).use {
            while (it.moveToNext()) set += it.getString(0) + "|" + it.getLong(1)
        }
        return set
    }

    fun pathIndexed(path: String): Boolean =
        readableDatabase.rawQuery("SELECT 1 FROM files WHERE source=0 AND path=? LIMIT 1", arrayOf(path)).use { it.moveToFirst() }

    /** Snapshot for index-stability: key "vol:mid" -> "size:modified". */
    fun indexSnapshot(): HashMap<String, String> {
        val map = HashMap<String, String>()
        readableDatabase.rawQuery("SELECT vol, mid, size, modified FROM files WHERE source=0", null).use {
            while (it.moveToNext()) map[it.getString(0) + ":" + it.getLong(1)] = it.getLong(2).toString() + ":" + it.getLong(3)
        }
        return map
    }

    // ---------------------------------------------------------------- StateStore

    override fun nextBatch(afterPk: Long, limit: Int): List<WorkItem> {
        val out = ArrayList<WorkItem>()
        readableDatabase.rawQuery(
            "SELECT pk, attempts FROM files WHERE status=0 AND pk>? ORDER BY pk LIMIT ?",
            arrayOf(afterPk.toString(), limit.toString()),
        ).use { while (it.moveToNext()) out += WorkItem(it.getLong(0), it.getInt(1)) }
        return out
    }

    override fun setTesting(worker: Int, pk: Long) {
        writableDatabase.execSQL("INSERT OR REPLACE INTO testing(worker,pk) VALUES(?,?)", arrayOf<Any>(worker, pk))
    }

    override fun clearTesting(worker: Int) {
        writableDatabase.execSQL("DELETE FROM testing WHERE worker=?", arrayOf<Any>(worker))
    }

    override fun saveResult(pk: Long, result: FileResult) {
        val db = writableDatabase
        val row = getRow(pk) ?: return
        val status = Classifier.statusOf(result.reasons)
        val w = if (result.width > 0) result.width else row.width
        val h = if (result.height > 0) result.height else row.height
        val dur = if (result.durationMs >= 0) result.durationMs else row.durationMs
        val anomalies = AnomalyDetector.detect(MediaFacts(row.isVideo, row.size, w, h, dur, result.fps, result.bitrate))
        val cv = ContentValues().apply {
            put("status", status.code)
            put("reasons", Reason.toCsv(result.reasons))
            put("w", w); put("h", h); put("dur", dur)
            put("fps", result.fps); put("bitrate", result.bitrate)
            put("anomalies", Anomaly.toMask(anomalies))
            put("mbps", result.readMbps)
            put("probed", if (result.probed) 1 else 0)
            put("ioerr", result.ioErrors)
        }
        db.update("files", cv, "pk=?", arrayOf(pk.toString()))
    }

    override fun markCrasher(pk: Long, reason: Reason) {
        val cv = ContentValues().apply {
            put("status", Status.CRASHER.code)
            put("reasons", reason.name)
        }
        writableDatabase.update("files", cv, "pk=?", arrayOf(pk.toString()))
    }

    override fun bumpAttempts(pk: Long): Int {
        val db = writableDatabase
        db.execSQL("UPDATE files SET attempts=attempts+1 WHERE pk=?", arrayOf<Any>(pk))
        return db.rawQuery("SELECT attempts FROM files WHERE pk=?", arrayOf(pk.toString())).use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    override fun recoverInterrupted(): Int {
        val db = writableDatabase
        val pks = ArrayList<Long>()
        db.rawQuery("SELECT pk FROM testing", null).use { while (it.moveToNext()) pks += it.getLong(0) }
        db.execSQL("DELETE FROM testing")
        for (pk in pks) if (bumpAttempts(pk) >= 2) markCrasher(pk, Reason.DECODER_CRASH)
        return pks.size
    }

    // ---------------------------------------------------------------- report queries

    private fun Cursor.toProblemRow(): ProblemRow {
        val row = toFileRow()
        val reasons = Reason.parseList(getString(getColumnIndexOrThrow("reasons")))
        val mask = getInt(getColumnIndexOrThrow("anomalies"))
        val folder = row.relPath?.trimEnd('/') ?: row.path?.substringBeforeLast('/', "") ?: ""
        return ProblemRow(
            pk = row.pk, uri = row.uri, name = row.name, folder = folder, sizeBytes = row.size, mime = row.mime,
            isVideo = row.isVideo, status = row.status, reasons = reasons, anomalies = Anomaly.fromMask(mask),
            width = row.width, height = row.height, durationMs = row.durationMs, volume = row.volume,
        )
    }

    fun problemRows(where: String, args: Array<String> = emptyArray(), limit: Int = Int.MAX_VALUE, order: String = "size DESC"): List<ProblemRow> {
        val out = ArrayList<ProblemRow>()
        readableDatabase.rawQuery("SELECT * FROM files WHERE $where ORDER BY $order LIMIT $limit", args).use {
            while (it.moveToNext()) out += it.toProblemRow()
        }
        return out
    }

    fun rowsWithStatus(status: Status, limit: Int) = problemRows("status=?", arrayOf(status.code.toString()), limit)

    fun anomalyCounts(): Map<Anomaly, Int> {
        val out = HashMap<Anomaly, Int>()
        readableDatabase.rawQuery("SELECT anomalies, COUNT(*) FROM files WHERE anomalies!=0 GROUP BY anomalies", null).use {
            while (it.moveToNext()) for (a in Anomaly.fromMask(it.getInt(0))) out[a] = (out[a] ?: 0) + it.getInt(1)
        }
        return out
    }

    fun reasonHistogram(): Map<Reason, Int> {
        val out = HashMap<Reason, Int>()
        readableDatabase.rawQuery("SELECT reasons FROM files WHERE status IN (2,3)", null).use {
            while (it.moveToNext()) for (r in Reason.parseList(it.getString(0))) out[r] = (out[r] ?: 0) + 1
        }
        return out
    }

    fun folderStats(minCountExclusive: Int): List<FolderStat> {
        val out = ArrayList<FolderStat>()
        readableDatabase.rawQuery(
            "SELECT COALESCE(rel,''), COUNT(*), SUM(size) FROM files WHERE source=0 GROUP BY COALESCE(rel,'') HAVING COUNT(*)>? ORDER BY COUNT(*) DESC",
            arrayOf(minCountExclusive.toString()),
        ).use { while (it.moveToNext()) out += FolderStat(it.getString(0).trimEnd('/').ifEmpty { "/" }, it.getInt(1), it.getLong(2)) }
        return out
    }

    fun topFoldersBySize(n: Int): List<FolderStat> {
        val out = ArrayList<FolderStat>()
        readableDatabase.rawQuery(
            "SELECT COALESCE(rel,''), COUNT(*), SUM(size) AS s FROM files WHERE source=0 GROUP BY COALESCE(rel,'') ORDER BY s DESC LIMIT $n", null,
        ).use { while (it.moveToNext()) out += FolderStat(it.getString(0).trimEnd('/').ifEmpty { "/" }, it.getInt(1), it.getLong(2)) }
        return out
    }

    fun sizeByKind(): Pair<Long, Long> =
        readableDatabase.rawQuery("SELECT video, SUM(size) FROM files WHERE source=0 GROUP BY video", null).use {
            var img = 0L; var vid = 0L
            while (it.moveToNext()) if (it.getInt(0) == 1) vid = it.getLong(1) else img = it.getLong(1)
            img to vid
        }

    fun pendingStuck(olderThanSeconds: Long, sample: Int): Pair<Int, List<String>> {
        val cutoff = (System.currentTimeMillis() / 1000 - olderThanSeconds).toString()
        val n = count("pending=1 AND added>0 AND added<?", arrayOf(cutoff))
        val names = ArrayList<String>()
        readableDatabase.rawQuery("SELECT name FROM files WHERE pending=1 AND added>0 AND added<? LIMIT $sample", arrayOf(cutoff)).use {
            while (it.moveToNext()) names += it.getString(0)
        }
        return n to names
    }

    /** Aggregated storage-failure signals: total IO errors, probed files, intermittent, slow, median MB/s, failing volumes. */
    data class IoStats(val ioErrors: Int, val probed: Int, val intermittent: Int, val slow: Int, val medianMbps: Float?, val failingVolumes: List<String>)

    fun ioStats(slowBelowMbps: Float): IoStats {
        val db = readableDatabase
        var io = 0; var probed = 0
        db.rawQuery("SELECT COALESCE(SUM(ioerr),0), COALESCE(SUM(probed),0) FROM files", null).use { if (it.moveToFirst()) { io = it.getInt(0); probed = it.getInt(1) } }
        val intermittent = count("reasons LIKE '%INTERMITTENT_IO%'")
        val speeds = ArrayList<Float>()
        db.rawQuery("SELECT mbps FROM files WHERE probed=1 AND mbps>0", null).use { while (it.moveToNext()) speeds += it.getFloat(0) }
        speeds.sort()
        val median = if (speeds.isEmpty()) null else speeds[speeds.size / 2]
        val failing = ArrayList<String>()
        db.rawQuery("SELECT vol FROM files GROUP BY vol HAVING SUM(ioerr)>0 OR SUM(reasons LIKE '%INTERMITTENT_IO%')>0", null).use {
            while (it.moveToNext()) failing += it.getString(0)
        }
        return IoStats(io, probed, intermittent, speeds.count { it < slowBelowMbps }, median, failing)
    }

    // ---------------------------------------------------------------- extras (orphans, leftovers)

    fun addExtra(kind: String, path: String, size: Long, note: String? = null) {
        writableDatabase.execSQL("INSERT INTO extra(kind,path,size,note) VALUES(?,?,?,?)", arrayOf<Any?>(kind, path, size, note))
    }

    fun extraCount(kind: String): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM extra WHERE kind=?", arrayOf(kind)).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    fun extraSamples(kind: String, n: Int): List<String> {
        val out = ArrayList<String>()
        readableDatabase.rawQuery("SELECT path FROM extra WHERE kind=? ORDER BY id LIMIT $n", arrayOf(kind)).use {
            while (it.moveToNext()) out += it.getString(0).substringAfterLast('/')
        }
        return out
    }

    fun clearExtras(kind: String) {
        writableDatabase.execSQL("DELETE FROM extra WHERE kind=?", arrayOf<Any>(kind))
    }

    // ---------------------------------------------------------------- duplicates

    /** Sizes shared by 2+ files (only real, non-empty, scanned MediaStore files). */
    fun duplicateSizeCandidates(): List<Long> {
        val out = ArrayList<Long>()
        readableDatabase.rawQuery(
            "SELECT size FROM files WHERE size>0 AND source=0 AND status NOT IN (5,6) GROUP BY size HAVING COUNT(*)>1", null,
        ).use { while (it.moveToNext()) out += it.getLong(0) }
        return out
    }

    fun pksWithSize(size: Long): List<Long> {
        val out = ArrayList<Long>()
        readableDatabase.rawQuery("SELECT pk FROM files WHERE size=? AND source=0 AND status NOT IN (5,6)", arrayOf(size.toString())).use {
            while (it.moveToNext()) out += it.getLong(0)
        }
        return out
    }

    fun setPartialHash(pk: Long, hash: String) {
        writableDatabase.execSQL("UPDATE files SET hashp=? WHERE pk=?", arrayOf<Any>(hash, pk))
    }

    fun addDupGroup(grp: Int, pks: List<Long>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (pk in pks) db.execSQL("INSERT INTO dups(grp,pk) VALUES(?,?)", arrayOf<Any>(grp, pk))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun clearDups() = writableDatabase.execSQL("DELETE FROM dups")

    fun dupGroupCount(): Int =
        readableDatabase.rawQuery("SELECT COUNT(DISTINCT grp) FROM dups", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    /** Top groups by reclaimable space, plus the total reclaimable bytes across all groups. */
    fun dupGroups(limit: Int): Pair<List<DupGroup>, Long> {
        val groups = ArrayList<Pair<Int, Long>>() // grp, reclaimable
        var total = 0L
        readableDatabase.rawQuery(
            "SELECT d.grp, MAX(f.size), COUNT(*) FROM dups d JOIN files f ON f.pk=d.pk GROUP BY d.grp", null,
        ).use {
            while (it.moveToNext()) {
                val rec = it.getLong(1) * (it.getInt(2) - 1)
                total += rec
                groups += it.getInt(0) to rec
            }
        }
        val top = groups.sortedByDescending { it.second }.take(limit)
        val result = top.map { (grp, _) ->
            val rows = problemRows("pk IN (SELECT pk FROM dups WHERE grp=?)", arrayOf(grp.toString()), order = "pk")
            DupGroup(rows, rows.firstOrNull()?.sizeBytes ?: 0)
        }
        return result to total
    }

    // ---------------------------------------------------------------- csv export

    fun forEachProblem(action: (ProblemRow) -> Unit) {
        readableDatabase.rawQuery("SELECT * FROM files WHERE status IN (2,3,4,5) OR anomalies!=0 ORDER BY status DESC, size DESC", null).use {
            while (it.moveToNext()) action(it.toProblemRow())
        }
    }
}
