package il.gallerydoctor.worker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import il.gallerydoctor.core.Container
import il.gallerydoctor.core.FileResult
import il.gallerydoctor.core.Magic
import il.gallerydoctor.core.Reason
import il.gallerydoctor.core.StructureAnalyzer
import il.gallerydoctor.core.StructureReport
import il.gallerydoctor.scan.ChannelSource
import java.io.FileNotFoundException
import java.io.IOException
import java.util.zip.CRC32

/** What the coordinator asks a worker to test. Plain data so it can cross the process boundary in a Bundle. */
data class TestRequest(
    val pk: Long,
    val uri: String,
    val name: String,
    val mime: String?,
    val isVideo: Boolean,
    val size: Long,
    /** Run the L3 decode test (downscaled image decode / video frame extraction). */
    val decode: Boolean,
    /** Also decode when L2 already found the file BROKEN (a sampled subset). */
    val decodeBroken: Boolean,
    /** Read the whole file twice to measure speed and detect unstable reads. */
    val ioProbe: Boolean,
)

/**
 * The risky part of the app. Runs ONLY inside a ":scannerN" process: file open, L1 header checks,
 * L2 structure parsing, L3 decoding. A native decoder crash here kills this process, not the UI.
 * Read-only: files are opened with mode "r" and never written.
 */
class FileTester(private val ctx: Context) {
    companion object {
        const val TARGET_PX = 512
        const val PROBE_MAX_BYTES = 32L * 1024 * 1024
        const val PROBE_MIN_BYTES = 1L * 1024 * 1024
    }

    fun test(req: TestRequest): FileResult {
        debugHooks(req.name)
        val reasons = LinkedHashSet<Reason>()
        var width = 0
        var height = 0
        var durationMs = -1L
        var fps = 0f
        var bitrate = 0L
        var ioErrors = 0
        var mbps = 0f
        var probed = false
        var decoded = false
        var decodeMs = 0
        val uri = Uri.parse(req.uri)

        try {
            // ---- open (ghost-row detection: the index says the file exists, does it?) ----
            val src = try {
                ChannelSource.open(ctx, uri)
            } catch (e: FileNotFoundException) {
                val msg = e.message ?: ""
                if (msg.contains("ENOENT") || msg.contains("No such file")) reasons += Reason.GHOST_ROW
                else { reasons += Reason.OPEN_FAILED; ioErrors++ }
                return FileResult(reasons.toList(), ioErrors = ioErrors)
            } catch (e: SecurityException) {
                reasons += Reason.OPEN_FAILED
                return FileResult(reasons.toList())
            } catch (e: IOException) {
                reasons += Reason.IO_READ_ERROR
                return FileResult(reasons.toList(), ioErrors = 1)
            }

            val report: StructureReport = src.use { s ->
                // ---- L1 + L2: header and structure ----
                val r = try {
                    StructureAnalyzer.analyze(s, Magic.extensionOf(req.name), req.mime)
                } catch (e: IOException) {
                    ioErrors++
                    reasons += Reason.IO_READ_ERROR
                    StructureReport(Container.UNKNOWN, emptyList())
                }

                // ---- storage probe: full read twice, compare CRC, measure speed ----
                if (req.ioProbe && req.size in 1..PROBE_MAX_BYTES) {
                    val p = ioProbe(s)
                    probed = true
                    ioErrors += p.errors
                    mbps = p.mbps
                    if (p.unstable) reasons += Reason.INTERMITTENT_IO
                }
                r
            }
            reasons += report.reasons
            width = report.width
            height = report.height

            val container = report.container
            val isImageContainer = container in setOf(Container.JPEG, Container.PNG, Container.GIF, Container.WEBP, Container.BMP, Container.HEIF)
            val isVideo = req.isVideo || Magic.isVideoContainer(container)
            val critical = reasons.any { it == Reason.ZERO_BYTES || it == Reason.BAD_MAGIC }

            // ---- L1 (Android side): image bounds ----
            if (isImageContainer && !critical) {
                if (supportsDecode(container)) {
                    val b = imageBounds(uri)
                    when {
                        b == null || b.first < 0 || b.second < 0 -> reasons += Reason.BOUNDS_FAIL
                        b.first == 0 || b.second == 0 -> reasons += Reason.BAD_DIMENSIONS
                        else -> { width = b.first; height = b.second }
                    }
                } else reasons += Reason.UNSUPPORTED_FORMAT
                if (container == Container.JPEG || container == Container.HEIF || container == Container.PNG || container == Container.WEBP) {
                    if (!exifOk(uri)) reasons += Reason.EXIF_ERROR
                }
            }

            // ---- L3: decode test ----
            val brokenAtL2 = reasons.any { it.severity >= il.gallerydoctor.core.Severity.BROKEN }
            val wantDecode = req.decode && !critical && (!brokenAtL2 || req.decodeBroken)
            if (wantDecode) {
                val t0 = System.nanoTime()
                if (isVideo) {
                    val v = testVideo(uri)
                    reasons += v.reasons
                    if (v.durationMs >= 0) durationMs = v.durationMs
                    if (v.width > 0) { width = v.width; height = v.height }
                    fps = v.fps
                    bitrate = v.bitrate
                    decoded = true
                } else if (isImageContainer && supportsDecode(container)) {
                    reasons += testImage(uri)
                    decoded = true
                }
                decodeMs = ((System.nanoTime() - t0) / 1_000_000).toInt()
            }
        } catch (e: OutOfMemoryError) {
            reasons += Reason.DECODE_OOM
        } catch (e: IOException) {
            reasons += Reason.IO_READ_ERROR
            ioErrors++
        } catch (e: Throwable) {
            reasons += Reason.DECODE_FAIL
        }
        return FileResult(reasons.toList(), width, height, durationMs, fps, bitrate, ioErrors, mbps, probed, decoded, decodeMs)
    }

    // ------------------------------------------------------------------ helpers

    private fun supportsDecode(c: Container): Boolean = when (c) {
        Container.HEIF -> Build.VERSION.SDK_INT >= 28
        else -> true
    }

    private fun imageBounds(uri: Uri): Pair<Int, Int>? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val stream = ctx.contentResolver.openInputStream(uri) ?: return null
        // In bounds-only mode decodeStream ALWAYS returns null. The answer is in outWidth/outHeight (-1 on failure),
        // so the return value must not be used to decide success (that mistake flagged every image as corrupt).
        stream.use { BitmapFactory.decodeStream(it, null, opts) }
        return opts.outWidth to opts.outHeight
    }

    private fun exifOk(uri: Uri): Boolean = try {
        ctx.contentResolver.openInputStream(uri)?.use { ExifInterface(it).getAttribute(ExifInterface.TAG_DATETIME) }
        true
    } catch (e: IOException) {
        false
    } catch (e: RuntimeException) {
        false
    }

    /** Downscaled decode only. Full-resolution decoding is never done. */
    private fun testImage(uri: Uri): List<Reason> {
        val out = ArrayList<Reason>()
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                var partial = false
                val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { decoder, info, _ ->
                    val longSide = maxOf(info.size.width, info.size.height)
                    decoder.setTargetSampleSize(maxOf(1, longSide / TARGET_PX))
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.setOnPartialImageListener { partial = true; true }
                }
                bmp.recycle()
                if (partial) out += Reason.DECODE_PARTIAL
            } else {
                val bounds = imageBounds(uri)
                var sample = 1
                if (bounds != null) while (maxOf(bounds.first, bounds.second) / sample > TARGET_PX * 2) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.RGB_565 }
                val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                if (bmp == null) out += Reason.DECODE_FAIL else bmp.recycle()
            }
        } catch (e: OutOfMemoryError) {
            out += Reason.DECODE_OOM
        } catch (e: IOException) { // includes ImageDecoder.DecodeException
            out += Reason.DECODE_FAIL
        } catch (e: RuntimeException) {
            out += Reason.DECODE_FAIL
        }
        return out
    }

    private class VideoOutcome(val reasons: List<Reason>, val durationMs: Long, val width: Int, val height: Int, val fps: Float, val bitrate: Long)

    /** One frame near the middle and one near the end. Frames are downscaled where the API allows. */
    private fun testVideo(uri: Uri): VideoOutcome {
        val reasons = ArrayList<Reason>()
        val mmr = MediaMetadataRetriever()
        try {
            try {
                mmr.setDataSource(ctx, uri)
            } catch (e: RuntimeException) {
                return VideoOutcome(listOf(Reason.VIDEO_OPEN_FAIL), -1, 0, 0, 0f, 0)
            }
            fun meta(key: Int) = mmr.extractMetadata(key)
            val dur = meta(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: -1L
            val w = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val bitrate = meta(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L
            var fps = meta(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull() ?: 0f
            if (Build.VERSION.SDK_INT >= 28 && dur > 0) {
                val frames = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toLongOrNull()
                if (frames != null && frames > 0) fps = frames * 1000f / dur
            }
            val hasVideo = meta(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
            if (hasVideo) {
                val midUs = maxOf(0L, dur) * 1000 / 2
                val endUs = maxOf(midUs, (dur - 500) * 1000)
                val mid = frame(mmr, midUs)
                if (mid == null) reasons += Reason.FRAME_MID_FAIL else mid.recycle()
                if (mid != null && endUs > midUs) {
                    val end = frame(mmr, endUs)
                    if (end == null) reasons += Reason.FRAME_END_FAIL else end.recycle()
                }
            }
            return VideoOutcome(reasons, dur, w, h, fps, bitrate)
        } catch (e: OutOfMemoryError) {
            return VideoOutcome(listOf(Reason.DECODE_OOM), -1, 0, 0, 0f, 0)
        } catch (e: RuntimeException) {
            return VideoOutcome(listOf(Reason.VIDEO_OPEN_FAIL), -1, 0, 0, 0f, 0)
        } finally {
            try { mmr.release() } catch (_: Exception) { }
        }
    }

    private fun frame(mmr: MediaMetadataRetriever, timeUs: Long): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= 27) mmr.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST, TARGET_PX, TARGET_PX)
        else mmr.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
    } catch (e: RuntimeException) {
        null
    }

    private class Probe(val errors: Int, val mbps: Float, val unstable: Boolean)

    /** One full sequential read. Returns the CRC32, or null if the read failed or ended early. */
    private fun readAll(src: ChannelSource, buf: ByteArray): Long? = try {
        val crc = CRC32()
        var pos = 0L
        while (pos < src.size) {
            val n = src.read(pos, buf, 0, buf.size)
            if (n <= 0) break
            crc.update(buf, 0, n)
            pos += n
        }
        if (pos < src.size) null else crc.value
    } catch (e: IOException) {
        null
    }

    /** Reads the file twice. Unequal CRCs, or an error in only one pass, mean an unstable read. */
    private fun ioProbe(src: ChannelSource): Probe {
        val buf = ByteArray(256 * 1024)
        val t0 = System.nanoTime()
        val first = readAll(src, buf)
        val secs = (System.nanoTime() - t0) / 1e9
        val second = readAll(src, buf)
        val errors = (if (first == null) 1 else 0) + (if (second == null) 1 else 0)
        val mbps = if (first != null && src.size >= PROBE_MIN_BYTES && secs > 0) (src.size / 1e6 / secs).toFloat() else 0f
        val unstable = (first == null) != (second == null) || (first != null && first != second)
        return Probe(errors, mbps, unstable)
    }

    /** Debug builds only: lets the manual test plan prove crash isolation with files named GD_TEST_CRASH_* / GD_TEST_HANG_*. */
    private fun debugHooks(name: String) {
        if (!il.gallerydoctor.BuildConfig.DEBUG) return
        when {
            name.startsWith("GD_TEST_CRASH_") -> android.os.Process.killProcess(android.os.Process.myPid())
            name.startsWith("GD_TEST_HANG_") -> Thread.sleep(Long.MAX_VALUE)
        }
    }
}

