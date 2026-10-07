package il.gallerydoctor.ui

import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import il.gallerydoctor.ScanController
import il.gallerydoctor.ScanState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        enableEdgeToEdge()
        setContent {
            GalleryDoctorTheme { App() }
        }
    }
}

/** Exactly three screens: Start, Progress, Report. A failed scan is shown as a banner on Start. */
@Composable
private fun App() {
    val ctx = LocalContext.current
    val state by ScanController.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { ScanController.refreshIdle(ctx) }

    Scaffold(modifier = Modifier.fillMaxSize(), containerColor = MaterialTheme.colorScheme.surface) { padding ->
        when (val s = state) {
            is ScanState.Idle -> StartScreen(padding, s, error = null)
            is ScanState.Failed -> StartScreen(padding, ScanState.Idle(), error = s.message)
            is ScanState.Running -> ProgressScreen(padding, s)
            is ScanState.Done -> ReportScreen(padding, s.report)
        }
    }
}
