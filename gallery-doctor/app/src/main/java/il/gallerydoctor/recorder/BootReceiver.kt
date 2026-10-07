package il.gallerydoctor.recorder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** After a reboot: remember that it happened and, if the recorder was on, start it again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        RecorderLog.noteBoot(context)
        if (RecorderLog.isEnabled(context)) {
            try {
                RecorderService.start(context)
            } catch (_: Exception) {
                // Android may refuse to start a foreground service in the background; the next app launch restarts it
            }
        }
    }
}
