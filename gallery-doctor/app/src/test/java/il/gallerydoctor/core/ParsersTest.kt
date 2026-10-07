package il.gallerydoctor.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsersTest {
    private fun analyze(bytes: ByteArray, ext: String, mime: String? = null) =
        StructureAnalyzer.analyze(ByteArraySource(bytes), ext, mime)

    private fun status(r: StructureReport) = Classifier.statusOf(r.reasons)

    // ---------- JPEG ----------

    @Test fun validJpegIsClean() {
        val r = analyze(Fixtures.jpeg(640, 480), "jpg", "image/jpeg")
        assertTrue(r.reasons.toString(), r.reasons.isEmpty())
        assertEquals(640, r.width)
        assertEquals(480, r.height)
        assertEquals(Status.OK, status(r))
    }

    @Test fun truncatedJpegIsDetected() {
        val full = Fixtures.jpeg()
        for (keep in listOf(full.size / 2, full.size - 100, full.size - 2, full.size / 10)) {
            val r = analyze(full.copyOf(keep), "jpg", "image/jpeg")
            assertTrue("keep=$keep got ${r.reasons}", Reason.JPEG_NO_EOI in r.reasons || Reason.JPEG_TRUNCATED_SEGMENT in r.reasons)
            assertEquals(Status.SUSPECT, status(r))
        }
    }

    @Test fun jpegWithTrailerAfterEoiIsStillOk() {
        // Motion photos append a whole MP4 after the JPEG's EOI.
        val r = analyze(Fixtures.jpeg() + ByteArray(200_000) { 1 }, "jpg")
        assertTrue(r.reasons.toString(), r.reasons.isEmpty())
    }

    @Test fun jpegCutInsideHeaderSegment() {
        val r = analyze(Fixtures.jpeg().copyOf(60), "jpg")
        assertTrue(r.reasons.toString(), r.reasons.isNotEmpty())
        assertTrue(status(r) != Status.OK)
    }

    @Test fun jpegZeroFilledTailIsFlagged() {
        val full = Fixtures.jpeg()
        val damaged = full.copyOf()
        for (i in full.size - 6000 until full.size - 2) damaged[i] = 0
        val r = analyze(damaged, "jpg")
        assertTrue(r.reasons.toString(), Reason.ZERO_TAIL in r.reasons)
    }

    @Test fun jpegGarbageInsteadOfMarker() {
        val b = Fixtures.jpeg().copyOf()
        b[2] = 0x12
        val r = analyze(b, "jpg")
        assertTrue(Reason.JPEG_BAD_MARKER in r.reasons)
        assertEquals(Status.BROKEN, status(r))
    }

    // ---------- PNG ----------

    @Test fun validPngIsClean() {
        val r = analyze(Fixtures.png(64, 48), "png", "image/png")
        assertTrue(r.reasons.toString(), r.reasons.isEmpty())
        assertEquals(64, r.width)
        assertEquals(48, r.height)
    }

    @Test fun pngBadCrcIsDetected() {
        val r = analyze(Fixtures.pngWithBadCrc(), "png")
        assertTrue(Reason.PNG_BAD_CRC in r.reasons)
        assertEquals(Status.SUSPECT, status(r))
    }

    @Test fun pngMissingIendIsDetected() {
        val p = Fixtures.png()
        val r = analyze(p.copyOf(p.size - 12), "png")
        assertTrue(r.reasons.toString(), Reason.PNG_NO_IEND in r.reasons)
    }

    @Test fun pngTruncatedMidChunk() {
        val p = Fixtures.png()
        val r = analyze(p.copyOf(p.size / 2), "png")
        assertTrue(r.reasons.toString(), Reason.PNG_TRUNCATED in r.reasons)
    }

    // ---------- WebP ----------

    @Test fun validWebpIsClean() {
        val r = analyze(Fixtures.webp(32, 16), "webp", "image/webp")
        assertTrue(r.reasons.toString(), r.reasons.isEmpty())
        assertEquals(32, r.width)
        assertEquals(16, r.height)
    }

    @Test fun truncatedWebpRiffSizeMismatch() {
        val w = Fixtures.webp()
        val r = analyze(w.copyOf(w.size - 10), "webp")
        assertTrue(Reason.WEBP_TRUNCATED in r.reasons)
    }

    @Test fun webpWithExtraBytesIsSizeMismatch() {
        val r = analyze(Fixtures.webp() + ByteArray(100), "webp")
        assertTrue(Reason.WEBP_SIZE_MISMATCH in r.reasons)
    }

    // ---------- MP4 / MOV / HEIC ----------

    @Test fun validMp4MoovAtEnd() {
        val r = analyze(Fixtures.mp4(moovAtEnd = true), "mp4", "video/mp4")
        assertTrue(r.reasons.toString(), r.reasons.isEmpty())
        assertEquals(1, r.tracks)
    }

    @Test fun validMp4MoovFirst() {
        val r = analyze(Fixtures.mp4(moovAtEnd = false, tracks = 2), "mp4")
        assertTrue(r.reasons.isEmpty())
        assertEquals(2, r.tracks)
    }

    @Test fun mp4MissingMoovMeansInterruptedRecording() {
        val r = analyze(Fixtures.mp4MissingMoov(), "mp4", "video/mp4")
        assertTrue(Reason.ISO_NO_MOOV in r.reasons)
        assertEquals(Status.BROKEN, status(r))
    }

    @Test fun mp4TruncatedMdatLosesMoov() {
        val m = Fixtures.mp4(mdatBytes = 50_000)
        val r = analyze(m.copyOf(m.size / 2), "mp4")
        assertTrue(r.reasons.toString(), Reason.ISO_TRUNCATED in r.reasons)
        assertTrue(Reason.ISO_NO_MOOV in r.reasons)
    }

    @Test fun mp4TruncatedInsideMoov() {
        val m = Fixtures.mp4()
        val r = analyze(m.copyOf(m.size - 20), "mp4")
        assertTrue(Reason.ISO_TRUNCATED in r.reasons)
        assertTrue(Reason.ISO_NO_MOOV in r.reasons)
    }

    @Test fun mp4WithoutTracks() {
        val r = analyze(Fixtures.mp4(tracks = 0), "mp4")
        assertTrue(Reason.ISO_NO_TRACKS in r.reasons)
    }

    @Test fun heicValidAndTruncated() {
        val h = Fixtures.heic()
        assertTrue(analyze(h, "heic").reasons.isEmpty())
        val cut = analyze(h.copyOf(h.size - 500), "heic")
        assertTrue(Reason.ISO_TRUNCATED in cut.reasons)
    }

    // ---------- header level ----------

    @Test fun zeroBytesFile() {
        for (ext in listOf("jpg", "png", "mp4", "webp", "heic")) {
            val r = analyze(ByteArray(0), ext)
            assertEquals(listOf(Reason.ZERO_BYTES), r.reasons)
            assertEquals(Status.BROKEN, status(r))
        }
    }

    @Test fun wrongExtensionIsFlagged() {
        val r = analyze(Fixtures.png(), "jpg", "image/jpeg")
        assertTrue(Reason.MAGIC_MISMATCH in r.reasons)
        assertEquals(Status.SUSPECT, status(r))
        // the real content is still validated as a PNG
        assertEquals(Container.PNG, r.container)
    }

    @Test fun textFileNamedJpgIsBadMagic() {
        val r = analyze("this is not an image at all".toByteArray(), "jpg")
        assertEquals(listOf(Reason.BAD_MAGIC), r.reasons)
        assertEquals(Status.BROKEN, status(r))
    }

    @Test fun mp4NamedMovIsFine_butMp4NamedJpgIsNot() {
        assertTrue(analyze(Fixtures.mp4(), "mov").reasons.isEmpty())
        assertTrue(Reason.MAGIC_MISMATCH in analyze(Fixtures.mp4(), "jpg").reasons)
    }

    @Test fun unknownExtensionAndContentIsIgnored() {
        val r = analyze(ByteArray(100) { 3 }, "dat")
        assertTrue(r.reasons.isEmpty())
    }

    @Test fun reasonsRoundTripThroughCsv() {
        val list = listOf(Reason.JPEG_NO_EOI, Reason.ZERO_TAIL, Reason.HANG_TIMEOUT)
        assertEquals(list, Reason.parseList(Reason.toCsv(list)))
        assertFalse(Reason.parseList("").isNotEmpty())
    }
}
