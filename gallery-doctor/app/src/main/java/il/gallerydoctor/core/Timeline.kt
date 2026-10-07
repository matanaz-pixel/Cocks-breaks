package il.gallerydoctor.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ======================================================================================
// The background recorder keeps a small timeline (one line per sample) so that a problem which comes and goes
// can be caught later: the media database being recreated, the index shrinking, the phone dying on an empty battery.
// Parsing and summarising are pure Kotlin so they are unit-testable on the JVM.
// ======================================================================================

/** One line of the recorder log. */
sealed interface TimelineEvent {
    val ts: Long // epoch millis

    /** A periodic reading of the media index. [version] and [generation] come from MediaStore; -1 / "" when unavailable. */
    data class Sample(
        override val ts: Long, val volume: String, val version: String, val generation: Long,
        val count: Int, val freeBytes: Long, val totalBytes: Long, val battery: Int, val charging: Boolean,
    ) : TimelineEvent

    data class Boot(override val ts: Long) : TimelineEvent
    data class Started(override val ts: Long) : TimelineEvent
    /** [changes] notifications from the media index during one minute. */
    data class Changes(override val ts: Long, val changes: Int) : TimelineEvent
}

object TimelineCodec {
    fun encode(e: TimelineEvent): String = when (e) {
        is TimelineEvent.Sample -> listOf(e.ts, "S", e.volume.replace('|', '_'), e.version.replace('|', '_'), e.generation, e.count, e.freeBytes, e.totalBytes, e.battery, if (e.charging) 1 else 0).joinToString("|")
        is TimelineEvent.Boot -> "${e.ts}|B"
        is TimelineEvent.Started -> "${e.ts}|X"
        is TimelineEvent.Changes -> "${e.ts}|O|${e.changes}"
    }

    fun decode(line: String): TimelineEvent? {
        val p = line.split('|')
        if (p.size < 2) return null
        return try {
            val ts = p[0].toLong()
            when (p[1]) {
                "S" -> if (p.size != 10) null else TimelineEvent.Sample(ts, p[2], p[3], p[4].toLong(), p[5].toInt(), p[6].toLong(), p[7].toLong(), p[8].toInt(), p[9] == "1")
                "B" -> TimelineEvent.Boot(ts)
                "X" -> TimelineEvent.Started(ts)
                "O" -> TimelineEvent.Changes(ts, p[2].toInt())
                else -> null
            }
        } catch (_: NumberFormatException) {
            null
        } catch (_: IndexOutOfBoundsException) {
            null
        }
    }

    fun decodeAll(lines: Sequence<String>): List<TimelineEvent> = lines.mapNotNull { decode(it) }.toList().sortedBy { it.ts }
}

data class Transition(val ts: Long, val volume: String, val from: String, val to: String)
data class CountDrop(val ts: Long, val from: Int, val to: Int, val recoveredAt: Long?)
data class EmptyBatteryShutdown(val bootTs: Long, val lastSeenTs: Long, val battery: Int)

data class TimelineSummary(
    val firstTs: Long = 0,
    val lastTs: Long = 0,
    val samples: Int = 0,
    /** MediaStore version string changed: the media database was recreated or upgraded. */
    val versionChanges: List<Transition> = emptyList(),
    /** MediaStore generation counter went down: the database was recreated and its counter restarted. */
    val generationDrops: List<Transition> = emptyList(),
    val countDrops: List<CountDrop> = emptyList(),
    val emptyBatteryShutdowns: List<EmptyBatteryShutdown> = emptyList(),
    val boots: Int = 0,
    val lowSpaceShare: Int = 0,
    val maxChangesPerMinute: Int = 0,
    val maxChangesAt: Long = 0,
) {
    val spanHours: Long get() = if (samples == 0) 0 else (lastTs - firstTs) / 3_600_000
    val hasData: Boolean get() = samples > 0
    /** Direct observation that the media database was recreated. */
    val databaseRecreated: Boolean get() = versionChanges.isNotEmpty() || generationDrops.isNotEmpty()
    val anyEvent: Boolean get() = databaseRecreated || countDrops.isNotEmpty() || emptyBatteryShutdowns.isNotEmpty()
}

object Timeline {
    /** Fewer samples than this cannot show anything (about 90 minutes at one sample per 10 minutes). */
    const val MIN_SAMPLES = 9
    private const val DROP_MIN_FILES = 100
    private const val DROP_PERCENT = 5
    private const val EMPTY_BATTERY_PERCENT = 5
    private const val LOW_SPACE_PERCENT = 5

    fun summarize(events: List<TimelineEvent>): TimelineSummary {
        val samples = events.filterIsInstance<TimelineEvent.Sample>()
        if (samples.isEmpty()) return TimelineSummary()

        val versions = ArrayList<Transition>()
        val gens = ArrayList<Transition>()
        val drops = ArrayList<CountDrop>()
        val lastByVolume = HashMap<String, TimelineEvent.Sample>()
        // an open drop waits for the count to come back (a rebuild shows as a dip, a deletion as a lasting drop)
        val open = HashMap<String, Int>()
        for (s in samples) {
            val prev = lastByVolume[s.volume]
            if (prev != null) {
                if (prev.version.isNotEmpty() && s.version.isNotEmpty() && prev.version != s.version) versions += Transition(s.ts, s.volume, prev.version, s.version)
                if (prev.generation >= 0 && s.generation >= 0 && s.generation < prev.generation) gens += Transition(s.ts, s.volume, prev.generation.toString(), s.generation.toString())
                val drop = prev.count - s.count
                if (drop >= DROP_MIN_FILES && drop * 100 / maxOf(1, prev.count) >= DROP_PERCENT) {
                    drops += CountDrop(s.ts, prev.count, s.count, null)
                    open[s.volume] = drops.lastIndex
                } else open[s.volume]?.let { idx ->
                    val d = drops[idx]
                    if (s.count >= d.from - d.from * 2 / 100) { drops[idx] = d.copy(recoveredAt = s.ts); open.remove(s.volume) }
                }
            }
            lastByVolume[s.volume] = s
        }

        val sampleTimes = samples.map { it.ts }
        val empty = ArrayList<EmptyBatteryShutdown>()
        val boots = events.filterIsInstance<TimelineEvent.Boot>()
        for (b in boots) {
            val before = samples.lastOrNull { it.ts < b.ts } ?: continue
            if (before.battery in 0..EMPTY_BATTERY_PERCENT && !before.charging) empty += EmptyBatteryShutdown(b.ts, before.ts, before.battery)
        }

        val primary = samples.filter { it.totalBytes > 0 }
        val lowSpace = if (primary.isEmpty()) 0 else primary.count { it.freeBytes * 100 / it.totalBytes < LOW_SPACE_PERCENT || it.freeBytes < 2L * 1024 * 1024 * 1024 } * 100 / primary.size
        val busiest = events.filterIsInstance<TimelineEvent.Changes>().maxByOrNull { it.changes }
        return TimelineSummary(
            firstTs = sampleTimes.min(), lastTs = sampleTimes.max(), samples = samples.size,
            versionChanges = versions, generationDrops = gens, countDrops = drops, emptyBatteryShutdowns = empty,
            boots = boots.size, lowSpaceShare = lowSpace,
            maxChangesPerMinute = busiest?.changes ?: 0, maxChangesAt = busiest?.ts ?: 0,
        )
    }

    private fun dt(ms: Long) = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date(ms))

    fun lines(t: TimelineSummary): List<String> = buildList {
        if (!t.hasData) {
            add("מקליט הרקע לא הופעל או שלא נאספו דגימות. הפעילו אותו במסך הפתיחה ותנו לו לרוץ יום-יומיים, כולל הרגע שבו התקלה קורית.")
            return@buildList
        }
        add("מקליט הרקע אסף ${He.num(t.samples)} דגימות בין ${dt(t.firstTs)} ל-${dt(t.lastTs)} (כ-${t.spanHours} שעות).")
        if (t.samples < MIN_SAMPLES) add("זה מעט מדי כדי להסיק משהו. השאירו אותו פועל זמן ארוך יותר.")
        for (v in t.versionChanges) add("${dt(v.ts)}: גרסת מסד הנתונים של המדיה השתנתה (${v.from} ← ${v.to}). זו ראיה ישירה שהמסד נוצר מחדש או עודכן.")
        for (g in t.generationDrops) add("${dt(g.ts)}: מונה השינויים של מסד המדיה ירד מ-${g.from} ל-${g.to}. זה קורה רק כשהמסד נוצר מחדש מאפס.")
        for (d in t.countDrops) add(
            "${dt(d.ts)}: מספר הקבצים באינדקס ירד מ-${He.num(d.from)} ל-${He.num(d.to)}" +
                (d.recoveredAt?.let { " וחזר ב-${dt(it)}. כך נראית רשימה חלקית בזמן בנייה מחדש." } ?: " ולא חזר עד סוף ההקלטה."),
        )
        for (e in t.emptyBatteryShutdowns) add("${dt(e.bootTs)}: הטלפון הופעל מחדש אחרי שהסוללה הייתה ב-${e.battery}% (נראה לאחרונה ב-${dt(e.lastSeenTs)}). כיבוי מסוללה ריקה קטע כתיבה פעילה.")
        if (t.maxChangesPerMinute >= 200) add("שיא הפעילות של האינדקס: ${He.num(t.maxChangesPerMinute)} שינויים בדקה אחת, ב-${dt(t.maxChangesAt)}.")
        if (t.lowSpaceShare >= 50) add("ב-${t.lowSpaceShare}% מהדגימות האחסון היה כמעט מלא.")
        if (!t.anyEvent && t.samples >= MIN_SAMPLES) add("במשך ההקלטה לא נרשם אירוע: המסד לא נוצר מחדש והאינדקס לא התכווץ.")
    }
}
