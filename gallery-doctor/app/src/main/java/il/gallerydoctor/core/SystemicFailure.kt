package il.gallerydoctor.core

/**
 * A check that fails on a large share of ALL files is almost certainly broken itself (or the phone's decoder is),
 * not the files. Real corruption is rare and scattered; "nearly every photo is corrupt" is a tooling signal.
 *
 * Such reasons are removed from the per-file verdicts and reported separately, so one broken check can never
 * turn a whole library into "77,000 corrupt files" again.
 */
object SystemicFailure {
    const val SHARE = 0.25
    const val MIN_COUNT = 50

    /** Findings that stand on their own: real IO trouble, crashes, hangs, empty files, ghost rows. Never neutralised. */
    private val exempt = setOf(
        Reason.DECODER_CRASH, Reason.HANG_TIMEOUT, Reason.SLOW_DECODE, Reason.WORKER_DIED_ONCE, Reason.GHOST_ROW,
        Reason.UNSUPPORTED_FORMAT, Reason.ZERO_BYTES, Reason.OPEN_FAILED, Reason.IO_READ_ERROR, Reason.INTERMITTENT_IO,
    )
    private val videoOnly = setOf(Reason.FRAME_MID_FAIL, Reason.FRAME_END_FAIL, Reason.VIDEO_OPEN_FAIL)
    private val isoShared = setOf(Reason.ISO_NO_FTYP, Reason.ISO_BAD_BOX, Reason.ISO_NO_MOOV, Reason.ISO_TRUNCATED, Reason.ISO_NO_TRACKS, Reason.ISO_NO_META)

    /** Reasons (with their counts) whose share of the files they apply to is implausibly high. */
    fun detect(histogram: Map<Reason, Int>, images: Int, videos: Int): Map<Reason, Int> =
        histogram.filter { (reason, n) ->
            if (reason in exempt || n < MIN_COUNT) return@filter false
            val population = when (reason) {
                in videoOnly -> videos
                in isoShared -> images + videos
                else -> images
            }
            population > 0 && n >= SHARE * population
        }

    fun toMeta(m: Map<Reason, Int>): String = m.entries.joinToString(";") { "${it.key.name}=${it.value}" }

    fun fromMeta(s: String?): Map<Reason, Int> =
        s.orEmpty().split(';').mapNotNull { part ->
            val (name, n) = part.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
            val reason = Reason.entries.firstOrNull { it.name == name } ?: return@mapNotNull null
            n.toIntOrNull()?.let { reason to it }
        }.toMap()
}
