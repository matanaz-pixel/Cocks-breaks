package il.gallerydoctor.scan

import il.gallerydoctor.data.StateDb
import java.io.File

/**
 * Looks for what the media index does NOT know about, inside the normal media folders only:
 *  - orphans: media files on disk that are not indexed
 *  - leftovers: stale ".pending-<expiry>-name" / ".trashed-<expiry>-name" files
 *
 * Only DCIM, Pictures, Movies, Download, WhatsApp, Telegram and Android/media are walked. With the
 * media permission Android does not allow listing the rest of the storage, so we don't pretend to.
 */
object FsWalker {
    private val ROOT_DIRS = listOf("DCIM", "Pictures", "Movies", "Download", "WhatsApp", "Telegram", "Snapchat", "MIUI", "Android/media")
    private val TEMP = Regex("^\\.(pending|trashed)-(\\d+)-(.+)$")
    private const val MAX_STORED = 20_000
    private const val GRACE_SECONDS = 2 * 86_400L
    private const val NOMEDIA_MIN_FILES = 10
    private const val HIDDEN_MIN_FILES = 10
    private const val HIDDEN_VISIT_CAP = 200_000

    data class Result(val orphans: Int, val leftovers: Int, val inaccessibleDirs: Int)

    fun walk(volumeRoots: List<File>, db: StateDb, nowSeconds: Long = System.currentTimeMillis() / 1000, isActive: () -> Boolean = { true }): Result {
        db.clearExtras("ORPHAN")
        db.clearExtras("LEFTOVER")
        db.clearExtras("NOMEDIA")
        db.clearExtras("HIDDEN_DIR")
        db.clearExtras("THUMBS")
        var orphans = 0
        var leftovers = 0
        var inaccessible = 0
        var stored = 0

        for (root in volumeRoots) for (sub in ROOT_DIRS) {
            val start = File(root, sub)
            if (!start.isDirectory) continue
            val stack = ArrayDeque<File>()
            stack.addLast(start)
            while (stack.isNotEmpty() && isActive()) {
                val dir = stack.removeLast()
                val children = dir.listFiles()
                if (children == null) { inaccessible++; continue }
                if (children.any { it.name == ".nomedia" }) {
                    // deliberately hidden from the gallery: not an orphan, but worth reporting when it hides many files
                    val hidden = children.count { !it.isDirectory && !it.name.startsWith('.') && MediaEnumerator.isMediaName(it.name) }
                    if (hidden >= NOMEDIA_MIN_FILES) db.addExtra("NOMEDIA", dir.path, hidden.toLong())
                    continue
                }
                for (f in children) {
                    val name = f.name
                    if (f.isDirectory) {
                        when {
                            name == ".thumbnails" -> f.list()?.size?.let { if (it >= 1000) db.addExtra("THUMBS", f.path, it.toLong()) }
                            name.startsWith('.') -> hiddenStats(f, db)
                            else -> stack.addLast(f)
                        }
                        continue
                    }
                    val temp = TEMP.matchEntire(name)
                    if (temp != null) {
                        val expiry = temp.groupValues[2].toLongOrNull()
                        // Android deletes these itself some time after the expiry; only flag clearly overdue ones.
                        if (expiry == null || expiry + GRACE_SECONDS < nowSeconds) {
                            leftovers++
                            if (stored++ < MAX_STORED) db.addExtra("LEFTOVER", f.path, f.length(), temp.groupValues[1])
                        }
                        continue
                    }
                    if (name.startsWith('.') || !MediaEnumerator.isMediaName(name)) continue
                    val len = f.length()
                    if (len <= 0) continue
                    if (!db.pathIndexed(f.path)) {
                        orphans++
                        if (stored++ < MAX_STORED) db.addExtra("ORPHAN", f.path, len)
                    }
                }
            }
        }
        return Result(orphans, leftovers, inaccessible)
    }

    /** Counts media files inside a hidden folder (private album, recycle bin) without treating them as orphans. */
    private fun hiddenStats(start: File, db: StateDb) {
        var files = 0
        var bytes = 0L
        var visited = 0
        val stack = ArrayDeque<File>()
        stack.addLast(start)
        while (stack.isNotEmpty() && visited < HIDDEN_VISIT_CAP) {
            val children = stack.removeLast().listFiles() ?: continue
            for (f in children) {
                visited++
                if (f.isDirectory) stack.addLast(f)
                else if (MediaEnumerator.isMediaName(f.name)) { files++; bytes += f.length() }
            }
        }
        if (files >= HIDDEN_MIN_FILES) db.addExtra("HIDDEN_DIR", start.path, bytes, files.toString())
    }
}
