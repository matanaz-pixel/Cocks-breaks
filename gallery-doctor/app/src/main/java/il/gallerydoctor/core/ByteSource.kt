package il.gallerydoctor.core

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile

/** Random-access, read-only view of a file. All parsers work on this, so they are unit-testable on the JVM. */
interface ByteSource {
    val size: Long

    /** Reads up to [len] bytes at [pos]. Returns the byte count, or <= 0 at EOF. */
    fun read(pos: Long, buf: ByteArray, off: Int, len: Int): Int
}

fun ByteSource.readFully(pos: Long, len: Int): ByteArray? {
    if (pos < 0 || len < 0 || pos + len > size) return null
    val b = ByteArray(len)
    var got = 0
    while (got < len) {
        val n = read(pos + got, b, got, len - got)
        if (n <= 0) return null
        got += n
    }
    return b
}

fun ByteSource.u8(pos: Long): Int? {
    val b = readFully(pos, 1) ?: return null
    return b[0].toInt() and 0xFF
}

fun ByteSource.u16(pos: Long): Int? {
    val b = readFully(pos, 2) ?: return null
    return ((b[0].toInt() and 0xFF) shl 8) or (b[1].toInt() and 0xFF)
}

fun ByteSource.u32(pos: Long): Long? {
    val b = readFully(pos, 4) ?: return null
    return ((b[0].toLong() and 0xFF) shl 24) or ((b[1].toLong() and 0xFF) shl 16) or
        ((b[2].toLong() and 0xFF) shl 8) or (b[3].toLong() and 0xFF)
}

fun ByteSource.u64(pos: Long): Long? {
    val b = readFully(pos, 8) ?: return null
    var v = 0L
    for (x in b) v = (v shl 8) or (x.toLong() and 0xFF)
    return v
}

fun ByteSource.fourcc(pos: Long): String? {
    val b = readFully(pos, 4) ?: return null
    return String(b, Charsets.ISO_8859_1)
}

class ByteArraySource(private val data: ByteArray) : ByteSource {
    override val size: Long get() = data.size.toLong()
    override fun read(pos: Long, buf: ByteArray, off: Int, len: Int): Int {
        if (pos >= data.size) return -1
        val n = minOf(len.toLong(), data.size - pos).toInt()
        System.arraycopy(data, pos.toInt(), buf, off, n)
        return n
    }
}

class RandomAccessFileSource(file: File) : ByteSource, Closeable {
    private val raf = RandomAccessFile(file, "r")
    override val size: Long = raf.length()
    override fun read(pos: Long, buf: ByteArray, off: Int, len: Int): Int {
        raf.seek(pos)
        return raf.read(buf, off, len)
    }

    override fun close() = raf.close()
}

/** True when every byte in [pos, pos+len) is zero. */
fun ByteSource.isAllZero(pos: Long, len: Int): Boolean {
    val b = readFully(pos, len) ?: return false
    for (x in b) if (x.toInt() != 0) return false
    return true
}
