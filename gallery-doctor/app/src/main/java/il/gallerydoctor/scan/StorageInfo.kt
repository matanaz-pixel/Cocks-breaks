package il.gallerydoctor.scan

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import il.gallerydoctor.core.AppInfo
import il.gallerydoctor.core.AppRole
import il.gallerydoctor.core.VolumeStat
import java.io.File

object StorageInfo {
    /** A mounted volume: its MediaStore name, user-facing label, root directory and capacity. */
    data class Volume(val mediaStoreName: String, val label: String, val root: File?, val removable: Boolean)

    fun volumes(ctx: Context): List<Volume> {
        val out = ArrayList<Volume>()
        if (Build.VERSION.SDK_INT >= 30) {
            val sm = ctx.getSystemService(StorageManager::class.java)
            for (v in sm.storageVolumes) {
                if (v.state != Environment.MEDIA_MOUNTED && v.state != Environment.MEDIA_MOUNTED_READ_ONLY) continue
                val name = v.mediaStoreVolumeName ?: continue
                val label = if (v.isPrimary) "אחסון פנימי" else (v.getDescription(ctx) ?: "כרטיס SD")
                out += Volume(name, label, v.directory, v.isRemovable)
            }
        } else {
            @Suppress("DEPRECATION")
            out += Volume("external", "אחסון פנימי", Environment.getExternalStorageDirectory(), false)
            ctx.getExternalFilesDirs(null).drop(1).filterNotNull().forEach { dir ->
                val root = dir.absolutePath.substringBefore("/Android/data")
                out += Volume(File(root).name.lowercase(), "כרטיס SD", File(root), true)
            }
        }
        return out
    }

    fun volumeStats(ctx: Context): List<VolumeStat> = volumes(ctx).mapNotNull { v ->
        val root = v.root ?: return@mapNotNull null
        try {
            val s = StatFs(root.path)
            VolumeStat(v.label, s.totalBytes, s.availableBytes, v.removable)
        } catch (_: Exception) {
            null
        }
    }

    /** State of every volume the system knows, including ones that are not healthy. A removed card is not a problem. */
    fun volumeStates(ctx: Context): List<il.gallerydoctor.core.VolumeState> {
        val sm = ctx.getSystemService(StorageManager::class.java)
        return sm.storageVolumes.filter { it.state != Environment.MEDIA_REMOVED && it.state != "unknown" }.map { v ->
            il.gallerydoctor.core.VolumeState(
                if (v.isPrimary) "אחסון פנימי" else (v.getDescription(ctx) ?: "כרטיס SD"), v.state, v.isRemovable,
            )
        }
    }

    /** MediaStore volume name -> label, so per-file volume names can be shown to the user. */
    fun labels(ctx: Context): Map<String, String> = volumes(ctx).associate { it.mediaStoreName to it.label }
}

object GalleryApps {
    private val GALLERY_PACKAGES = listOf(
        "com.google.android.apps.photos", "com.google.android.gallery3d", "com.android.gallery3d",
        "com.sec.android.gallery3d", "com.miui.gallery", "com.coloros.gallery3d", "com.oplus.gallery",
        "com.oneplus.gallery", "com.huawei.photos", "com.motorola.gallery", "com.vivo.gallery",
        "com.simplemobiletools.gallery.pro", "com.fossify.gallery",
    )
    private val PROVIDER_PACKAGES = listOf(
        "com.google.android.providers.media.module", "com.android.providers.media.module", "com.android.providers.media",
    )

    /** Needs the <queries> block in the manifest, otherwise Android 11+ hides other packages. */
    fun detect(ctx: Context): List<AppInfo> {
        val pm = ctx.packageManager
        val found = LinkedHashMap<String, AppInfo>()

        fun add(pkg: String, role: AppRole) {
            if (pkg in found) return
            try {
                val info = pm.getPackageInfo(pkg, 0)
                val ai = pm.getApplicationInfo(pkg, 0)
                val installer = try {
                    if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(pkg).installingPackageName
                    else @Suppress("DEPRECATION") pm.getInstallerPackageName(pkg)
                } catch (_: Exception) {
                    null
                }
                found[pkg] = AppInfo(
                    pkg, pm.getApplicationLabel(ai).toString(), info.versionName, role,
                    installedAtMillis = info.firstInstallTime, updatedAtMillis = info.lastUpdateTime,
                    installer = installer, targetSdk = ai.targetSdkVersion, enabled = ai.enabled,
                    stopped = (ai.flags and android.content.pm.ApplicationInfo.FLAG_STOPPED) != 0,
                    system = (ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0,
                )
            } catch (_: PackageManager.NameNotFoundException) {
            }
        }

        val galleryIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_APP_GALLERY)
        for (ri in pm.queryIntentActivities(galleryIntent, 0)) add(ri.activityInfo.packageName, AppRole.GALLERY)
        for (p in GALLERY_PACKAGES) add(p, AppRole.GALLERY)
        for (p in PROVIDER_PACKAGES) add(p, AppRole.MEDIA_PROVIDER)
        return found.values.toList()
    }
}
