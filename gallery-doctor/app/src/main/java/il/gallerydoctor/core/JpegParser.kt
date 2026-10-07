package il.gallerydoctor.core

/**
 * JPEG structure check: SOI, valid marker chain, SOF dimensions, EOI, truncation.
 *
 * Fast path: walk the marker headers up to the first SOS, then look for EOI in the tail of the
 * file. Only if the tail has no EOI do we scan the entropy-coded data, which is the expensive path.
 */
object JpegParser {
    private const val TAIL_WINDOW = 65536
    private const val ZERO_RUN = 2048

    fun parse(src: ByteSource): StructureReport {
        val size = src.size
        val reasons = LinkedHashSet<Reason>()
        var width = 0
        var height = 0

        val soi = src.readFully(0, 2)
        if (soi == null || soi[0] != 0xFF.toByte() || soi[1] != 0xD8.toByte())
            return StructureReport(Container.JPEG, listOf(Reason.JPEG_NO_SOI))

        var pos = 2L
        var sawSof = false
        var sawSos = false
        var sawEoi = false
        var lastScanStart = 0L

        loop@ while (true) {
            val first = src.u8(pos)
            if (first == null) { reasons += Reason.JPEG_NO_EOI; break }
            if (first != 0xFF) { reasons += Reason.JPEG_BAD_MARKER; break }

            var p = pos + 1
            var m: Int
            do {
                m = src.u8(p) ?: -1
                p++
            } while (m == 0xFF)
            if (m == -1) { reasons += Reason.JPEG_NO_EOI; break }
            pos = p

            when {
                m == 0xD9 -> { sawEoi = true; break@loop }
                m == 0x00 -> { reasons += Reason.JPEG_BAD_MARKER; break@loop }
                m == 0x01 || m == 0xD8 || m in 0xD0..0xD7 -> continue@loop
            }

            val len = src.u16(pos)
            if (len == null) { reasons += Reason.JPEG_TRUNCATED_SEGMENT; break }
            if (len < 2) { reasons += Reason.JPEG_BAD_MARKER; break }
            if (pos + len > size) { reasons += Reason.JPEG_TRUNCATED_SEGMENT; break }

            if (isSof(m)) {
                val hdr = src.readFully(pos + 2, 6)
                if (hdr != null) {
                    height = ((hdr[1].toInt() and 0xFF) shl 8) or (hdr[2].toInt() and 0xFF)
                    width = ((hdr[3].toInt() and 0xFF) shl 8) or (hdr[4].toInt() and 0xFF)
                    sawSof = true
                }
            }
            pos += len

            if (m == 0xDA) {
                if (!sawSof) { reasons += Reason.JPEG_BAD_MARKER; break }
                sawSos = true
                if (lastScanStart == 0L) lastScanStart = pos
                if (tailHasEoi(src, lastScanStart)) { sawEoi = true; break@loop }
                val next = findNextMarker(src, pos)
                if (next < 0) { reasons += Reason.JPEG_NO_EOI; break@loop }
                pos = next
            }
        }

        if (!sawSos && Reason.JPEG_BAD_MARKER !in reasons && Reason.JPEG_TRUNCATED_SEGMENT !in reasons &&
            Reason.JPEG_NO_EOI !in reasons
        ) reasons += Reason.JPEG_NO_SCAN
        if (!sawSos && sawEoi) reasons += Reason.JPEG_NO_SCAN
        if (sawSos && !sawEoi && Reason.JPEG_NO_EOI !in reasons && Reason.JPEG_TRUNCATED_SEGMENT !in reasons &&
            Reason.JPEG_BAD_MARKER !in reasons
        ) reasons += Reason.JPEG_NO_EOI

        if (sawSos && size > 2L * ZERO_RUN) {
            val tailZero = src.isAllZero(size - ZERO_RUN, ZERO_RUN)
            val beforeEoi = if (sawEoi) eoiPosition(src, lastScanStart) else -1L
            val zerosBeforeEoi = beforeEoi >= ZERO_RUN && beforeEoi - ZERO_RUN >= lastScanStart &&
                src.isAllZero(beforeEoi - ZERO_RUN, ZERO_RUN)
            if ((!sawEoi && tailZero) || zerosBeforeEoi) reasons += Reason.ZERO_TAIL
        }

        return StructureReport(Container.JPEG, reasons.toList(), width, height)
    }

    private fun isSof(m: Int) = m in 0xC0..0xCF && m != 0xC4 && m != 0xC8 && m != 0xCC

    /** Position of the last FF D9 inside the tail window (not before [minPos]), or -1. */
    private fun eoiPosition(src: ByteSource, minPos: Long): Long {
        val size = src.size
        val start = maxOf(minPos, size - TAIL_WINDOW)
        val len = (size - start).toInt()
        if (len < 2) return -1
        val b = src.readFully(start, len) ?: return -1
        for (i in len - 2 downTo 0) {
            if (b[i] == 0xFF.toByte() && b[i + 1] == 0xD9.toByte()) return start + i
        }
        return -1
    }

    private fun tailHasEoi(src: ByteSource, minPos: Long) = eoiPosition(src, minPos) >= 0

    /**
     * Scans entropy-coded data from [from] and returns the offset of the next real marker
     * (an FF that is not stuffed with 00 and not an RSTn), or -1 if the file ends first.
     */
    private fun findNextMarker(src: ByteSource, from: Long): Long {
        val buf = ByteArray(65536)
        var pos = from
        val size = src.size
        while (pos < size) {
            val n = src.read(pos, buf, 0, buf.size)
            if (n <= 0) return -1
            var i = 0
            while (i < n) {
                if (buf[i] == 0xFF.toByte()) {
                    val next = if (i + 1 < n) buf[i + 1].toInt() and 0xFF else (src.u8(pos + i + 1) ?: return -1)
                    if (next == 0x00 || next in 0xD0..0xD7 || next == 0xFF) { i++; continue }
                    return pos + i
                }
                i++
            }
            pos += n
        }
        return -1
    }
}
