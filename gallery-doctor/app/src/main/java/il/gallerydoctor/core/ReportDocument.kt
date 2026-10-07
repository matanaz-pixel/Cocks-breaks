package il.gallerydoctor.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface Block {
    data class Title(val text: String) : Block
    data class Heading(val text: String) : Block
    data class Para(val text: String) : Block
    data class Bullet(val text: String) : Block
    data object Gap : Block
}

/** One document model that feeds both the shared plain text and the PDF. */
object ReportDocument {
    private const val TABLE_LIMIT = 25

    fun build(data: ReportData): List<Block> {
        val i = data.input
        val a = data.analysis
        val out = ArrayList<Block>()

        out += Block.Title("דוח בדיקת הגלריה")
        if (i.generatedAtMillis > 0) {
            out += Block.Para("נוצר ב-" + SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date(i.generatedAtMillis)))
        }
        out += Block.Gap

        out += Block.Heading("סיכום: ${lightEmoji(a.light)} ${a.light.he}")
        out += Block.Para(a.verdict)
        out += Block.Para(
            "נבדקו ${He.files(i.totalFiles)} (${He.num(i.imageCount)} תמונות, ${He.num(i.videoCount)} סרטונים). " +
                "תקינים: ${He.num(i.okCount)}, חשודים: ${He.num(i.suspectCount)}, פגומים: ${He.num(i.brokenCount)}, " +
                "קרסו או נתקעו: ${He.num(i.crasherCount)}, רשומות רפאים: ${He.num(i.ghostCount)}.",
        )
        out += Block.Gap

        out += Block.Heading("הסיבות הסבירות ביותר")
        a.hypotheses.forEachIndexed { idx, h ->
            out += Block.Para("${idx + 1}. ${h.title} (רמת ביטחון: ${h.confidence.he})")
            h.evidence.forEach { out += Block.Bullet(it) }
        }
        out += Block.Gap

        out += Block.Heading("מה לעשות, צעד אחר צעד")
        a.actions.forEachIndexed { idx, s ->
            out += Block.Para("${idx + 1}. ${s.text}")
            out += Block.Bullet("למה זה רלוונטי: ${s.why}")
        }
        out += Block.Gap

        out += Block.Heading("אחסון")
        i.volumes.forEach {
            out += Block.Bullet("${it.label}${if (it.removable) " (כרטיס נשלף)" else ""}: ${it.usedPercent}% תפוס, פנוי ${He.bytes(it.freeBytes)} מתוך ${He.bytes(it.totalBytes)}${if (it.critical) " – קריטי" else ""}")
        }
        out += Block.Bullet("תמונות: ${He.bytes(i.imageBytes)}, סרטונים: ${He.bytes(i.videoBytes)}")
        if (i.topFolders.isNotEmpty()) out += Block.Para("התיקיות הגדולות ביותר:")
        i.topFolders.take(5).forEach { out += Block.Bullet("${it.path}: ${He.bytes(it.bytes)} (${He.files(it.count)})") }
        out += Block.Gap

        if (i.apps.isNotEmpty()) {
            out += Block.Heading("אפליקציות גלריה ואחסון מדיה")
            EnvLines.appLines(i.apps).forEach { out += Block.Bullet(it) }
            out += Block.Gap
        }
        if (i.suspectApps.isNotEmpty()) {
            out += Block.Heading("אפליקציות אחרות שיכולות להשפיע")
            EnvLines.suspectLines(i).forEach { out += Block.Bullet(it) }
            out += Block.Gap
        }
        val device = EnvLines.deviceLines(i)
        if (device.isNotEmpty()) {
            out += Block.Heading("המכשיר וההגדרות")
            device.forEach { out += Block.Bullet(it) }
            out += Block.Gap
        }
        out += Block.Heading("יומן הקריסות של המערכת")
        EnvLines.crashLogLines(i).forEach { out += Block.Bullet(it) }
        out += Block.Gap

        table(out, "קבצים שקרסו או נתקעו", i.crashers, i.crasherCount)
        table(out, "קבצים פגומים", i.broken, i.brokenCount)
        table(out, "קבצים חשודים", i.suspect, i.suspectCount)
        table(out, "קבצים חריגים בגודל או ברזולוציה", i.oversized, i.oversized.size)

        out += Block.Heading("כפילויות")
        if (i.dupGroupCount == 0) out += Block.Para("לא נמצאו כפילויות.")
        else {
            out += Block.Para("${He.num(i.dupGroupCount)} קבוצות כפילויות, אפשר לפנות ${He.bytes(i.reclaimableBytes)}.")
            i.dupGroups.take(10).forEach { g ->
                out += Block.Bullet("${g.files.size} עותקים של ${g.files.first().name} (${He.bytes(g.eachBytes)} כל אחד, בתיקיות: ${g.files.map { it.folder }.distinct().take(3).joinToString(", ")})")
            }
        }
        out += Block.Gap

        out += Block.Heading("תיקיות כבדות")
        if (i.heavyFolders.isEmpty()) out += Block.Para("אין תיקיות עם יותר מ-${He.num(i.folderThreshold)} קבצים.")
        i.heavyFolders.take(TABLE_LIMIT).forEach { out += Block.Bullet("${it.path}: ${He.files(it.count)}, ${He.bytes(it.bytes)}") }
        out += Block.Gap

        out += Block.Heading("אינדקס המדיה")
        out += Block.Bullet("רשומות רפאים (קובץ חסר): ${He.num(i.ghostCount)}")
        out += Block.Bullet("קבצים שלא באינדקס: ${He.num(maxOf(i.orphanCount, i.safOnlyCount))}")
        out += Block.Bullet("רשומות תקועות בכתיבה מעל 24 שעות: ${He.num(i.pendingStuckCount)}")
        out += Block.Bullet("קבצים זמניים ישנים: ${He.num(i.leftoverTempCount)}")
        if (i.indexChanged != null) out += Block.Bullet("שינויים תוך 60 שניות: ${He.num(i.indexChanged)} השתנו, ${He.num(i.indexVanished ?: 0)} נעלמו, ${He.num(i.indexAppeared ?: 0)} נוספו")
        out += Block.Gap

        out += Block.Heading("מגבלות הבדיקה")
        a.limits.forEach { out += Block.Bullet(it) }
        return out
    }

    private fun table(out: MutableList<Block>, title: String, rows: List<ProblemRow>, total: Int) {
        out += Block.Heading("$title (${He.num(total)})")
        if (rows.isEmpty()) {
            out += Block.Para("אין.")
        } else {
            rows.take(TABLE_LIMIT).forEach {
                out += Block.Bullet("${it.name} | ${it.folder} | ${He.bytes(it.sizeBytes)} | ${it.reasonsHe}")
            }
            if (total > TABLE_LIMIT) out += Block.Para("ועוד ${He.num(total - TABLE_LIMIT)}, הרשימה המלאה בקובץ ה-CSV.")
        }
        out += Block.Gap
    }

    private fun lightEmoji(l: Light) = when (l) { Light.RED -> "🔴"; Light.YELLOW -> "🟡"; Light.GREEN -> "🟢" }

    fun toPlainText(blocks: List<Block>): String = buildString {
        for (b in blocks) when (b) {
            is Block.Title -> appendLine(b.text).appendLine("=".repeat(b.text.length.coerceAtMost(30)))
            is Block.Heading -> appendLine().appendLine("## ${b.text}")
            is Block.Para -> appendLine(b.text)
            is Block.Bullet -> appendLine("  • ${b.text}")
            Block.Gap -> {}
        }
    }.trim() + "\n"
}

/** CSV with a UTF-8 BOM so Excel on Windows shows Hebrew correctly. */
object CsvWriter {
    const val BOM = "﻿"
    val HEADER = listOf("status", "reason_codes", "reasons_he", "name", "folder", "size_bytes", "mime", "width", "height", "duration_ms", "volume", "uri")

    fun escape(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    fun row(r: ProblemRow): String = listOf(
        r.status.name, Reason.toCsv(r.reasons), r.reasonsHe, r.name, r.folder, r.sizeBytes.toString(),
        r.mime ?: "", r.width.toString(), r.height.toString(), r.durationMs.toString(), r.volume, r.uri,
    ).joinToString(",") { escape(it) }

    fun header(): String = HEADER.joinToString(",")
}
