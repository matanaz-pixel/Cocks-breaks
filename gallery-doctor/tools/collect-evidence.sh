#!/usr/bin/env bash
# Gallery Doctor - evidence collector (Linux / macOS). READ-ONLY: it only reads information from the phone through adb
# and writes text files into a folder on this computer. It changes nothing on the phone except one permission grant
# (READ_LOGS to Gallery Doctor, so the app can show the gallery's real crash).
#
# Needs: adb (Android platform-tools) and a phone with USB debugging on, connected and authorised.
# Xiaomi / Redmi / POCO: Settings > Additional settings > Developer options > turn on "USB debugging" AND
# "USB debugging (Security settings)" (needs a Mi account and a SIM or Wi-Fi once).
set -u

APP=il.gallerydoctor
GALLERIES="com.miui.gallery com.google.android.apps.photos com.google.android.apps.photosgo com.android.gallery3d"
PROVIDERS="com.google.android.providers.media.module com.android.providers.media.module com.android.providers.media com.android.providers.downloads.ui"
OUT="gallery-evidence-$(date +%Y%m%d-%H%M%S)"

if ! command -v adb >/dev/null 2>&1; then echo "adb not found. Install Android platform-tools: https://developer.android.com/tools/releases/platform-tools"; exit 1; fi
adb start-server >/dev/null 2>&1
state=$(adb get-state 2>/dev/null || true)
if [ "$state" != "device" ]; then
  echo "No authorised phone found (adb state: ${state:-none})."
  echo "Connect the phone with a cable, turn on USB debugging and tap 'Allow' on the phone, then run this again."
  exit 1
fi
mkdir -p "$OUT"
echo "Writing to: $OUT"

run() { # run <file> <shell command...>
  local f="$1"; shift
  echo "  - $f"
  adb shell "$@" > "$OUT/$f" 2>&1 || true
}

echo "Granting READ_LOGS to $APP (lets the app read the gallery crash log)..."
adb shell pm grant "$APP" android.permission.READ_LOGS 2>&1 | tee "$OUT/grant-read-logs.txt" || true

echo "Collecting..."
run device.txt 'getprop | grep -E "ro.product.(model|brand|name)|ro.build.version.(release|sdk|security_patch|incremental)|ro.miui|ro.mi.os|ro.build.display.id|ro.boot.bootreason|sys.boot.reason|persist.sys.boot.reason"'
run storage.txt 'df -h /data /storage/emulated 2>&1; echo; dumpsys diskstats'
run battery.txt 'dumpsys battery'
run boot-reasons.txt 'getprop | grep -i reason; echo; uptime'
run crash-buffer.txt 'logcat -b crash -d -v threadtime'
run logcat-media-related.txt 'logcat -d -v threadtime | grep -iE "gallery|MediaProvider|media.module|sqlite|database|corrupt|Media scan|MediaScanner|FATAL EXCEPTION|ANR in|lowmemorykiller|lmkd"'
for tag in data_app_crash data_app_anr data_app_native_crash system_app_crash system_app_anr SYSTEM_TOMBSTONE; do
  run "dropbox-$tag.txt" "dumpsys dropbox --print $tag"
done
for p in $GALLERIES $PROVIDERS; do
  run "exit-info-$p.txt" "dumpsys activity exit-info $p"
  run "package-$p.txt" "dumpsys package $p | grep -E 'versionName|versionCode|firstInstallTime|lastUpdateTime|targetSdk|enabled|stopped|installerPackageName|Package \\[' "
  run "appops-$p.txt" "appops get $p"
done
run deviceidle-whitelist.txt 'dumpsys deviceidle whitelist'
run media-counts.txt 'echo "images:"; content query --uri content://media/external/images/media --projection _id | wc -l; echo "video:"; content query --uri content://media/external/video/media --projection _id | wc -l; echo "favorites (images):"; content query --uri content://media/external/images/media --projection _id --where "is_favorite=1" | wc -l; echo "favorites (video):"; content query --uri content://media/external/video/media --projection _id --where "is_favorite=1" | wc -l'
run folder-sizes.txt 'du -s /sdcard/* 2>/dev/null | sort -rn | head -30'
run big-folders-camera.txt 'ls /sdcard/DCIM/Camera 2>/dev/null | wc -l; ls /sdcard/DCIM 2>/dev/null; ls -a /sdcard/Pictures 2>/dev/null'
run nomedia-files.txt 'find /sdcard -maxdepth 4 -name ".nomedia" 2>/dev/null | head -100'
run mi-cloud.txt 'dumpsys account 2>/dev/null | grep -E "Account \\{name=.*type=(com.xiaomi|com.google)" | sed -E "s/name=[^,]*/name=<hidden>/"'

echo
echo "Done. Files are in: $OUT"
echo "Open them in any text editor, or send the whole folder to whoever helps with the diagnosis."
echo "They can contain app names and file/folder names. Review before sharing."
