package il.gallerydoctor.recorder

import android.content.Context
import android.os.SystemClock
import il.gallerydoctor.core.TimelineCodec
import il.gallerydoctor.core.TimelineEvent
import java.io.File

/** The recorder's small append-only log (one line per event) plus its on/off switch. Everything stays on the phone. */
object RecorderLog {
    private const val FILE = "recorder.log"
    private const val PREFS = "recorder"
    private const val KEY_ON = "enabled"
    private const val KEY_BOOT = "last_boot"
    private const val MAX_LINES = 30_000
    private const val TRIM_TO = 20_000
    private val lock = Any()

    private fun file(ctx: Context) = File(ctx.filesDir, FILE)

    fun isEnabled(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ON, false)

    fun setEnabled(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ON, on).apply()
    }

    fun append(ctx: Context, e: TimelineEvent) = synchronized(lock) {
        val f = file(ctx)
        f.appendText(TimelineCodec.encode(e) + "\n")
        // a rough size check is enough: about 60 bytes per line
        if (f.length() > MAX_LINES * 60L) {
            val keep = f.readLines().takeLast(TRIM_TO)
            f.writeText(keep.joinToString("\n", postfix = "\n"))
        }
    }

    fun readAll(ctx: Context): List<TimelineEvent> = synchronized(lock) {
        val f = file(ctx)
        if (!f.exists()) emptyList() else f.useLines { TimelineCodec.decodeAll(it) }
    }

    /**
     * Notes a reboot once. The boot time is derived from the uptime clock, so this also works when the boot
     * broadcast never arrived (Xiaomi blocks it unless auto-start is allowed). Returns true if a boot was logged.
     */
    fun noteBoot(ctx: Context): Boolean {
        val bootTime = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_BOOT, 0L)
        if (last != 0L && kotlin.math.abs(bootTime - last) < 120_000) return false
        prefs.edit().putLong(KEY_BOOT, bootTime).apply()
        if (last == 0L) return false // first run: there is no earlier boot to compare with
        append(ctx, TimelineEvent.Boot(bootTime))
        return true
    }
}
