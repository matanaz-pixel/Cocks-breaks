package il.gallerydoctor.recorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import il.gallerydoctor.R
import il.gallerydoctor.core.TimelineEvent
import il.gallerydoctor.scan.MediaEnumerator
import il.gallerydoctor.scan.StorageInfo
import il.gallerydoctor.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * Optional foreground service: every [SAMPLE_EVERY_TICKS] minutes it writes one reading of the media index
 * (database version, change counter, file count, free space, battery) and counts index change notifications.
 * It reads nothing but those counters and writes only its own log in the app's private folder.
 */
class RecorderService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val changes = AtomicInteger(0)
    private var loop: Job? = null
    private var observer: ContentObserver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.recorder_channel), NotificationManager.IMPORTANCE_MIN))
        val n = notification()
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(NOTIF_ID, n)
        RecorderLog.setEnabled(this, true)
        if (loop?.isActive != true) {
            RecorderLog.noteBoot(this)
            RecorderLog.append(this, TimelineEvent.Started(System.currentTimeMillis()))
            watch()
            loop = scope.launch {
                var tick = 0
                while (isActive) {
                    if (tick % SAMPLE_EVERY_TICKS == 0) sample()
                    delay(60_000)
                    tick++
                    val n2 = changes.getAndSet(0)
                    if (n2 > 0) RecorderLog.append(this@RecorderService, TimelineEvent.Changes(System.currentTimeMillis(), n2))
                }
            }
        }
        return START_STICKY
    }

    private fun watch() {
        val o = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) { changes.incrementAndGet() }
        }
        observer = o
        try {
            contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, o)
            contentResolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, o)
        } catch (_: SecurityException) {
        }
    }

    private fun sample() {
        val now = System.currentTimeMillis()
        val bat = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = bat?.let { it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / maxOf(1, it.getIntExtra(BatteryManager.EXTRA_SCALE, 100)) } ?: -1
        val status = bat?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        for (v in StorageInfo.volumes(this)) {
            val r = MediaProbe.read(this, v.mediaStoreName) ?: continue
            val stat = v.root?.let { try { android.os.StatFs(it.path) } catch (_: Exception) { null } }
            RecorderLog.append(
                this,
                TimelineEvent.Sample(now, v.mediaStoreName, r.version, r.generation, r.count, stat?.availableBytes ?: -1, stat?.totalBytes ?: -1, level, charging),
            )
        }
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.recorder_title))
            .setContentText(getString(R.string.recorder_notif_text))
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        observer?.let { contentResolver.unregisterContentObserver(it) }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "recorder"
        private const val NOTIF_ID = 2
        private const val SAMPLE_EVERY_TICKS = 10

        fun start(ctx: Context) {
            androidx.core.content.ContextCompat.startForegroundService(ctx, Intent(ctx, RecorderService::class.java))
        }

        fun stop(ctx: Context) {
            RecorderLog.setEnabled(ctx, false)
            ctx.stopService(Intent(ctx, RecorderService::class.java))
        }
    }
}

/** One reading of the media index of a volume. */
data class MediaReading(val version: String, val generation: Long, val count: Int)

object MediaProbe {
    /** Database version (changes when the media database is recreated or upgraded), change counter and image+video count. */
    fun read(ctx: Context, volume: String): MediaReading? = try {
        val version = if (Build.VERSION.SDK_INT >= 29) MediaStore.getVersion(ctx, volume) else MediaStore.getVersion(ctx)
        val generation = if (Build.VERSION.SDK_INT >= 30) MediaStore.getGeneration(ctx, volume) else -1L
        MediaReading(version ?: "", generation, MediaEnumerator.countVolume(ctx, volume))
    } catch (_: Exception) {
        null
    }
}
