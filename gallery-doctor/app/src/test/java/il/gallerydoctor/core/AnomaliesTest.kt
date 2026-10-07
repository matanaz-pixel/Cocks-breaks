package il.gallerydoctor.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnomaliesTest {
    private fun img(w: Int, h: Int, bytes: Long = 3_000_000) =
        MediaFacts(false, bytes, w, h, -1, 0f, 0)

    private fun vid(w: Int, h: Int, bytes: Long = 50_000_000, dur: Long = 60_000, fps: Float = 30f, br: Long = 20_000_000) =
        MediaFacts(true, bytes, w, h, dur, fps, br)

    @Test fun normalPhotoHasNoAnomaly() = assertTrue(AnomalyDetector.detect(img(4000, 3000)).isEmpty())

    @Test fun hugeImages() {
        assertTrue(Anomaly.IMAGE_OVER_50MP in AnomalyDetector.detect(img(9000, 6000)))
        assertTrue(Anomaly.IMAGE_SIDE_OVER_16384 in AnomalyDetector.detect(img(20000, 1000)))
        assertTrue(Anomaly.IMAGE_OVER_30MB in AnomalyDetector.detect(img(4000, 3000, 31L * 1024 * 1024)))
    }

    @Test fun panoramas() {
        assertTrue(Anomaly.PANORAMA_EXTREME in AnomalyDetector.detect(img(12000, 2000)))
        assertTrue(Anomaly.PANORAMA_EXTREME in AnomalyDetector.detect(img(1600, 200)))
        assertTrue(Anomaly.PANORAMA_EXTREME !in AnomalyDetector.detect(img(4032, 3024)))
    }

    @Test fun videos() {
        assertTrue(Anomaly.VIDEO_OVER_2GB in AnomalyDetector.detect(vid(1920, 1080, bytes = 3L * 1024 * 1024 * 1024)))
        assertTrue(Anomaly.VIDEO_8K in AnomalyDetector.detect(vid(7680, 4320)))
        assertTrue(Anomaly.VIDEO_4K_HIGH_BITRATE in AnomalyDetector.detect(vid(3840, 2160, br = 120_000_000)))
        assertTrue(AnomalyDetector.detect(vid(3840, 2160, br = 50_000_000)).isEmpty())
        assertTrue(Anomaly.VIDEO_OVER_60FPS in AnomalyDetector.detect(vid(1920, 1080, fps = 120f)))
        assertTrue(Anomaly.VIDEO_ZERO_DURATION in AnomalyDetector.detect(vid(1920, 1080, dur = 0)))
        assertTrue(Anomaly.VIDEO_ABSURD_DURATION in AnomalyDetector.detect(vid(1920, 1080, dur = 7L * 3600 * 1000)))
        assertTrue(AnomalyDetector.detect(vid(1920, 1080, dur = -1)).isEmpty())
    }

    @Test fun maskRoundTrip() {
        val set = setOf(Anomaly.VIDEO_8K, Anomaly.IMAGE_OVER_30MB)
        assertEquals(set, Anomaly.fromMask(Anomaly.toMask(set)).toSet())
    }
}
