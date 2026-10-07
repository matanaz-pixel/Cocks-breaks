package il.gallerydoctor.scan

import android.content.Context
import android.net.Uri
import il.gallerydoctor.core.ByteSource
import il.gallerydoctor.data.StateDb
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest

/** A [ByteSource] over a ParcelFileDescriptor's channel. The caller owns closing via [close]. */
class ChannelSource(private val pfd: android.os.ParcelFileDescriptor) : ByteSource, java.io.Closeable {
    private val stream = FileInputStream(pfd.fileDescriptor)
    private val channel: FileChannel = stream.channel
    override val size: Long = channel.size()

    override fun read(pos: Long, buf: ByteArray, off: Int, len: Int): Int =
        channel.read(ByteBuffer.wrap(buf, off, len), pos)

    fun channel(): FileChannel = channel

    override fun close() {
        try { stream.close() } catch (_: Exception) { }
        try { pfd.close() } catch (_: Exception) { }
    }

    companion object {
        fun open(ctx: Context, uri: Uri): ChannelSource {
            val pfd = ctx.contentResolver.openFileDescriptor(uri, "r") ?: throw java.io.FileNotFoundException("null descriptor: $uri")
            return try { ChannelSource(pfd) } catch (e: Throwable) { pfd.close(); throw e }
        }
    }
}

data class IndexDrift(val changed: Int, val vanished: Int, val appeared: Int, val byOwner: Map<String, Int> = emptyMap())

object IndexStability {
    const val INTERVAL_MS = 60_000L

    /** Compares the first enumeration (stored in [db]) with a fresh one. Called 60 seconds after the first. */
    fun measure(ctx: Context, db: StateDb): IndexDrift {
        val before = db.indexSnapshot()
        val after = MediaEnumerator.snapshot(ctx)
        var changed = 0
        var vanished = 0
        var appeared = 0
        val owners = HashMap<String, Int>()
        fun blame(owner: String?) { if (owner != null) owners[owner] = (owners[owner] ?: 0) + 1 }
        for ((k, v) in before) {
            val now = after[k]
            if (now == null) { vanished++; blame(v.owner) }
            else if (now.sig != v.sig) { changed++; blame(now.owner ?: v.owner) }
        }
        for ((k, v) in after) if (k !in before) { appeared++; blame(v.owner) }
        return IndexDrift(changed, vanished, appeared, owners)
    }
}

/**
 * Duplicate finder. Group by size, then by a partial hash (first 64 KB + last 64 KB + size),
 * then a full SHA-256 only for the files that are still candidates. Reads only; one thread.
 */
object DuplicateFinder {
    private const val PART = 64 * 1024

    fun run(ctx: Context, db: StateDb, isActive: () -> Boolean, onProgress: (done: Int, total: Int) -> Unit) {
        db.clearDups()
        val sizes = db.duplicateSizeCandidates()
        var group = 0
        sizes.forEachIndexed { index, size ->
            if (!isActive()) return
            onProgress(index, sizes.size)
            val pks = db.pksWithSize(size)
            val byPartial = HashMap<String, MutableList<Long>>()
            for (pk in pks) {
                if (!isActive()) return
                val row = db.getRow(pk) ?: continue
                val h = try { partialHash(ctx, Uri.parse(row.uri), size) } catch (_: Exception) { null } ?: continue
                db.setPartialHash(pk, h)
                byPartial.getOrPut(h) { ArrayList() } += pk
            }
            for ((_, candidates) in byPartial) {
                if (candidates.size < 2) continue
                val byFull = HashMap<String, MutableList<Long>>()
                for (pk in candidates) {
                    if (!isActive()) return
                    val row = db.getRow(pk) ?: continue
                    val h = try { fullHash(ctx, Uri.parse(row.uri)) } catch (_: Exception) { null } ?: continue
                    byFull.getOrPut(h) { ArrayList() } += pk
                }
                for ((_, same) in byFull) if (same.size > 1) db.addDupGroup(group++, same)
            }
        }
        onProgress(sizes.size, sizes.size)
    }

    private fun partialHash(ctx: Context, uri: Uri, size: Long): String? {
        ChannelSource.open(ctx, uri).use { src ->
            val md = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(PART)
            fun feed(pos: Long, len: Int) {
                var got = 0
                while (got < len) {
                    val n = src.read(pos + got, buf, got, len - got)
                    if (n <= 0) break
                    got += n
                }
                md.update(buf, 0, got)
            }
            feed(0, minOf(PART.toLong(), size).toInt())
            if (size > PART) feed(maxOf(PART.toLong(), size - PART), minOf(PART.toLong(), size - PART).toInt())
            md.update(size.toString().toByteArray())
            return md.digest().joinToString("") { "%02x".format(it) }
        }
    }

    private fun fullHash(ctx: Context, uri: Uri): String? {
        val md = MessageDigest.getInstance("SHA-256")
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            val buf = ByteArray(256 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        } ?: return null
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
