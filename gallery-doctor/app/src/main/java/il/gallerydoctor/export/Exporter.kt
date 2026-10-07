package il.gallerydoctor.export

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.widget.Toast
import androidx.core.content.FileProvider
import il.gallerydoctor.core.Block
import il.gallerydoctor.core.CsvWriter
import il.gallerydoctor.core.ProblemRow
import il.gallerydoctor.core.ReportData
import il.gallerydoctor.core.ReportDocument
import il.gallerydoctor.data.StateDb
import java.io.File

/**
 * Report export. Everything is written to the app's own cache directory (never to the user's
 * media folders) and shared through FileProvider.
 */
object Exporter {
    private fun exportDir(ctx: Context) = File(ctx.cacheDir, "exports").apply { mkdirs() }

    fun shareText(ctx: Context, data: ReportData) {
        val text = ReportDocument.toPlainText(ReportDocument.build(data)).take(200_000)
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            .putExtra(Intent.EXTRA_SUBJECT, "דוח בדיקת הגלריה")
        launch(ctx, Intent.createChooser(send, null))
    }

    fun sharePdf(ctx: Context, data: ReportData) {
        val file = File(exportDir(ctx), "gallery-doctor-report.pdf")
        PdfExporter.write(ReportDocument.build(data), file)
        shareFile(ctx, file, "application/pdf")
    }

    fun shareCsv(ctx: Context, db: StateDb) {
        val file = File(exportDir(ctx), "gallery-doctor-problems.csv")
        file.bufferedWriter(Charsets.UTF_8).use { w ->
            w.write(CsvWriter.BOM)
            w.write(CsvWriter.header()); w.write("\r\n")
            db.forEachProblem { r -> w.write(CsvWriter.row(r)); w.write("\r\n") }
        }
        shareFile(ctx, file, "text/csv")
    }

    private fun shareFile(ctx: Context, file: File, mime: String) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        launch(ctx, Intent.createChooser(send, null).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    /** Opens the file in the system viewer so it can be tested by hand. Read-only grant. */
    fun openInViewer(ctx: Context, row: ProblemRow) {
        val type = row.mime ?: if (row.isVideo) "video/*" else "image/*"
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(row.uri), type)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        launch(ctx, view)
    }

    private fun launch(ctx: Context, intent: Intent) {
        try {
            ctx.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(ctx, "לא נמצאה אפליקציה שיכולה לפתוח את זה", Toast.LENGTH_LONG).show()
        } catch (_: SecurityException) {
            Toast.makeText(ctx, "אין הרשאה לפתוח את הקובץ", Toast.LENGTH_LONG).show()
        }
    }
}

/** Hebrew RTL PDF via StaticLayout (PdfDocument has no text shaping of its own). */
object PdfExporter {
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40

    fun write(blocks: List<Block>, file: File) {
        val doc = PdfDocument()
        val width = PAGE_W - 2 * MARGIN
        fun paint(size: Float, bold: Boolean) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            color = 0xFF111111.toInt()
        }
        val title = paint(22f, true)
        val heading = paint(15f, true)
        val body = paint(11f, false)

        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
        var y = MARGIN.toFloat()

        fun newPage() {
            doc.finishPage(page)
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
            y = MARGIN.toFloat()
        }

        fun draw(text: String, p: TextPaint, spaceAfter: Float) {
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, p, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setTextDirection(TextDirectionHeuristics.RTL)
                .setLineSpacing(3f, 1f)
                .build()
            var line = 0
            while (line < layout.lineCount) {
                val avail = PAGE_H - MARGIN - y
                var end = line
                val top = layout.getLineTop(line)
                while (end < layout.lineCount && layout.getLineBottom(end) - top <= avail) end++
                if (end == line) { newPage(); continue }
                val bottom = layout.getLineBottom(end - 1)
                val canvas = page.canvas
                canvas.save()
                canvas.translate(MARGIN.toFloat(), y - top)
                canvas.clipRect(0f, top.toFloat(), width.toFloat(), bottom.toFloat())
                layout.draw(canvas)
                canvas.restore()
                y += (bottom - top) + spaceAfter
                line = end
                if (line < layout.lineCount) newPage()
            }
        }

        for (b in blocks) when (b) {
            is Block.Title -> draw(b.text, title, 8f)
            is Block.Heading -> { y += 6f; draw(b.text, heading, 4f) }
            is Block.Para -> draw(b.text, body, 5f)
            is Block.Bullet -> draw("• " + b.text, body, 3f)
            Block.Gap -> y += 6f
        }
        doc.finishPage(page)
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
    }
}
