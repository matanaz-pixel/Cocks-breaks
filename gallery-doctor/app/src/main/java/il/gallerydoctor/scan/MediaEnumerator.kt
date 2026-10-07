package il.gallerydoctor.scan

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.MediaStore
import il.gallerydoctor.core.Magic
import il.gallerydoctor.data.FileRow

/** Batched, read-only MediaStore enumeration of Images and Video across every volume (including SD cards). */
object MediaEnumerator {
    private const val BATCH = 1000

    @Suppress("DEPRECATION")
    private val DATA = MediaStore.MediaColumns.DATA

    fun volumes(ctx: Context): List<String> =
        if (Build.VERSION.SDK_INT >= 29) MediaStore.getExternalVolumeNames(ctx).toList() else listOf("external")

    private fun baseUri(volume: String, video: Boolean): Uri =
        if (Build.VERSION.SDK_INT >= 29) {
            if (video) MediaStore.Video.Media.getContentUri(volume) else MediaStore.Images.Media.getContentUri(volume)
        } else {
            if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

    private fun projection(video: Boolean): Array<String> {
        val cols = arrayListOf(
            MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.WIDTH, MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.DATE_MODIFIED, DATA,
        )
        if (video) cols += MediaStore.Video.VideoColumns.DURATION
        if (Build.VERSION.SDK_INT >= 29) {
            cols += MediaStore.MediaColumns.RELATIVE_PATH
            cols += MediaStore.MediaColumns.IS_PENDING
        }
        if (Build.VERSION.SDK_INT >= 30) cols += MediaStore.MediaColumns.IS_TRASHED
        return cols.toTypedArray()
    }

    private fun queryArgs(lastId: Long): Bundle = Bundle().apply {
        putString(ContentResolver.QUERY_ARG_SQL_SELECTION, "${MediaStore.MediaColumns._ID} > ?")
        putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf(lastId.toString()))
        putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${MediaStore.MediaColumns._ID} ASC")
        putInt(ContentResolver.QUERY_ARG_LIMIT, BATCH)
        if (Build.VERSION.SDK_INT >= 30) putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
        if (Build.VERSION.SDK_INT >= 31) putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
    }

    /** Streams batches of rows to [onBatch]. Returns the number of rows read. Never writes anything. */
    fun enumerate(ctx: Context, onBatch: (List<FileRow>) -> Unit, isActive: () -> Boolean = { true }): Int {
        var total = 0
        for (volume in volumes(ctx)) {
            for (video in listOf(false, true)) {
                var uri = baseUri(volume, video)
                if (Build.VERSION.SDK_INT == 29) uri = MediaStore.setIncludePending(uri)
                var lastId = 0L
                while (isActive()) {
                    val batch = ArrayList<FileRow>(BATCH)
                    val cursor = try {
                        ctx.contentResolver.query(uri, projection(video), queryArgs(lastId), null)
                    } catch (_: SecurityException) {
                        null
                    } catch (_: IllegalArgumentException) {
                        null
                    } ?: break
                    cursor.use { c ->
                        val idI = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                        while (c.moveToNext()) {
                            val row = c.toRow(volume, video, uri, idI)
                            lastId = row.mediaId
                            batch += row
                        }
                    }
                    if (batch.isEmpty()) break
                    onBatch(batch)
                    total += batch.size
                    if (batch.size < BATCH) break
                }
            }
        }
        return total
    }

    private fun Cursor.str(name: String): String? = getColumnIndex(name).takeIf { it >= 0 }?.let { if (isNull(it)) null else getString(it) }
    private fun Cursor.lng(name: String, def: Long = 0): Long = getColumnIndex(name).takeIf { it >= 0 }?.let { if (isNull(it)) def else getLong(it) } ?: def
    private fun Cursor.int(name: String, def: Int = 0): Int = lng(name, def.toLong()).toInt()

    private fun Cursor.toRow(volume: String, video: Boolean, base: Uri, idI: Int): FileRow {
        val id = getLong(idI)
        val path = str(DATA)
        val name = str(MediaStore.MediaColumns.DISPLAY_NAME) ?: path?.substringAfterLast('/') ?: "id$id"
        val rel = str(MediaStore.MediaColumns.RELATIVE_PATH) ?: path?.let { deriveRelative(it) }
        return FileRow(
            volume = volume, mediaId = id,
            uri = ContentUris.withAppendedId(base, id).toString(),
            name = name, path = path, relPath = rel,
            size = lng(MediaStore.MediaColumns.SIZE),
            mime = str(MediaStore.MediaColumns.MIME_TYPE),
            isVideo = video,
            width = int(MediaStore.MediaColumns.WIDTH), height = int(MediaStore.MediaColumns.HEIGHT),
            durationMs = if (video) lng(MediaStore.Video.VideoColumns.DURATION, -1) else -1,
            dateAdded = lng(MediaStore.MediaColumns.DATE_ADDED), dateModified = lng(MediaStore.MediaColumns.DATE_MODIFIED),
            pending = int(MediaStore.MediaColumns.IS_PENDING) == 1,
            trashed = int(MediaStore.MediaColumns.IS_TRASHED) == 1,
        )
    }

    /** "/storage/emulated/0/DCIM/Camera/a.jpg" -> "DCIM/Camera/" ; SD cards: "/storage/ABCD-1234/DCIM/x.jpg" -> "DCIM/". */
    fun deriveRelative(path: String): String? {
        val m = Regex("^/storage/[^/]+/(?:\\d+/)?(.*)/[^/]*$").find(path) ?: return null
        return m.groupValues[1].let { if (it.isEmpty()) "" else "$it/" }
    }

    /**
     * Index-stability probe: current (volume:id -> size:modified) of every row, read with the same
     * query path as [enumerate]. Used to diff against the first enumeration 60 seconds later.
     */
    fun snapshot(ctx: Context): HashMap<String, String> {
        val map = HashMap<String, String>()
        enumerate(ctx, { rows -> for (r in rows) map[r.volume + ":" + r.mediaId] = r.size.toString() + ":" + r.dateModified })
        return map
    }

    /** True if the file extension is one we treat as media when walking the filesystem. */
    fun isMediaName(name: String): Boolean {
        val ext = Magic.extensionOf(name)
        return Magic.fromExtension(ext) != il.gallerydoctor.core.Container.UNKNOWN || ext in setOf("dng", "tif", "tiff", "cr2", "nef", "arw")
    }
}

/** Optional pass through a folder tree picked with ACTION_OPEN_DOCUMENT_TREE (works for SD cards). */
object SafEnumerator {
    private val COLS = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
    )

    /**
     * Walks the tree and emits media files that are NOT already in MediaStore (matched by name+size).
     * Those are, by definition, files the gallery cannot see.
     */
    fun enumerate(
        ctx: Context, tree: Uri, indexedKeys: Set<String>,
        onBatch: (List<FileRow>) -> Unit, isActive: () -> Boolean = { true },
    ): Int {
        var found = 0
        val treeId = DocumentsContract.getTreeDocumentId(tree)
        val stack = ArrayDeque<Pair<String, String>>() // documentId, relative path
        stack.addLast(treeId to "")
        var batch = ArrayList<FileRow>()
        var nextId = -1L
        while (stack.isNotEmpty() && isActive()) {
            val (docId, rel) = stack.removeLast()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
            val cursor = try {
                ctx.contentResolver.query(children, COLS, null, null, null)
            } catch (_: Exception) {
                null
            } ?: continue
            cursor.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0)
                    val name = c.getString(1) ?: continue
                    val mime = c.getString(2)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (name.startsWith(".thumbnails") || name == ".trash") continue
                        stack.addLast(id to "$rel$name/")
                        continue
                    }
                    val isImage = mime?.startsWith("image/") == true
                    val isVideo = mime?.startsWith("video/") == true
                    if (!isImage && !isVideo && !MediaEnumerator.isMediaName(name)) continue
                    val size = if (c.isNull(3)) 0 else c.getLong(3)
                    if ((name + "|" + size) in indexedKeys) continue
                    batch += FileRow(
                        source = FileRow.SOURCE_SAF, volume = "saf", mediaId = nextId--,
                        uri = DocumentsContract.buildDocumentUriUsingTree(tree, id).toString(),
                        name = name, path = null, relPath = rel, size = size, mime = mime,
                        isVideo = isVideo || Magic.isVideoContainer(Magic.fromExtension(Magic.extensionOf(name))),
                        dateModified = if (c.isNull(4)) 0 else c.getLong(4) / 1000,
                    )
                    found++
                    if (batch.size >= 500) { onBatch(batch); batch = ArrayList() }
                }
            }
        }
        if (batch.isNotEmpty()) onBatch(batch)
        return found
    }

    fun displayName(ctx: Context, tree: Uri): String = try {
        ctx.contentResolver.query(
            DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)),
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null,
        )?.use { if (it.moveToFirst()) it.getString(0) else null } ?: tree.lastPathSegment ?: "תיקייה"
    } catch (_: Exception) {
        tree.lastPathSegment ?: "תיקייה"
    }
}
