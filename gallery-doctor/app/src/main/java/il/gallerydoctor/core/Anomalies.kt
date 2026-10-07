package il.gallerydoctor.core

enum class Anomaly(val he: String) {
    IMAGE_OVER_50MP("תמונה ברזולוציה גבוהה במיוחד (מעל 52 מגה-פיקסל, כמו מצב 108MP)"),
    IMAGE_SIDE_OVER_16384("צלע של תמונה מעל 16,384 פיקסלים"),
    IMAGE_OVER_30MB("תמונה כבדה מעל 30MB"),
    PANORAMA_EXTREME("פנורמה בפרופורציות קיצוניות (עומס על המפענח)"),
    VIDEO_OVER_2GB("סרטון מעל 2GB"),
    VIDEO_8K("סרטון ברזולוציית 8K"),
    VIDEO_4K_HIGH_BITRATE("סרטון 4K בקצב נתונים גבוה מאוד"),
    VIDEO_OVER_60FPS("סרטון מעל 60 פריימים לשנייה"),
    VIDEO_ZERO_DURATION("סרטון באורך 0"),
    VIDEO_ABSURD_DURATION("סרטון באורך חריג (מעל 6 שעות)");

    val bit: Int get() = 1 shl ordinal

    companion object {
        fun fromMask(mask: Int): List<Anomaly> = entries.filter { mask and it.bit != 0 }
        fun toMask(set: Collection<Anomaly>): Int = set.fold(0) { acc, a -> acc or a.bit }
    }
}

data class MediaFacts(
    val isVideo: Boolean,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long, // -1 when unknown
    val fps: Float,
    val bitrate: Long,
    /** A JPEG with a short video embedded (Google/Xiaomi/Samsung motion photo). Its size is mostly the video. */
    val motionPhoto: Boolean = false,
)

object AnomalyDetector {
    /** MVIMG_xxx.jpg (Google/Xiaomi), xxx.MP.jpg (Pixel), xxxMP.jpg (Samsung): a photo with an embedded clip. */
    fun isMotionPhotoName(name: String): Boolean =
        name.startsWith("MVIMG_") || Regex("""(?i)(\.|_)?MP\.jpe?g$""").containsMatchIn(name)

    /**
     * "More than 50 MP", with a margin: a standard 50MP sensor outputs 8192x6144 = 50.3 MP, which is a normal photo
     * on today's phones (on a real report 164 of 203 "heavy" images were exactly that). Real stress starts above it,
     * e.g. 108 MP modes.
     */
    const val MAX_PIXELS = 52_000_000L
    const val MAX_SIDE = 16_384
    const val MAX_IMAGE_BYTES = 30L * 1024 * 1024
    const val MAX_VIDEO_BYTES = 2L * 1024 * 1024 * 1024
    const val HIGH_BITRATE = 100_000_000L
    const val MAX_FPS = 60.5f
    const val ABSURD_DURATION_MS = 6L * 60 * 60 * 1000

    fun detect(f: MediaFacts): Set<Anomaly> {
        val out = LinkedHashSet<Anomaly>()
        val longSide = maxOf(f.width, f.height)
        val shortSide = minOf(f.width, f.height)
        if (!f.isVideo) {
            if (f.width > 0 && f.height > 0 && f.width.toLong() * f.height > MAX_PIXELS) out += Anomaly.IMAGE_OVER_50MP
            if (longSide > MAX_SIDE) out += Anomaly.IMAGE_SIDE_OVER_16384
            if (f.sizeBytes > MAX_IMAGE_BYTES && !f.motionPhoto) out += Anomaly.IMAGE_OVER_30MB
            if (shortSide > 0) {
                val ratio = longSide.toDouble() / shortSide
                if ((ratio >= 4.0 && longSide >= 4000) || ratio >= 8.0) out += Anomaly.PANORAMA_EXTREME
            }
        } else {
            if (f.sizeBytes > MAX_VIDEO_BYTES) out += Anomaly.VIDEO_OVER_2GB
            if (longSide >= 7680) out += Anomaly.VIDEO_8K
            else if (longSide >= 3840 && f.bitrate >= HIGH_BITRATE) out += Anomaly.VIDEO_4K_HIGH_BITRATE
            if (f.fps > MAX_FPS) out += Anomaly.VIDEO_OVER_60FPS
            if (f.durationMs == 0L && f.sizeBytes > 0) out += Anomaly.VIDEO_ZERO_DURATION
            if (f.durationMs > ABSURD_DURATION_MS) out += Anomaly.VIDEO_ABSURD_DURATION
        }
        return out
    }
}
