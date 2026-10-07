@echo off
rem Gallery Doctor - evidence collector (Windows). READ-ONLY: it only reads information from the phone through adb
rem and writes text files into a folder on this computer. It changes nothing on the phone except one permission grant
rem (READ_LOGS to Gallery Doctor, so the app can show the gallery's real crash).
rem
rem Needs: adb (Android platform-tools) and a phone with USB debugging on, connected and authorised.
rem Xiaomi / Redmi / POCO: Settings > Additional settings > Developer options > turn on "USB debugging" AND
rem "USB debugging (Security settings)" (needs a Mi account and a SIM or Wi-Fi once).
setlocal enabledelayedexpansion
set APP=il.gallerydoctor
set PKGS=com.miui.gallery com.google.android.apps.photos com.google.android.apps.photosgo com.android.gallery3d com.google.android.providers.media.module com.android.providers.media.module com.android.providers.media

where adb >nul 2>nul
if errorlevel 1 (
  echo adb not found. Install Android platform-tools: https://developer.android.com/tools/releases/platform-tools
  echo and put this file inside the platform-tools folder, or add that folder to PATH.
  pause
  exit /b 1
)
adb start-server >nul 2>nul
for /f "delims=" %%S in ('adb get-state 2^>nul') do set STATE=%%S
if not "%STATE%"=="device" (
  echo No authorised phone found. Connect the phone with a cable, turn on USB debugging and tap Allow on the phone.
  echo Then run this file again. You can check with:  adb devices   (the phone must say "device", not "unauthorized")
  pause
  exit /b 1
)
for /f "delims=" %%T in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd-HHmmss"') do set STAMP=%%T
set OUT=gallery-evidence-%STAMP%
mkdir "%OUT%"
echo Writing to: %OUT%

echo Granting READ_LOGS to %APP% ...
adb shell pm grant %APP% android.permission.READ_LOGS > "%OUT%\grant-read-logs.txt" 2>&1
type "%OUT%\grant-read-logs.txt"

echo Collecting...
adb shell "getprop | grep -E 'ro.product.(model|brand|name)|ro.build.version.(release|sdk|security_patch|incremental)|ro.miui|ro.mi.os|ro.build.display.id|ro.boot.bootreason|sys.boot.reason|persist.sys.boot.reason'" > "%OUT%\device.txt" 2>&1
adb shell "df -h /data /storage/emulated; dumpsys diskstats" > "%OUT%\storage.txt" 2>&1
adb shell "dumpsys battery" > "%OUT%\battery.txt" 2>&1
adb shell "getprop | grep -i reason; uptime" > "%OUT%\boot-reasons.txt" 2>&1
adb shell "logcat -b crash -d -v threadtime" > "%OUT%\crash-buffer.txt" 2>&1
adb shell "logcat -d -v threadtime | grep -iE 'gallery|MediaProvider|media.module|sqlite|database|corrupt|Media scan|MediaScanner|FATAL EXCEPTION|ANR in|lowmemorykiller|lmkd'" > "%OUT%\logcat-media-related.txt" 2>&1
for %%G in (data_app_crash data_app_anr data_app_native_crash system_app_crash system_app_anr SYSTEM_TOMBSTONE) do (
  adb shell "dumpsys dropbox --print %%G" > "%OUT%\dropbox-%%G.txt" 2>&1
)
for %%P in (%PKGS%) do (
  adb shell "dumpsys activity exit-info %%P" > "%OUT%\exit-info-%%P.txt" 2>&1
  adb shell "dumpsys package %%P | grep -E 'versionName|versionCode|firstInstallTime|lastUpdateTime|targetSdk|enabled|stopped|installerPackageName'" > "%OUT%\package-%%P.txt" 2>&1
  adb shell "appops get %%P" > "%OUT%\appops-%%P.txt" 2>&1
)
adb shell "dumpsys deviceidle whitelist" > "%OUT%\deviceidle-whitelist.txt" 2>&1
adb shell "echo images:; content query --uri content://media/external/images/media --projection _id | wc -l; echo video:; content query --uri content://media/external/video/media --projection _id | wc -l; echo favorites_images:; content query --uri content://media/external/images/media --projection _id --where 'is_favorite=1' | wc -l; echo favorites_video:; content query --uri content://media/external/video/media --projection _id --where 'is_favorite=1' | wc -l" > "%OUT%\media-counts.txt" 2>&1
adb shell "du -s /sdcard/* 2>/dev/null | sort -rn | head -30" > "%OUT%\folder-sizes.txt" 2>&1
adb shell "find /sdcard -maxdepth 4 -name .nomedia 2>/dev/null | head -100" > "%OUT%\nomedia-files.txt" 2>&1

echo.
echo Done. Files are in: %CD%\%OUT%
echo They can contain app names and file/folder names. Review before sharing.
pause
endlocal
