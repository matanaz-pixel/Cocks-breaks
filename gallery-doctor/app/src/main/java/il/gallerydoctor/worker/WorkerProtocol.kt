package il.gallerydoctor.worker

import android.os.Bundle
import il.gallerydoctor.core.FileResult
import il.gallerydoctor.core.Reason

/** Messenger protocol between the UI-process client and the isolated scanner services. */
object WorkerProtocol {
    const val MSG_PING = 1
    const val MSG_PONG = 2
    const val MSG_TEST = 3
    const val MSG_RESULT = 4

    /** Bundle key holding the request sequence number. The worker echoes it back in the result. */
    const val SEQ = "seq"

    fun requestToBundle(r: TestRequest) = Bundle().apply {
        putLong("pk", r.pk)
        putString("uri", r.uri)
        putString("name", r.name)
        putString("mime", r.mime)
        putBoolean("video", r.isVideo)
        putLong("size", r.size)
        putBoolean("decode", r.decode)
        putBoolean("decodeBroken", r.decodeBroken)
        putBoolean("ioProbe", r.ioProbe)
    }

    fun bundleToRequest(b: Bundle) = TestRequest(
        pk = b.getLong("pk"),
        uri = b.getString("uri") ?: "",
        name = b.getString("name") ?: "",
        mime = b.getString("mime"),
        isVideo = b.getBoolean("video"),
        size = b.getLong("size"),
        decode = b.getBoolean("decode"),
        decodeBroken = b.getBoolean("decodeBroken"),
        ioProbe = b.getBoolean("ioProbe"),
    )

    fun resultToBundle(r: FileResult) = Bundle().apply {
        putStringArray("reasons", r.reasons.map { it.name }.toTypedArray())
        putInt("w", r.width)
        putInt("h", r.height)
        putLong("dur", r.durationMs)
        putFloat("fps", r.fps)
        putLong("bitrate", r.bitrate)
        putInt("ioErrors", r.ioErrors)
        putFloat("mbps", r.readMbps)
        putBoolean("probed", r.probed)
        putBoolean("decoded", r.decoded)
        putInt("decodeMs", r.decodeMs)
    }

    fun bundleToResult(b: Bundle) = FileResult(
        reasons = (b.getStringArray("reasons") ?: emptyArray()).mapNotNull { n -> Reason.entries.firstOrNull { it.name == n } },
        width = b.getInt("w"),
        height = b.getInt("h"),
        durationMs = b.getLong("dur", -1),
        fps = b.getFloat("fps"),
        bitrate = b.getLong("bitrate"),
        ioErrors = b.getInt("ioErrors"),
        readMbps = b.getFloat("mbps"),
        probed = b.getBoolean("probed"),
        decoded = b.getBoolean("decoded"),
        decodeMs = b.getInt("decodeMs"),
    )
}
