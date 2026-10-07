package il.gallerydoctor.core

import java.util.zip.CRC32

/** PNG: signature, IHDR, every chunk CRC, IEND present, truncation. */
object PngParser {
    fun parse(src: ByteSource): StructureReport {
        val size = src.size
        val reasons = LinkedHashSet<Reason>()
        var width = 0
        var height = 0
        var pos = 8L
        var first = true
        var sawIend = false
        var badCrc = false
        val buf = ByteArray(65536)

        while (true) {
            if (pos + 8 > size) break
            val len = src.u32(pos) ?: break
            val type = src.fourcc(pos + 4) ?: break
            if (len > Int.MAX_VALUE || pos + 12 + len > size) {
                reasons += Reason.PNG_TRUNCATED
                break
            }
            if (first) {
                first = false
                if (type != "IHDR" || len != 13L) { reasons += Reason.PNG_BAD_IHDR; break }
                val w = src.u32(pos + 8) ?: 0
                val h = src.u32(pos + 12) ?: 0
                if (w <= 0 || h <= 0 || w > Int.MAX_VALUE || h > Int.MAX_VALUE) {
                    reasons += Reason.PNG_BAD_IHDR
                    break
                }
                width = w.toInt()
                height = h.toInt()
            }
            val stored = src.u32(pos + 8 + len) ?: break
            val crc = CRC32()
            crc.update(type.toByteArray(Charsets.ISO_8859_1))
            var off = pos + 8
            var remaining = len
            while (remaining > 0) {
                val n = src.read(off, buf, 0, minOf(remaining, buf.size.toLong()).toInt())
                if (n <= 0) break
                crc.update(buf, 0, n)
                off += n
                remaining -= n
            }
            if (remaining > 0) { reasons += Reason.PNG_TRUNCATED; break }
            if (crc.value != stored) badCrc = true
            pos += 12 + len
            if (type == "IEND") { sawIend = true; break }
        }
        if (first && !reasons.contains(Reason.PNG_TRUNCATED)) reasons += Reason.PNG_BAD_IHDR
        if (!sawIend && Reason.PNG_TRUNCATED !in reasons && Reason.PNG_BAD_IHDR !in reasons) reasons += Reason.PNG_NO_IEND
        if (badCrc) reasons += Reason.PNG_BAD_CRC
        return StructureReport(Container.PNG, reasons.toList(), width, height)
    }
}

/** WebP: RIFF header, RIFF size vs file size, chunk walk, canvas dimensions. */
object WebpParser {
    fun parse(src: ByteSource): StructureReport {
        val size = src.size
        val reasons = LinkedHashSet<Reason>()
        var width = 0
        var height = 0

        val riffSize = if (size >= 12) src.u32(4)?.let { Integer.reverseBytes(it.toInt()).toLong() and 0xFFFFFFFFL } else null
        if (riffSize == null) return StructureReport(Container.WEBP, listOf(Reason.WEBP_BAD_HEADER))
        val riffEnd = riffSize + 8
        if (riffEnd > size) reasons += Reason.WEBP_TRUNCATED
        else if (size - riffEnd > 1) reasons += Reason.WEBP_SIZE_MISMATCH

        val limit = minOf(riffEnd, size)
        var pos = 12L
        var firstChunk = true
        while (pos + 8 <= limit) {
            val fcc = src.fourcc(pos) ?: break
            val csize = src.u32(pos + 4)?.let { Integer.reverseBytes(it.toInt()).toLong() and 0xFFFFFFFFL } ?: break
            if (firstChunk) {
                firstChunk = false
                if (fcc != "VP8 " && fcc != "VP8L" && fcc != "VP8X") { reasons += Reason.WEBP_BAD_HEADER; break }
            }
            if (width == 0) {
                val dims = dimensions(src, fcc, pos + 8, csize)
                if (dims != null) { width = dims.first; height = dims.second }
            }
            val end = pos + 8 + csize + (csize and 1)
            if (end > limit + 1) { reasons += Reason.WEBP_TRUNCATED; break }
            pos = end
        }
        if (firstChunk && Reason.WEBP_TRUNCATED !in reasons) reasons += Reason.WEBP_BAD_HEADER
        return StructureReport(Container.WEBP, reasons.toList(), width, height)
    }

    private fun le24(b: ByteArray, i: Int) =
        (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8) or ((b[i + 2].toInt() and 0xFF) shl 16)

    private fun dimensions(src: ByteSource, fcc: String, dataPos: Long, csize: Long): Pair<Int, Int>? = when (fcc) {
        "VP8X" -> src.readFully(dataPos, 10)?.let { Pair(le24(it, 4) + 1, le24(it, 7) + 1) }
        "VP8L" -> src.readFully(dataPos, 5)?.takeIf { it[0] == 0x2F.toByte() }?.let {
            val bits = (it[1].toInt() and 0xFF) or ((it[2].toInt() and 0xFF) shl 8) or
                ((it[3].toInt() and 0xFF) shl 16) or ((it[4].toInt() and 0xFF) shl 24)
            Pair((bits and 0x3FFF) + 1, ((bits ushr 14) and 0x3FFF) + 1)
        }
        "VP8 " -> if (csize >= 10) src.readFully(dataPos, 10)?.takeIf {
            it[3] == 0x9D.toByte() && it[4] == 0x01.toByte() && it[5] == 0x2A.toByte()
        }?.let {
            Pair(((it[6].toInt() and 0xFF) or ((it[7].toInt() and 0xFF) shl 8)) and 0x3FFF,
                ((it[8].toInt() and 0xFF) or ((it[9].toInt() and 0xFF) shl 8)) and 0x3FFF)
        } else null
        else -> null
    }
}
