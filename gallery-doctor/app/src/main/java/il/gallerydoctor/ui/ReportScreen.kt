package il.gallerydoctor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import il.gallerydoctor.R
import il.gallerydoctor.ScanController
import il.gallerydoctor.core.Confidence
import il.gallerydoctor.core.EnvLines
import il.gallerydoctor.core.He
import il.gallerydoctor.core.Hypothesis
import il.gallerydoctor.core.Light
import il.gallerydoctor.core.ProblemRow
import il.gallerydoctor.core.ReportData
import il.gallerydoctor.data.StateDb
import il.gallerydoctor.export.Exporter

private const val UI_ROWS = 50

private fun lightColor(l: Light) = when (l) {
    Light.RED -> Signal.Red
    Light.YELLOW -> Signal.Yellow
    Light.GREEN -> Signal.Green
}

/** Screen 3: the report. Verdict first, then ranked causes, then steps, then the tables. */
@Composable
fun ReportScreen(padding: PaddingValues, data: ReportData) {
    val ctx = LocalContext.current
    val a = data.analysis
    val i = data.input
    val expanded = remember { mutableStateListOf<String>() }
    fun toggle(id: String) { if (id in expanded) expanded.remove(id) else expanded.add(id) }
    // stringResource is composable, so it must be resolved here and not inside the LazyColumn builder
    val tCrashers = stringResource(R.string.table_crashers)
    val tBroken = stringResource(R.string.table_broken)
    val tSuspect = stringResource(R.string.table_suspect)
    val tOversized = stringResource(R.string.table_oversized)
    val appLines = remember(data) { EnvLines.appLines(i.apps) }
    val suspectLines = remember(data) { EnvLines.suspectLines(i) }
    val deviceLines = remember(data) { EnvLines.deviceLines(i) }
    val crashLines = remember(data) { EnvLines.crashLogLines(i) }
    val healthyLines = remember(data) { EnvLines.healthyLines(i) }
    val tHealthy = stringResource(R.string.sec_healthy)
    val tApps = stringResource(R.string.sec_apps)
    val tOtherApps = stringResource(R.string.sec_other_apps)
    val tDevice = stringResource(R.string.sec_device)
    val tCrashLog = stringResource(R.string.sec_crashlog)

    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "title") {
            Text(stringResource(R.string.report_title), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
        }

        // ---- 1. health summary ----
        item(key = "summary") {
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.size(64.dp).background(lightColor(a.light), CircleShape))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.light_label, a.light.he), style = MaterialTheme.typography.titleLarge)
                        Text(a.verdict, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        item(key = "counts") {
            Text(
                "נבדקו ${He.files(i.totalFiles)}. תקינים: ${He.num(i.okCount)}, חשודים: ${He.num(i.suspectCount)}, פגומים: ${He.num(i.brokenCount)}, " +
                    "קרסו או נתקעו: ${He.num(i.crasherCount)}, רשומות רפאים: ${He.num(i.ghostCount)}.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        // ---- 2. most likely causes ----
        item(key = "h-causes") { SectionTitle(stringResource(R.string.sec_causes)) }
        items(a.hypotheses.size, key = { "hyp-${a.hypotheses[it].id}" }) { idx -> HypothesisCard(idx + 1, a.hypotheses[idx]) }

        bulletSection("healthy", tHealthy, healthyLines)

        // ---- 3. actions ----
        item(key = "h-actions") { SectionTitle(stringResource(R.string.sec_actions)) }
        items(a.actions.size, key = { "act-${a.actions[it].key}" }) { idx ->
            val step = a.actions[idx]
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${idx + 1}. ${step.text}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(stringResource(R.string.why_prefix, step.why), style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
                }
            }
        }

        // ---- storage + apps ----
        item(key = "h-storage") { SectionTitle(stringResource(R.string.sec_storage)) }
        items(i.volumes.size, key = { "vol-$it" }) { idx ->
            val v = i.volumes[idx]
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.volume_line, v.label, v.usedPercent, He.bytes(v.freeBytes), He.bytes(v.totalBytes)) +
                            if (v.critical) " - " + stringResource(R.string.volume_critical) else "",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LinearProgressIndicator(
                        progress = { v.usedPercent / 100f }, modifier = Modifier.fillMaxWidth().height(10.dp),
                        color = if (v.critical) Signal.Red else MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        item(key = "kinds") {
            Text("תמונות: ${He.bytes(i.imageBytes)}, סרטונים: ${He.bytes(i.videoBytes)}", style = MaterialTheme.typography.bodyMedium)
        }
        bulletSection("apps", tApps, appLines)
        bulletSection("other-apps", tOtherApps, suspectLines)
        bulletSection("device", tDevice, deviceLines)
        bulletSection("crashlog", tCrashLog, crashLines)

        // ---- 4. tables ----
        item(key = "h-tables") { SectionTitle(stringResource(R.string.sec_tables)) }
        fileTable("crashers", tCrashers, i.crasherCount, i.crashers, expanded, ::toggle)
        fileTable("broken", tBroken, i.brokenCount, i.broken, expanded, ::toggle)
        fileTable("suspect", tSuspect, i.suspectCount, i.suspect, expanded, ::toggle)
        fileTable("oversized", tOversized, i.oversized.size, i.oversized, expanded, ::toggle)

        item(key = "dups-h") { TableHeader("dups", stringResource(R.string.table_dups), i.dupGroupCount, expanded, ::toggle) }
        if ("dups" in expanded) {
            if (i.dupGroups.isEmpty()) item(key = "dups-empty") { Text(stringResource(R.string.table_empty)) }
            items(minOf(i.dupGroups.size, 10), key = { "dup-$it" }) { gi ->
                val g = i.dupGroups[gi]
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.dup_line, g.files.size, He.bytes(g.eachBytes)) + " - ניתן לפנות ${He.bytes(g.reclaimableBytes)}", fontWeight = FontWeight.Medium)
                        g.files.take(6).forEach { FileRowView(it) }
                    }
                }
            }
        }

        item(key = "heavy-h") { TableHeader("heavy", stringResource(R.string.table_heavy), i.heavyFolders.size, expanded, ::toggle) }
        if ("heavy" in expanded) {
            if (i.heavyFolders.isEmpty()) item(key = "heavy-empty") { Text(stringResource(R.string.table_empty)) }
            items(minOf(i.heavyFolders.size, UI_ROWS), key = { "heavy-$it" }) { idx ->
                val f = i.heavyFolders[idx]
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(14.dp)) {
                        Text(f.path, fontWeight = FontWeight.Medium)
                        Text(stringResource(R.string.folder_line, He.num(f.count), He.bytes(f.bytes)), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // ---- 5. export ----
        item(key = "h-export") { SectionTitle(stringResource(R.string.sec_export)) }
        item(key = "export") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { Exporter.shareText(ctx, data) }, Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.btn_share_text)) }
                Button(onClick = { Exporter.sharePdf(ctx, data) }, Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.btn_share_pdf)) }
                OutlinedButton(onClick = { Exporter.shareCsv(ctx, StateDb.get(ctx)) }, Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.btn_share_csv)) }
            }
        }

        // ---- 6. honest limits ----
        item(key = "h-limits") { SectionTitle(stringResource(R.string.sec_limits)) }
        items(a.limits.size, key = { "lim-$it" }) { idx -> Text("• ${a.limits[idx]}", style = MaterialTheme.typography.bodyMedium) }

        item(key = "new") {
            OutlinedButton(onClick = { ScanController.backToStart(ctx) }, Modifier.fillMaxWidth().height(60.dp)) { Text(stringResource(R.string.btn_new_scan)) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Column(Modifier.padding(top = 10.dp)) {
        Text(text, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        HorizontalDivider(Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun HypothesisCard(rank: Int, h: Hypothesis) {
    val tint = when (h.confidence) {
        Confidence.HIGH -> Signal.Red
        Confidence.MEDIUM -> Signal.Yellow
        Confidence.LOW -> Color.Gray
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("$rank. ${h.title}", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(14.dp).background(tint, CircleShape))
                Text(stringResource(R.string.confidence, h.confidence.he), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
            h.evidence.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun TableHeader(id: String, title: String, count: Int, expanded: List<String>, toggle: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { toggle(id) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$title (${He.num(count)})", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Text(if (id in expanded) "▲" else "▼", style = MaterialTheme.typography.titleMedium)
    }
}

/** A titled list of plain-text lines (apps, phone facts, crash log). Skipped when there is nothing to show. */
private fun LazyListScope.bulletSection(id: String, title: String, lines: List<String>) {
    if (lines.isEmpty()) return
    item(key = "h-$id") { SectionTitle(title) }
    items(lines.size, key = { "$id-$it" }) { idx -> Text("• ${lines[idx]}", style = MaterialTheme.typography.bodyMedium) }
}

private fun LazyListScope.fileTable(
    id: String, title: String, total: Int, rows: List<ProblemRow>,
    expanded: List<String>, toggle: (String) -> Unit,
) {
    item(key = "$id-h") { TableHeader(id, title, total, expanded, toggle) }
    if (id !in expanded) return
    if (rows.isEmpty()) item(key = "$id-empty") { Text(stringResource(R.string.table_empty)) }
    items(minOf(rows.size, UI_ROWS), key = { "$id-${rows[it].pk}" }) { idx ->
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Box(Modifier.padding(14.dp)) { FileRowView(rows[idx]) }
        }
    }
    if (total > UI_ROWS) item(key = "$id-more") { Text(stringResource(R.string.table_more, UI_ROWS), style = MaterialTheme.typography.bodySmall) }
}

/** One file: name, location, size, reasons and a button to open it in the system viewer for a manual test. */
@Composable
private fun FileRowView(row: ProblemRow) {
    val ctx = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(row.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        val meta = buildList {
            if (row.folder.isNotEmpty()) add(row.folder)
            add(He.bytes(row.sizeBytes))
            if (row.width > 0 && row.height > 0) add("${row.width}x${row.height}")
            if (row.isVideo && row.durationMs >= 0) add(He.duration(row.durationMs))
        }.joinToString(" | ")
        Text(meta, style = MaterialTheme.typography.bodySmall)
        if (row.reasonsHe.isNotEmpty()) Text(row.reasonsHe, style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { Exporter.openInViewer(ctx, row) }) { Text(stringResource(R.string.btn_open)) }
    }
}
