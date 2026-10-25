# CLAUDE.md — Operating Guidelines for AI Assistants

This repository contains **LeoTV**, a tvOS-style launcher for Android TV / Google TV.

## 🛠️ Tool Reference

| Script | Purpose |
|---|---|
| `./tools/build-apk.sh` | Run tests, lint, build release APK → `dist/LeoTV.apk` |
| `./tools/audit.sh [TV_IP]` | Read-only TV inspection (packages, Home owner, hardware). No changes to device. |
| `./tools/apply.sh <plan> [TV_IP]` | Execute an approved plan with snapshot + auto-rollback. |
| `./tools/restore.sh <snapshot_dir> [TV_IP]` | Full rollback: re-enable packages, restore stock launcher. |

Build reference (macOS):

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew test assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell am force-stop com.leotv.launcher && \
  adb shell am start -W -n com.leotv.launcher/com.leotv.launcher.MainActivity
```

## 🛡️ Critical Guardrails

1. **Autonomous Approval Ban** — Never run `apply.sh`, `setup.sh -y`, or any debloat command without the user explicitly saying "Approved" or "Proceed" in chat.
2. **Protected packages** — Always honor `tools/protected.txt` (Chromecast, Assistant, Gboard, Bluetooth, Wi-Fi, Settings, System UI, GMS, Setup Wizards).
3. **Stock launcher protection** — Never disable `launcherx`, `tvlauncher`, `leanbacklauncher`, or `mitv.tvhome` via generic debloat; handle Home handover exclusively through Phase 3 of `apply.sh`.
4. **Volume key integrity** — Never intercept volume or navigation keys in accessibility services; preserve the Linux `InputDispatcher` repeat rate.
5. **No CPU-heavy UI** — Never introduce bitmap blurs, real-time screen captures, or background loop threads for UI visuals. Use 2D Skia Canvas (`LinearGradient`, hardware shaders) only.
6. **API level compatibility** — Always guard features for Android 5.1 (SDK 22) through Android 14+ (SDK 34).
