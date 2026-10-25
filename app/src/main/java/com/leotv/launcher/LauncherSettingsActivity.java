package com.leotv.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Remote-friendly Apple TV (tvOS 18) Settings activity:
 * Left hero branding with live system specs, Right grouped list controlling
 * tvOS layout, animations, system hardware (Display, Wi-Fi, Bluetooth), apps, and power.
 */
public final class LauncherSettingsActivity extends Activity {
    private SharedPreferences choices;
    private final TextView[] columnChoices = new TextView[4];
    private TextView animationsChoice;
    private TextView appBackdropsChoice;
    private TextView slideshowTransitionChoice;
    private TextView slideshowIntervalChoice;
    private TextView followThemeChoice;
    private int columns;
    private boolean animations;
    private boolean appBackdrops;
    private SlideshowTransition slideshowTransition;
    private int slideshowInterval;

    private String systemInformation() {
        String version;
        try { version = getPackageManager().getPackageInfo(getPackageName(),0).versionName; }
        catch (android.content.pm.PackageManager.NameNotFoundException ignored) { version = "—"; }
        return android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL
                + " · " + android.os.Build.DEVICE
                + "\nAndroid " + android.os.Build.VERSION.RELEASE + " · API " + android.os.Build.VERSION.SDK_INT
                + "\nROM: " + android.os.Build.DISPLAY
                + this.getString(R.string.leo_build_id_label) + android.os.Build.ID
                + "\nFirmware: " + android.os.Build.VERSION.INCREMENTAL
                + "\nLeoTV " + version;
    }

    private void showSystemInformation() {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.setContentView(R.layout.dialog_system_information);
        ((TextView) dialog.findViewById(R.id.systemInformation)).setText(systemInformation());
        dialog.findViewById(R.id.closeSystemInformation).setOnClickListener(v -> dialog.dismiss());
        Typography.apply(dialog.getWindow().getDecorView());
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        dialog.getWindow().setDimAmount(0.6f);
        dialog.show();
        float density = getResources().getDisplayMetrics().density;
        int width = Math.min((int) (520 * density),
                getResources().getDisplayMetrics().widthPixels - (int) (64 * density));
        dialog.getWindow().setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.findViewById(R.id.closeSystemInformation).requestFocus();
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_launcher_settings);
        Typography.apply(getWindow().getDecorView());
        findViewById(R.id.systemInformationChoice).setOnClickListener(v -> showSystemInformation());
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);
        columns = config.columns;
        animations = config.animations;
        appBackdrops = config.appBackdrops;
        slideshowTransition = config.slideshowTransition;
        slideshowInterval = config.slideshowInterval;

        // Apps per row choices (3, 4, 5, 6)
        int[] ids = {R.id.columns4, R.id.columns5, R.id.columns6, R.id.columns7};
        for (int i = 0; i < ids.length; i++) {
            final int value = i + 3;
            columnChoices[i] = findViewById(ids[i]);
            columnChoices[i].setOnClickListener(v -> {
                if (columns == value) return;
                columns = value;
                choices.edit().putInt("columns", value).apply();
                setResult(RESULT_OK);
                refresh();
            });
        }
        if (getResources().getBoolean(R.bool.rtl_app_grid)) {
            LinearLayout row = (LinearLayout) columnChoices[0].getParent();
            row.removeAllViews();
            for (int i = columnChoices.length - 1; i >= 0; i--) row.addView(columnChoices[i]);
        }

        columnChoices[Math.max(0, Math.min(columnChoices.length - 1, columns - 3))].requestFocus();

        // Animations Toggle
        animationsChoice = findViewById(R.id.animationsChoice);
        animationsChoice.setOnClickListener(v -> {
            animations = !animations;
            choices.edit().putBoolean("animations", animations).apply();
            setResult(RESULT_OK);
            refresh();
        });

        // Google Photos Wallpaper Settings & Album Sync
        findViewById(R.id.wallpaperChoice).setOnClickListener(v -> {
            new GooglePhotosAlbumDialog(this, () -> {
                setResult(RESULT_OK);
                refresh();
            }).show();
        });

        // Dynamic App Backdrops Toggle
        appBackdropsChoice = findViewById(R.id.appBackdropsChoice);
        appBackdropsChoice.setOnClickListener(v -> {
            appBackdrops = !appBackdrops;
            choices.edit().putBoolean("app_backdrops", appBackdrops).apply();
            setResult(RESULT_OK);
            refresh();
        });

        // Slideshow Transition Chooser & Live Preview
        slideshowTransitionChoice = findViewById(R.id.slideshowTransitionChoice);
        slideshowTransitionChoice.setOnClickListener(v -> {
            new SlideshowPreviewDialog(this, transition -> {
                slideshowTransition = transition;
                setResult(RESULT_OK);
                refresh();
            }).show();
        });

        // Slideshow Interval Chooser (10s, 15s, 30s, 60s)
        slideshowIntervalChoice = findViewById(R.id.slideshowIntervalChoice);
        slideshowIntervalChoice.setOnClickListener(v -> {
            int[] intervals = {10, 15, 30, 60};
            int nextIdx = 0;
            for (int i = 0; i < intervals.length; i++) {
                if (intervals[i] == slideshowInterval) {
                    nextIdx = (i + 1) % intervals.length;
                    break;
                }
            }
            slideshowInterval = intervals[nextIdx];
            choices.edit().putInt("slideshow_interval", slideshowInterval).apply();
            setResult(RESULT_OK);
            refresh();
        });

        // Dock Apps Manager
        findViewById(R.id.dockAppsChoice).setOnClickListener(v -> {
            new DockManagerDialog(this, () -> {
                setResult(RESULT_OK);
                refresh();
            }).show();
        });

        // Reset to default Apple TV theme
        followThemeChoice = findViewById(R.id.followThemeChoice);
        followThemeChoice.setOnClickListener(v -> {
            if (!hasOnTvChoices()) return;
            choices.edit().remove("columns").remove("animations").remove("advanced_effects").remove("app_backdrops")
                    .remove("slideshow_transition").remove("slideshow_interval").remove("custom_dock_apps")
                    .remove("google_photos_album").commit();
            Config current = Config.load(this);
            columns = current.columns;
            animations = current.animations;
            appBackdrops = current.appBackdrops;
            slideshowTransition = current.slideshowTransition;
            slideshowInterval = current.slideshowInterval;
            setResult(RESULT_OK);
            refresh();
        });

        findViewById(R.id.donateChoice).setOnClickListener(v -> openDonate());

        // Full Android TV System Settings
        findViewById(R.id.tvSettingsChoice).setOnClickListener(v ->
                openComponent("com.android.tv.settings", "com.android.tv.settings.MainSettings",
                        new Intent(Settings.ACTION_SETTINGS)));

        refresh();
        columnChoices[Math.max(0, Math.min(columns - 3, 3))].requestFocus();
    }

    private void openDonate() {
        final String url = "https://paypal.me/leonguyens";
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            // Many TV ROMs have no browser. Keep the address available for a phone.
            new AlertDialog.Builder(this)
                    .setTitle(R.string.leo_donate)
                    .setMessage(getString(R.string.leo_donate_no_browser) + "\n\n" + url)
                    .setPositiveButton(R.string.leo_copy_link, (dialog, which) -> {
                        android.content.ClipboardManager clipboard =
                                (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        if (clipboard != null) {
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("PayPal", url));
                            Toast.makeText(this, R.string.leo_link_copied, Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton(R.string.leo_close, null)
                    .show();
        }
    }

    private void refresh() {
        for (int i = 0; i < columnChoices.length; i++) {
            columnChoices[i].setSelected(columns == i + 3);
        }
        animationsChoice.setText(getString(R.string.animations_state,
                getString(animations ? R.string.on : R.string.off)));
        TextView wallpaperChoice = findViewById(R.id.wallpaperChoice);
        if (wallpaperChoice != null) {
            int count = GooglePhotosWallpaperLoader.getSlideshowFiles(this).size();
            String countSuffix = count > 0 ? (" (" + count + this.getString(R.string.leo_photo_count_suffix)) : "";
            wallpaperChoice.setText(this.getString(R.string.leo_wallpaper_album) + countSuffix);
        }
        if (appBackdropsChoice != null) {
            appBackdropsChoice.setText(this.getString(R.string.leo_backdrops_prefix) + getString(appBackdrops ? R.string.on : R.string.off));
        }
        if (slideshowTransitionChoice != null) {
            slideshowTransitionChoice.setText(this.getString(R.string.leo_transition_prefix) + this.getString(slideshowTransition.title));
        }
        if (slideshowIntervalChoice != null) {
            String intervalStr = slideshowInterval >= 60 ? (slideshowInterval / 60 + this.getString(R.string.leo_minutes_suffix)) : (slideshowInterval + this.getString(R.string.leo_seconds_suffix));
            slideshowIntervalChoice.setText(this.getString(R.string.leo_interval_prefix) + intervalStr);
        }
        followThemeChoice.setSelected(!hasOnTvChoices());
    }

    private boolean hasOnTvChoices() {
        return choices.contains("columns") || choices.contains("animations")
                || choices.contains("advanced_effects") || choices.contains("app_backdrops")
                || choices.contains("slideshow_transition") || choices.contains("slideshow_interval")
                || choices.contains("google_photos_album");
    }

    private void openComponent(String pkg, String cls, Intent fallback) {
        try {
            Intent intent = new Intent();
            intent.setClassName(pkg, cls);
            startActivity(intent);
        } catch (Exception e) {
            if (fallback != null) {
                open(fallback);
            } else {
                Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}
