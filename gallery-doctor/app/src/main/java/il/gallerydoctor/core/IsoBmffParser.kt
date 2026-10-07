package il.gallerydoctor.core

/**
 * ISO base media file parser for MP4 / MOV / 3GP and HEIC / HEIF.
 *
 * Walks the top-level box tree and checks: ftyp, moov (a missing moov means an interrupted
 * recording), box sizes vs the real file size (mdat truncation), and at least one track.
 * Only box headers are read, so even multi-GB videos cost a handful of small reads.
 */
object IsoBmffParser {
    private class Box(val type: String, val start: Long, val size: Long, val header: Int, val truncated: Boolean)

    fun parse(src: ByteSource, heif: Boolean): StructureReport {
        val fileSize = src.size
        val reasons = LinkedHashSet<Reason>()
        val top = ArrayList<Box>()
        var pos = 0L

        while (pos < fileSize) {
            if (fileSize - pos < 8) break // trailing padding
            val box = readBox(src, pos, fileSize)
            if (box == null) { reasons += Reason.ISO_BAD_BOX; break }
            top += box
            if (box.truncated) { reasons += Reason.ISO_TRUNCATED; break }
            pos += box.size
        }

        val ftyp = top.firstOrNull { it.type == "ftyp" }
        val moovBox = top.firstOrNull { it.type == "moov" }
        val moov = moovBox?.takeIf { !it.truncated }
        val meta = top.firstOrNull { it.type == "meta" && !it.truncated }

        if (ftyp == null && (heif || moov == null)) reasons += Reason.ISO_NO_FTYP
        if (ftyp != null && ftyp.size < 16) reasons += Reason.ISO_BAD_BOX

        var tracks = -1
        if (heif) {
            if (meta == null && ftyp != null) reasons += Reason.ISO_NO_META
        } else {
            if (moov == null) {
                reasons += Reason.ISO_NO_MOOV
            } else {
                val children = childBoxes(src, moov)
                tracks = children.count { it.type == "trak" }
                if (tracks < 1) reasons += Reason.ISO_NO_TRACKS
            }
        }
        return StructureReport(if (heif) Container.HEIF else Container.ISO_VIDEO, reasons.toList(), tracks = tracks)
    }

    private fun readBox(src: ByteSource, pos: Long, limit: Long): Box? {
        val sz32 = src.u32(pos) ?: return null
        val type = src.fourcc(pos + 4) ?: return null
        var header = 8
        var size = sz32
        when (sz32) {
            0L -> size = limit - pos
            1L -> {
                size = src.u64(pos + 8) ?: return null
                header = 16
            }
        }
        if (size < header || size < 0) return null
        val truncated = pos + size > limit
        return Box(type, pos, size, header, truncated)
    }

    private fun childBoxes(src: ByteSource, parent: Box): List<Box> {
        val out = ArrayList<Box>()
        var pos = parent.start + parent.header
        val end = parent.start + parent.size
        while (pos + 8 <= end) {
            val b = readBox(src, pos, end) ?: break
            out += b
            if (b.truncated) break
            pos += b.size
        }
        return out
    }
}
