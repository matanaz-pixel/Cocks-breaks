package il.gallerydoctor.core

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.CRC32

/** Generates valid and deliberately corrupted media files for the parser tests. */
object Fixtures {
    /**
     * A structurally valid baseline JPEG built by hand (JFIF, DQT, SOF0, DHT, SOS, entropy data, EOI).
     * No java.awt / ImageIO: Android unit tests compile against android.jar, which has neither.
     * The entropy bytes are pseudo-random and never contain 0xFF, so the marker chain stays valid.
     */
    fun jpeg(w: Int = 640, h: Int = 480, entropyBytes: Int = 120_000): ByteArray {
        val out = ByteArrayOutputStream()
        fun u8(v: Int) = out.write(v)
        fun u16(v: Int) { out.write(v shr 8); out.write(v and 0xFF) }
        u16(0xFFD8)
        u16(0xFFE0); u16(16); out.write("JFIF".toByteArray()); u8(0); u16(0x0101); u8(0); u16(1); u16(1); u8(0); u8(0)
        u16(0xFFDB); u16(67); u8(0); repeat(64) { u8(16) }
        u16(0xFFC0); u16(17); u8(8); u16(h); u16(w); u8(3)
        u8(1); u8(0x22); u8(0); u8(2); u8(0x11); u8(1); u8(3); u8(0x11); u8(1)
        u16(0xFFC4); u16(31); u8(0x00)
        val counts = intArrayOf(0, 1, 5, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0)
        counts.forEach { u8(it) }
        repeat(12) { u8(it) }
        u16(0xFFDA); u16(12); u8(3); u8(1); u8(0); u8(2); u8(0x11); u8(3); u8(0x11); u8(0); u8(63); u8(0)
        val rnd = java.util.Random(42)
        repeat(entropyBytes) { u8(1 + rnd.nextInt(254)) } // 1..254, never 0x00 or 0xFF
        u16(0xFFD9)
        return out.toByteArray()
    }

    /** A real, valid PNG built with java.util.zip only. */
    fun png(w: Int = 64, h: Int = 64): ByteArray {
        val raw = ByteArrayOutputStream()
        for (y in 0 until h) {
            raw.write(0) // filter: none
            for (x in 0 until w) { raw.write((x * 4) and 0xFF); raw.write((y * 4) and 0xFF); raw.write(((x + y) * 2) and 0xFF) }
        }
        val deflater = java.util.zip.Deflater()
        deflater.setInput(raw.toByteArray())
        deflater.finish()
        val z = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (!deflater.finished()) z.write(buf, 0, deflater.deflate(buf))
        deflater.end()

        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A))
        fun chunk(type: String, data: ByteArray) {
            out.write(ByteBuffer.allocate(4).putInt(data.size).array())
            out.write(type.toByteArray())
            out.write(data)
            out.write(ByteBuffer.allocate(4).putInt(crc(type, data).toInt()).array())
        }
        chunk("IHDR", ByteBuffer.allocate(13).putInt(w).putInt(h).put(8).put(2).put(0).put(0).put(0).array())
        chunk("IDAT", z.toByteArray())
        chunk("IEND", ByteArray(0))
        return out.toByteArray()
    }

    /** Flips one byte inside the first IDAT chunk, leaving its stored CRC untouched. */
    fun pngWithBadCrc(): ByteArray {
        val b = png().copyOf()
        var pos = 8
        while (pos + 8 < b.size) {
            val len = ByteBuffer.wrap(b, pos, 4).int
            val type = String(b, pos + 4, 4, Charsets.ISO_8859_1)
            if (type == "IDAT") {
                b[pos + 8 + len / 2] = (b[pos + 8 + len / 2].toInt() xor 0x5A).toByte()
                return b
            }
            pos += 12 + len
        }
        error("no IDAT")
    }

    fun webp(w: Int = 32, h: Int = 16): ByteArray {
        // VP8L lossless header followed by padding data: enough for a structural check.
        val bits = (w - 1) or ((h - 1) shl 14)
        val payload = ByteBuffer.allocate(5 + 20).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        payload.put(0x2F).putInt(bits)
        val chunkData = payload.array()
        val out = ByteBuffer.allocate(12 + 8 + chunkData.size).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        out.put("RIFF".toByteArray()).putInt(4 + 8 + chunkData.size).put("WEBP".toByteArray())
        out.put("VP8L".toByteArray()).putInt(chunkData.size).put(chunkData)
        return out.array()
    }

    private fun box(type: String, payload: ByteArray): ByteArray =
        ByteBuffer.allocate(8 + payload.size).putInt(8 + payload.size).put(type.toByteArray()).put(payload).array()

    fun ftyp(brand: String = "isom"): ByteArray =
        box("ftyp", brand.toByteArray() + ByteArray(4) + "isomiso2".toByteArray())

    fun moov(tracks: Int = 1): ByteArray {
        val mvhd = box("mvhd", ByteArray(100))
        var payload = mvhd
        repeat(tracks) { payload += box("trak", box("tkhd", ByteArray(84))) }
        return box("moov", payload)
    }

    fun mp4(mdatBytes: Int = 4096, tracks: Int = 1, moovAtEnd: Boolean = true): ByteArray {
        val mdat = box("mdat", ByteArray(mdatBytes) { (it % 251 + 1).toByte() })
        return if (moovAtEnd) ftyp() + mdat + moov(tracks) else ftyp() + moov(tracks) + mdat
    }

    fun mp4MissingMoov(): ByteArray = ftyp() + box("mdat", ByteArray(8192) { 7 })

    fun heic(): ByteArray {
        val meta = box("meta", ByteArray(4) + box("hdlr", ByteArray(24)) + box("iloc", ByteArray(16)))
        return ftyp("heic") + meta + box("mdat", ByteArray(2048) { 9 })
    }

    fun crc(type: String, data: ByteArray): Long = CRC32().also { it.update(type.toByteArray()); it.update(data) }.value
}
