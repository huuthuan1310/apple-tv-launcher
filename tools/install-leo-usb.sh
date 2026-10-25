#!/usr/bin/env bash
# Launcher-only API22 USB install. No debloat, default-Home changes or key filtering.
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ -z "${JAVA_HOME:-}" && -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]]; then
    export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
fi
./gradlew assembleRelease

APK="${1:-app/build/outputs/apk/release/app-release.apk}"
SDK_LEVEL=$(adb -d shell getprop ro.build.version.sdk | tr -d '\r')
[[ "$SDK_LEVEL" -ge 22 ]] || { echo 'Android SDK 22 or newer required'; exit 1; }
[[ -f "$APK" ]] || { echo "APK missing: $APK"; exit 1; }
mkdir -p backups/leo-usb
[[ -f backups/leo-usb/secure-before.txt ]] || adb -d shell settings list secure > backups/leo-usb/secure-before.txt
[[ -f backups/leo-usb/disabled-before.txt ]] || adb -d shell pm list packages -d > backups/leo-usb/disabled-before.txt
adb -d install -r "$APK"
adb -d shell pm hide com.google.android.leanbacklauncher || true
adb -d shell am start -W -n com.leotv.launcher/com.leotv.launcher.MainActivity
# Enable the launcher helper, preserving unrelated accessibility services.
python3 - <<'PY'
import subprocess

def adb(*args):
    return subprocess.check_output(['adb','-d',*args],text=True).strip()
current=adb('shell','settings','get','secure','enabled_accessibility_services')
services=[s for s in current.split(':') if s not in ('','null')]
component='com.leotv.launcher/com.leotv.launcher.HomeKeyService'
if component not in services: services.append(component)
adb('shell','settings','put','secure','enabled_accessibility_services',':'.join(services))
adb('shell','settings','put','secure','accessibility_enabled','1')
PY
