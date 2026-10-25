# LeoTV — tvOS-style launcher for Android TV

![Version](https://img.shields.io/badge/version-1.0--community-blue)
![Android](https://img.shields.io/badge/Android-5.1%2B%20(API%2022%2B)-green?logo=android)
![License](https://img.shields.io/badge/license-MIT-lightgrey)
![APK](https://img.shields.io/badge/APK-~738%20KB-orange)

LeoTV is developed by **Leo Nguyen**, based on the MIT-licensed
[Apple TV Launcher by Fardin](https://github.com/Fardin7798/apple-tv-launcher).
It brings a tvOS-style home screen to Android TV devices without replacing their ROM.

## Features

- Translucent frosted-glass Dock with zero CPU overhead (hardware 2D Skia shader).
- Hold OK on an app, choose **Move**, then use the D-pad to reorder. OK saves, Back cancels.
- Compact Control Center and launcher settings with a separate system information panel.
- Date, Vietnamese lunar date, time and live weather detected from the public IP address.
- Google Photos album wallpapers with Fade, Ken Burns, Slide and Zoom & Fade transitions.
- System language support; English fallback for untranslated strings. Bundled translations: Vietnamese, German, Catalan, Spanish, French, Russian, Simplified/Traditional Chinese, Thai, Serbian Latin, Polish, Arabic, Hebrew, Turkish, Portuguese.
- Home and Input accessibility helpers do **not** intercept volume or navigation keys.

Application ID: `com.leotv.launcher`. Requires Android 5.1 / API 22 or newer.

## Screenshots / Ảnh giao diện

[View all menus in English and Vietnamese](docs/screenshots.md).

| English | Tiếng Việt |
|---|---|
| ![LeoTV Home in English](docs/screenshots/en/home.png) | ![Màn hình chính LeoTV tiếng Việt](docs/screenshots/vi/home.png) |
| ![Launcher settings](docs/screenshots/en/settings.png) | ![Cài đặt launcher](docs/screenshots/vi/settings.png) |

## Build

Requires JDK 21, Android SDK Platform 36 and Android SDK Build Tools.
Set `ANDROID_HOME` or `sdk.dir` in `local.properties` (do not commit this file).
On macOS the script auto-detects the Android Studio JDK if `JAVA_HOME` is not set.
First run requires network access to download Gradle and dependencies.

```bash
./tools/build-apk.sh
```

Runs unit tests, lint, and release build, then exports:

- `dist/LeoTV.apk`
- `dist/LeoTV.apk.sha256`

### Signing key

To update an existing install, use the same signing key.
The script reads `LEO_KEYSTORE`, falling back to `~/.android/debug.keystore`
(alias `androiddebugkey`, password `android`).
Never commit the keystore or distribute it with the APK.

To generate a new development key (only when the file does not yet exist):

```bash
mkdir -p "$HOME/.android"
keytool -genkeypair -keystore "$HOME/.android/debug.keystore" \
  -storepass android -keypass android -alias androiddebugkey \
  -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=LeoTV Development"
./tools/build-apk.sh
```

## Debloat / Dọn dẹp box

> [!WARNING]
> Script debloat **chỉ được test trên Xiaomi Mi Box ROM 5.1.1**. Chưa test trên box khác.
> Đọc kỹ plan file trước khi approve. Không chạy mù.

Quy trình gồm 3 bước, **không thay đổi TV cho đến khi bạn xem xét và approve**:

### Bước 1 — Audit (chỉ đọc, không đụng TV)

```bash
./tools/audit.sh <TV_IP>
```

Tạo ra 3 file trong `reports/`:
- `report_<ts>.md` — toàn bộ thông tin hardware, ROM, package
- `plan_<ts>.txt` — danh sách app sẽ bị tắt (chỉnh sửa được)
- `apply_<ts>.sh` — script thực thi tương ứng plan

### Bước 2 — Xem xét plan

Mở `reports/plan_<ts>.txt`, bỏ dấu `[x]` ở những app bạn **muốn giữ lại**.
Đối chiếu với profile tương ứng trong `profiles/xiaomi.txt`.

### Bước 3 — Apply (sau khi đã approve)

```bash
./tools/apply.sh reports/plan_<ts>.txt <TV_IP>
```

Script tự chụp snapshot trước khi thay đổi. Rollback bất kỳ lúc nào:

```bash
./tools/restore.sh backups/<snapshot_dir> <TV_IP>
```

**Các package được bảo vệ tuyệt đối** (không bao giờ bị tắt): xem `tools/protected.txt`.

---

## Install on a Xiaomi Mi Box (ROM 5.1.1 / Android 5.1)

> These steps apply to Xiaomi Mi Box running stock ROM 5.1.1 (Android 5.1, API 22).
> Adapt serial flags for other devices listed in `adb devices`.

### 1 — Enable ADB on the box

1. **Settings → About → Build number**: press OK 7 times until developer mode activates.
2. **Settings → Developer options → USB debugging**: turn ON.
3. Connect the box to the same Wi-Fi as your computer, then note its IP address
   (**Settings → Network → Wi-Fi → current network → IP address**).

### 2 — Connect from your computer

```bash
adb connect <BOX_IP>:5555
# Accept the "Allow USB debugging?" dialog on the TV screen.
adb devices
# Confirm the box appears as "device" (not "unauthorized").
```

### 3 — Install LeoTV

```bash
# Build first (see Build section above), then:
adb -s <BOX_IP>:5555 install -r dist/LeoTV.apk

# Or install directly from the Gradle output:
adb -s <BOX_IP>:5555 install -r app/build/outputs/apk/release/app-release.apk
```

### 4 — Set as default Home

On the box: **Settings → Device → Home screen → Home app → LeoTV**.

If the ROM does not offer a picker, use the ADB preferred-activity method:

```bash
adb shell cmd package set-home-activity com.leotv.launcher/com.leotv.launcher.MainActivity
```

Verify:

```bash
adb shell input keyevent 3   # Home key — LeoTV should appear
```

### 5 — Enable the Home accessibility helper (optional)

Only needed if the ROM's Home button does not open LeoTV by default:

```bash
adb shell settings put secure enabled_accessibility_services \
  com.leotv.launcher/com.leotv.launcher.HomeKeyService
adb shell settings put secure accessibility_enabled 1
```

The helper intercepts **only** the Home keyevent; it does not touch volume or navigation keys.

### 6 — Useful ADB commands for ROM 5.1.1

```bash
# Force-stop and restart the launcher
adb shell am force-stop com.leotv.launcher
adb shell am start -W -n com.leotv.launcher/com.leotv.launcher.MainActivity

# Check which app owns Home
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN \
  -c android.intent.category.HOME

# List all enabled accessibility services
adb shell settings get secure enabled_accessibility_services

# Dump launcher activity stack
adb shell dumpsys activity activities | grep -E "mFocusedActivity|ResumedActivity"

# Read-only audit: hardware, ROM version, packages
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
adb shell pm list packages -e | sort

# Revert Home to stock Xiaomi launcher
adb shell cmd package set-home-activity com.miui.home/.launcher.Launcher
# or for the Mi Box leanback launcher:
adb shell cmd package set-home-activity com.google.android.leanbacklauncher/.MainHomeActivity
```

### 7 — Restore

The build scripts do **not** debloat or modify system packages.
To uninstall LeoTV and revert Home:

```bash
adb shell cmd package set-home-activity com.google.android.leanbacklauncher/.MainHomeActivity
adb uninstall com.leotv.launcher
```

## Install on another TV (general)

Transfer `dist/LeoTV.apk` via USB or a file-manager app, or:

```bash
adb -s <device-serial> install -r dist/LeoTV.apk
```

Open LeoTV from the app list, then select it as the default Home app in Android settings.
Enable the accessibility helper only if the ROM requires it for the Home button.
Keep the original launcher available as a fallback.

Apps, logins, Dock order and Google Photos album links must be configured on each device.
The YouTube banner opens SmartTube when installed; the App Store banner opens Google Play.

## Google Photos wallpapers

The APK ships with an **empty** Google Photos album link.
Add your own public album in launcher settings.
Updates signed with the same key retain saved album links and other app settings.

Weather is provided by [Open-Meteo](https://open-meteo.com/) under CC BY 4.0.

## Support LeoTV

If you enjoy LeoTV, you can support its development with a voluntary PayPal donation:

**[Donate via PayPal — paypal.me/leonguyens](https://paypal.me/leonguyens)**

Select **Donate via PayPal** at the bottom of the launcher settings list.
On TVs without a browser, the app shows the address and a copy-link button.

## License

[MIT license](LICENSE), including the original copyright notice from
[Apple TV Launcher by Fardin](https://github.com/Fardin7798/apple-tv-launcher).
