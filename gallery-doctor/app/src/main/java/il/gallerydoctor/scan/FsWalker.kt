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
    private val ROOT_DIRS = listOf("DCIM", "Pictures", "Movies", "Download", "WhatsApp", "Telegram", "Snapchat", "Android/media")
    private val TEMP = Regex("^\\.(pending|trashed)-(\\d+)-(.+)$")
    private const val MAX_STORED = 20_000

    data class Result(val orphans: Int, val leftovers: Int, val inaccessibleDirs: Int)

    fun walk(volumeRoots: List<File>, db: StateDb, nowSeconds: Long = System.currentTimeMillis() / 1000, isActive: () -> Boolean = { true }): Result {
        db.clearExtras("ORPHAN")
        db.clearExtras("LEFTOVER")
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
                if (children.any { it.name == ".nomedia" }) continue // deliberately hidden from the gallery
                for (f in children) {
                    val name = f.name
                    if (f.isDirectory) {
                        if (!name.startsWith('.')) stack.addLast(f)
                        continue
                    }
                    val temp = TEMP.matchEntire(name)
                    if (temp != null) {
                        val expiry = temp.groupValues[2].toLongOrNull()
                        if (expiry == null || expiry < nowSeconds) {
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
}
