package il.gallerydoctor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import il.gallerydoctor.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that only exists to keep the UI-process orchestrator alive while the screen
 * is off. The real work happens in [ScanController] and the isolated :scanner processes.
 */
class ScanService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        val first = build(getString(R.string.notif_text_starting), 0, 0)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, first, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, first)
        }
        scope.launch {
            ScanController.state.collect { s ->
                if (s is ScanState.Running) {
                    val text = when (s.phase) {
                        Phase.ENUMERATING -> getString(R.string.phase_enumerating)
                        Phase.FILES -> getString(R.string.phase_files)
                        Phase.CHECKING -> getString(R.string.phase_checking)
                        Phase.DUPLICATES -> getString(R.string.phase_duplicates)
                        Phase.FINISHING -> getString(R.string.phase_finishing)
                    }
                    getSystemService(NotificationManager::class.java)
                        .notify(NOTIF_ID, build(text, s.done.toInt(), s.total.toInt()))
                } else {
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun build(text: String, done: Int, total: Int): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(total, done, total == 0)
            .build()
    }

    companion object {
        private const val CHANNEL = "scan"
        private const val NOTIF_ID = 1
    }
}
