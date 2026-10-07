package il.gallerydoctor.core

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.CRC32
import javax.imageio.ImageIO

/** Generates valid and deliberately corrupted media files for the parser tests. */
object Fixtures {
    fun jpeg(w: Int = 640, h: Int = 480): ByteArray {
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val rnd = java.util.Random(42)
        for (y in 0 until h) for (x in 0 until w) img.setRGB(x, y, rnd.nextInt(0xFFFFFF))
        return ByteArrayOutputStream().also { ImageIO.write(img, "jpg", it) }.toByteArray()
    }

    fun png(w: Int = 64, h: Int = 64): ByteArray {
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until h) for (x in 0 until w) img.setRGB(x, y, (x * 4) shl 16 or (y * 4))
        return ByteArrayOutputStream().also { ImageIO.write(img, "png", it) }.toByteArray()
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
