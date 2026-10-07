package il.gallerydoctor.data

import android.content.Context
import il.gallerydoctor.core.ScanSnapshot

/** A few lines in SharedPreferences: what earlier scans saw, so the next report can say what changed. */
object ScanHistory {
    private const val PREFS = "scan_history"
    private const val KEY = "lines"
    private const val KEEP = 12

    private fun read(ctx: Context): List<ScanSnapshot> =
        (ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: "")
            .lines().mapNotNull { ScanSnapshot.decode(it) }

    /** Saves one snapshot; saving the same scan again replaces it, so showing the last report twice is harmless. */
    fun save(ctx: Context, snapshot: ScanSnapshot) {
        val all = (read(ctx).filter { it.startedMillis != snapshot.startedMillis } + snapshot).sortedBy { it.startedMillis }.takeLast(KEEP)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, all.joinToString("\n") { it.encode() }).apply()
    }

    /** The most recent scan that started before [startedMillis]. */
    fun previous(ctx: Context, startedMillis: Long): ScanSnapshot? =
        read(ctx).filter { it.startedMillis < startedMillis }.maxByOrNull { it.startedMillis }
}
