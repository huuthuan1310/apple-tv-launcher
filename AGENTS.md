# Agent Runbook: Autonomous Android TV Transformation (LeoTV Profile)

This document is the single source of truth for AI agents (Antigravity, Claude Code, Cursor, Devin, Roo Code, Aider, Windsurf) maintaining and developing this repository.

---

## 🎯 Primary Goal & Architecture
Transform any connected Android TV or Google TV (Xiaomi Mi Box, Sony, TCL, OnePlus, Hisense, Shield, Chromecast) into an ultra-clean, ad-free tvOS-style experience under the **LeoTV** profile (`com.leotv.launcher`):
1. **Pure Hardware-Speed tvOS 18 Leanback Grid**:
   - Zero CPU-heavy blur/glow thread overhead.
   - 60fps Skia hardware-accelerated translucent glass platter on dock (`DockDecoration.java`).
2. **Horizontal Scrolling Dock (Up to 10 Apps)**:
   - Centered horizontally when <= screen width, cuộn ngang (horizontal scroll) when > screen width.
   - D-pad navigation across dock, grid, and top status bar.
   - In-place app reordering via `AppActionDialog` and `DockManagerDialog`.
3. **Streamlined Top Status Bar**:
   - **Left Cluster**: Grouped Time, Date, and live Weather (`leftInfoCluster`).
   - **Right Platter**: Minimal circular User Avatar (`btnProfile`) in idle state; smoothly expands on hover or D-pad UP to reveal functional controls (Settings, Wi-Fi, Inputs).
4. **Auto IP-Based Geolocation Weather**:
   - `IpWeather.java` automatically detects location via IP (no GPS permission required) with 3-layer fallback (`ipwho.is` -> `geojs.io` -> `ip-api.com`).
   - Queries Open-Meteo live weather and caches aggressively in `SharedPreferences`.
   - Android 5.1+ TLS compatibility via Composite TrustManager (`isrg_root_x1` + system CAs).
5. **Google Photos Dynamic Wallpaper Slideshow**:
   - `GooglePhotosWallpaperLoader.java` + `SlideshowAnimator.java` supporting Fade, Ken Burns, Slide Horizontal, and Zoom.
6. **Hardware Input Integrity**:
   - 100% hardware-speed volume hold acceleration preserved (zero accessibility lag).
   - Accessibility services (`HomeKeyService`) do NOT intercept volume or navigation keys.

---

## 🛠️ Build & Verification Reference

```bash
# Set Java 21 Home
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

# Run tests & build release APK
./gradlew test assembleRelease

# Install directly to connected device
adb install -r app/build/outputs/apk/release/app-release.apk

# Restart launcher
adb shell am force-stop com.leotv.launcher && adb shell am start -W -n com.leotv.launcher/com.leotv.launcher.MainActivity
```

---

## 🛡️ Critical Guardrails for Agents

1. **Maintain Extreme TV Performance**:
   - NEVER introduce real-time CPU screen captures, bitmap blurs, or background loop threads for UI visuals.
   - Always use 2D Skia Canvas drawing (`LinearGradient`, hardware-accelerated shaders) for glass/platter effects.
2. **Preserve Linux `InputDispatcher` Repeat Rate (Volume Keys)**:
   - Volume and navigation keys must NEVER be intercepted or consumed in accessibility services to preserve Android's native repeat queue and hold acceleration.
3. **Preserve Compatibility Across Android 5.1 (SDK 22) up to Android 14+ (SDK 34)**:
   - Always check Android API levels for features (e.g. TLS certificates, View animations, WindowInsets).
4. **Dock Sizing & Layout Rules**:
   - In `AppAdapter.DockAdapter`, dock item views MUST have explicit `vLp.width = w` (never `MATCH_PARENT`) to prevent items stretching horizontally across the screen.
5. **AI Agent Autonomous Approval Ban**:
   - AI agents must NEVER execute destructive commands or debloat system packages autonomously without explicit user approval in chat.
