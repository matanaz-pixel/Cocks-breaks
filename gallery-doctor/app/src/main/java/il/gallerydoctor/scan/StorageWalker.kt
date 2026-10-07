package il.gallerydoctor.scan

import il.gallerydoctor.data.StateDb
import java.io.File

/**
 * Adds up the size of every top-level folder of the shared storage, media or not, to answer "where did the space go".
 * Listing only: no file is opened. Folders Android hides from apps (Android/data, Android/obb) are skipped.
 */
object StorageWalker {
    private const val MAX_VISITS = 4_000_000
    private const val MAX_MILLIS = 240_000L
    private const val DETAIL_MIN_BYTES = 5L shl 30
    const val ROOT_FILES = "(קבצים בתיקייה הראשית)"

    /** Returns true if the whole tree was visited. */
    fun walk(root: File, db: StateDb, isActive: () -> Boolean = { true }): Boolean {
        db.clearExtras("STORAGE_DIR")
        val start = System.currentTimeMillis()
        var visits = 0
        var complete = true
        val bytes = LinkedHashMap<String, Long>()
        val counts = HashMap<String, Int>()
        // top-level folder -> (second-level folder -> bytes), kept only to show details of the big ones
        val second = HashMap<String, HashMap<String, Long>>()
        val secondCount = HashMap<String, HashMap<String, Int>>()

        val top = root.listFiles()
        if (top == null) { db.putMeta("storagewalk_complete", "0"); return false }
        for (t in top) {
            if (!isActive()) { complete = false; break }
            if (!t.isDirectory) {
                bytes.merge(ROOT_FILES, t.length(), Long::plus); counts.merge(ROOT_FILES, 1, Int::plus)
                continue
            }
            val stack = ArrayDeque<Pair<File, String?>>() // directory, second-level name (null while at the top level)
            stack.addLast(t to null)
            while (stack.isNotEmpty()) {
                if (!isActive() || visits > MAX_VISITS || System.currentTimeMillis() - start > MAX_MILLIS) { complete = false; break }
                val (dir, sub) = stack.removeLast()
                val children = dir.listFiles() ?: continue
                for (f in children) {
                    visits++
                    if (f.isDirectory) {
                        stack.addLast(f to (sub ?: f.name))
                    } else {
                        val len = f.length()
                        bytes.merge(t.name, len, Long::plus); counts.merge(t.name, 1, Int::plus)
                        val key = sub ?: ROOT_FILES
                        second.getOrPut(t.name) { HashMap() }.merge(key, len, Long::plus)
                        secondCount.getOrPut(t.name) { HashMap() }.merge(key, 1, Int::plus)
                    }
                }
            }
            if (!complete) break
        }

        for ((name, size) in bytes.entries.sortedByDescending { it.value }.take(15)) {
            db.addExtra("STORAGE_DIR", name, size, (counts[name] ?: 0).toString())
            if (size >= DETAIL_MIN_BYTES) {
                second[name]?.entries?.sortedByDescending { it.value }?.take(3)?.forEach { (sub, b) ->
                    if (sub != ROOT_FILES) db.addExtra("STORAGE_DIR", "$name/$sub", b, (secondCount[name]?.get(sub) ?: 0).toString())
                }
            }
        }
        db.putMeta("storagewalk_complete", if (complete) "1" else "0")
        return complete
    }
}
