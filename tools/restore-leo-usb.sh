#!/usr/bin/env bash
# Return to Leanback; no system app or data is removed.
set -euo pipefail
python3 - <<'PY'
import subprocess

def adb(*args):
    return subprocess.check_output(['adb','-d',*args],text=True).strip()
component='com.leotv.launcher/com.leotv.launcher.HomeKeyService'
adb('shell','pm','unhide','com.google.android.leanbacklauncher')
adb('shell','pm','unhide','com.google.android.youtube.tv')
import os
if os.path.exists('backups/leo-usb/hidden-debloat.txt'):
    with open('backups/leo-usb/hidden-debloat.txt') as f:
        for p in f.read().splitlines():
            if p.strip():
                try: adb('shell','pm','unhide',p.strip())
                except: pass
services=[s for s in adb('shell','settings','get','secure','enabled_accessibility_services').split(':') if s not in ('','null',component)]
if services:
    adb('shell','settings','put','secure','enabled_accessibility_services',':'.join(services))
else:
    adb('shell','settings','delete','secure','enabled_accessibility_services')
    adb('shell','settings','delete','secure','accessibility_enabled')
adb('shell','am','start','-n','com.google.android.leanbacklauncher/.MainActivity')
PY
