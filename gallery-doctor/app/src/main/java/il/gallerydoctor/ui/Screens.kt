package il.gallerydoctor.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import il.gallerydoctor.Phase
import il.gallerydoctor.R
import il.gallerydoctor.ScanController
import il.gallerydoctor.ScanOptions
import il.gallerydoctor.ScanState
import il.gallerydoctor.core.He
import il.gallerydoctor.core.TimelineEvent
import il.gallerydoctor.recorder.RecorderLog
import il.gallerydoctor.recorder.RecorderService
import il.gallerydoctor.scan.SafEnumerator

private fun mediaPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

private fun hasMediaAccess(ctx: Context) =
    mediaPermissions().all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }

/** Screen 1: one big button, a few optional extras. */
@Composable
fun StartScreen(padding: PaddingValues, idle: ScanState.Idle, error: String?) {
    val ctx = LocalContext.current
    var quick by rememberSaveable { mutableStateOf(false) }
    var tree by rememberSaveable { mutableStateOf<String?>(null) }
    var treeName by rememberSaveable { mutableStateOf<String?>(null) }
    var permissionProblem by rememberSaveable { mutableStateOf(false) }
    var resumeAfterPermission by rememberSaveable { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
            }
            tree = uri.toString()
            treeName = SafEnumerator.displayName(ctx, uri)
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasMediaAccess(ctx)) {
            permissionProblem = false
            ScanController.start(ctx, ScanOptions(deep = !quick, treeUri = tree, resume = resumeAfterPermission))
        } else permissionProblem = true
    }

    var recorderOn by remember { mutableStateOf(RecorderLog.isEnabled(ctx)) }
    val recorderPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasMediaAccess(ctx)) {
            RecorderService.start(ctx)
            recorderOn = true
            permissionProblem = false
        } else permissionProblem = true
    }
    fun setRecorder(on: Boolean) {
        if (!on) { RecorderService.stop(ctx); recorderOn = false; return }
        if (hasMediaAccess(ctx)) { RecorderService.start(ctx); recorderOn = true }
        else recorderPermission.launch(if (Build.VERSION.SDK_INT >= 33) mediaPermissions() + Manifest.permission.POST_NOTIFICATIONS else mediaPermissions())
    }

    fun begin(resume: Boolean) {
        resumeAfterPermission = resume
        if (hasMediaAccess(ctx)) {
            ScanController.start(ctx, ScanOptions(deep = !quick, treeUri = tree, resume = resume))
        } else {
            val wanted = if (Build.VERSION.SDK_INT >= 33) mediaPermissions() + Manifest.permission.POST_NOTIFICATIONS else mediaPermissions()
            permissionLauncher.launch(wanted)
        }
    }

    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(stringResource(R.string.start_title), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.start_body), style = MaterialTheme.typography.bodyLarge)

        if (error != null) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.scan_failed_title), style = MaterialTheme.typography.titleMedium)
                    Text(error, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (permissionProblem) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.perm_needed), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = {
                        ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)))
                    }) { Text(stringResource(R.string.btn_open_settings)) }
                }
            }
        }

        if (Build.MANUFACTURER.equals("xiaomi", ignoreCase = true)) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.xiaomi_hint), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = {
                        ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)))
                    }) { Text(stringResource(R.string.btn_open_settings)) }
                }
            }
        }

        Button(
            onClick = { begin(resume = false) },
            modifier = Modifier.fillMaxWidth().height(104.dp),
            shape = RoundedCornerShape(28.dp),
        ) { Text(stringResource(R.string.btn_start), style = MaterialTheme.typography.headlineMedium) }

        if (idle.canResume) {
            OutlinedButton(onClick = { begin(resume = true) }, modifier = Modifier.fillMaxWidth().height(60.dp)) {
                Text(stringResource(R.string.btn_resume))
            }
        }
        if (idle.hasReport) {
            OutlinedButton(onClick = { ScanController.showLastReport(ctx) }, modifier = Modifier.fillMaxWidth().height(60.dp)) {
                Text(stringResource(R.string.btn_last_report))
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.recorder_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.recorder_desc), style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = recorderOn, onCheckedChange = { setRecorder(it) })
                }
                val samples = remember(recorderOn) { RecorderLog.readAll(ctx).filterIsInstance<TimelineEvent.Sample>() }
                Text(
                    if (samples.isEmpty()) stringResource(R.string.recorder_status_none)
                    else stringResource(R.string.recorder_status, He.num(samples.size), java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.US).format(java.util.Date(samples.last().ts))),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.opt_quick), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.opt_quick_desc), style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = quick, onCheckedChange = { quick = it })
                }
                if (treeName == null) {
                    OutlinedButton(onClick = { picker.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.btn_pick_folder))
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.folder_selected, treeName.orEmpty()), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { tree = null; treeName = null }) { Text(stringResource(R.string.btn_clear_folder)) }
                    }
                }
            }
        }
    }
}

/** Screen 2: progress with ETA. Keeps the screen on while visible. */
@Composable
fun ProgressScreen(padding: PaddingValues, s: ScanState.Running) {
    val activity = LocalActivity.current
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    val phase = when (s.phase) {
        Phase.ENUMERATING -> R.string.phase_enumerating
        Phase.FILES -> R.string.phase_files
        Phase.CHECKING -> R.string.phase_checking
        Phase.DUPLICATES -> R.string.phase_duplicates
        Phase.FINISHING -> R.string.phase_finishing
    }
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        Text(stringResource(R.string.progress_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(phase), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)

        if (s.total > 0) {
            LinearProgressIndicator(progress = { (s.done.toFloat() / s.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(14.dp))
            Text(stringResource(R.string.progress_count, He.num(s.done), He.num(s.total)), style = MaterialTheme.typography.titleMedium)
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(14.dp))
            if (s.done > 0) Text(stringResource(R.string.progress_count_only, He.num(s.done)), style = MaterialTheme.typography.titleMedium)
        }
        s.etaSeconds?.let { Text(etaText(it), style = MaterialTheme.typography.bodyLarge) }
        s.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
        if (s.crashers > 0) Text(stringResource(R.string.progress_crashers, s.crashers), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        if (s.workerRestarts > 0) Text(stringResource(R.string.progress_restarts, s.workerRestarts), style = MaterialTheme.typography.bodySmall)

        Text(stringResource(R.string.progress_hint), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        OutlinedButton(onClick = { ScanController.cancel() }, modifier = Modifier.fillMaxWidth().height(60.dp)) {
            Text(stringResource(R.string.btn_cancel))
        }
    }
}

@Composable
private fun etaText(seconds: Long): String {
    val minutes = (seconds + 30) / 60
    return when {
        seconds < 60 -> stringResource(R.string.eta_less_than_minute)
        minutes < 60 -> stringResource(R.string.eta_minutes, minutes.toInt())
        else -> stringResource(R.string.eta_hours, (minutes / 60).toInt(), (minutes % 60).toInt())
    }
}
