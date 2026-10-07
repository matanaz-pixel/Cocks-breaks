package il.gallerydoctor.core

enum class Container { JPEG, PNG, GIF, WEBP, BMP, HEIF, ISO_VIDEO, MATROSKA, AVI, UNKNOWN }

/** What a structural parser found. [tracks] is -1 when not applicable. */
data class StructureReport(
    val container: Container,
    val reasons: List<Reason>,
    val width: Int = 0,
    val height: Int = 0,
    val tracks: Int = -1,
)

object Magic {
    private val heifBrands = setOf("heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1", "avif", "avis")
    private val legacyMovTop = setOf("moov", "mdat", "wide", "free", "skip", "pnot")

    fun sniff(head: ByteArray): Container {
        val n = head.size
        fun b(i: Int) = if (i < n) head[i].toInt() and 0xFF else -1
        fun str(from: Int, len: Int): String? =
            if (from + len <= n) String(head, from, len, Charsets.ISO_8859_1) else null
        if (b(0) == 0xFF && b(1) == 0xD8) return Container.JPEG
        if (n >= 8 && b(0) == 0x89 && str(1, 3) == "PNG" && b(4) == 0x0D && b(5) == 0x0A && b(6) == 0x1A && b(7) == 0x0A)
            return Container.PNG
        if (str(0, 6) == "GIF87a" || str(0, 6) == "GIF89a") return Container.GIF
        if (str(0, 4) == "RIFF") {
            when (str(8, 4)) {
                "WEBP" -> return Container.WEBP
                "AVI " -> return Container.AVI
            }
        }
        if (b(0) == 'B'.code && b(1) == 'M'.code) return Container.BMP
        if (b(0) == 0x1A && b(1) == 0x45 && b(2) == 0xDF && b(3) == 0xA3) return Container.MATROSKA
        val boxType = str(4, 4)
        if (boxType == "ftyp") {
            val brand = str(8, 4)?.lowercase()
            return if (brand != null && brand in heifBrands) Container.HEIF else Container.ISO_VIDEO
        }
        if (boxType != null && boxType in legacyMovTop) return Container.ISO_VIDEO
        return Container.UNKNOWN
    }

    fun fromExtension(ext: String): Container = when (ext.lowercase().trimStart('.')) {
        "jpg", "jpeg", "jpe", "jfif" -> Container.JPEG
        "png" -> Container.PNG
        "gif" -> Container.GIF
        "webp" -> Container.WEBP
        "bmp" -> Container.BMP
        "heic", "heif", "hif", "avif" -> Container.HEIF
        "mp4", "m4v", "mov", "3gp", "3g2", "3gpp", "qt" -> Container.ISO_VIDEO
        "mkv", "webm" -> Container.MATROSKA
        "avi" -> Container.AVI
        else -> Container.UNKNOWN
    }

    fun fromMime(mime: String?): Container = when (mime?.lowercase()) {
        "image/jpeg", "image/jpg" -> Container.JPEG
        "image/png" -> Container.PNG
        "image/gif" -> Container.GIF
        "image/webp" -> Container.WEBP
        "image/bmp", "image/x-ms-bmp" -> Container.BMP
        "image/heic", "image/heif", "image/avif" -> Container.HEIF
        "video/mp4", "video/quicktime", "video/3gpp", "video/3gpp2", "video/x-m4v" -> Container.ISO_VIDEO
        "video/x-matroska", "video/webm" -> Container.MATROSKA
        "video/x-msvideo", "video/avi" -> Container.AVI
        else -> Container.UNKNOWN
    }

    fun extensionOf(name: String): String {
        val i = name.lastIndexOf('.')
        return if (i < 0 || i == name.length - 1) "" else name.substring(i + 1).lowercase()
    }

    fun isVideoContainer(c: Container) = c == Container.ISO_VIDEO || c == Container.MATROSKA || c == Container.AVI
}

/**
 * Entry point of the L1 (header) and L2 (structure) checks. Pure Kotlin, no Android classes,
 * so it runs inside the isolated worker process and in JVM unit tests.
 */
object StructureAnalyzer {
    fun analyze(src: ByteSource, extension: String, mime: String?): StructureReport {
        if (src.size == 0L) return StructureReport(Container.UNKNOWN, listOf(Reason.ZERO_BYTES))

        val head = src.readFully(0, minOf(src.size, 16L).toInt()) ?: ByteArray(0)
        val sniffed = Magic.sniff(head)
        val byExt = Magic.fromExtension(extension)
        val expected = if (byExt != Container.UNKNOWN) byExt else Magic.fromMime(mime)

        val reasons = LinkedHashSet<Reason>()
        if (sniffed == Container.UNKNOWN) {
            if (expected != Container.UNKNOWN) reasons += Reason.BAD_MAGIC
            return StructureReport(expected, reasons.toList())
        }
        if (expected != Container.UNKNOWN && expected != sniffed) reasons += Reason.MAGIC_MISMATCH

        val inner = when (sniffed) {
            Container.JPEG -> JpegParser.parse(src)
            Container.PNG -> PngParser.parse(src)
            Container.WEBP -> WebpParser.parse(src)
            Container.HEIF -> IsoBmffParser.parse(src, heif = true)
            Container.ISO_VIDEO -> IsoBmffParser.parse(src, heif = false)
            else -> StructureReport(sniffed, emptyList())
        }
        reasons += inner.reasons
        return inner.copy(reasons = reasons.toList())
    }
}
