#!/usr/bin/env bash
# Read-only status, with optional D-pad test, screenshot or reboot. USB only.
set -euo pipefail
cd "$(dirname "$0")/.."
case "${1:-status}" in
 status)
   adb -d shell dumpsys activity activities | grep mFocusedActivity || true
   adb -d shell dumpsys accessibility
   adb -d shell dumpsys meminfo com.leotv.launcher | head -29
   adb -d shell df /data
   ;;
 home) adb -d shell input keyevent HOME ;;
 keys) shift; for key in "$@"; do adb -d shell input keyevent "$key"; done ;;
 screenshot)
   mkdir -p shots
   adb -d shell screencap -p /sdcard/leo-check.png
   adb -d pull /sdcard/leo-check.png "shots/${2:-leo-home}.png"
   ;;
 logs) adb -d logcat -d -s LeoHome:I AndroidRuntime:E ;;
 reboot) adb -d reboot ;;
 reconnect) adb -d wait-for-device; adb -d shell getprop sys.boot_completed ;;
 *) echo 'Usage: verify-leo-usb.sh [status|home|keys KEY...|screenshot NAME|logs|reboot|reconnect]'; exit 1 ;;
esac
