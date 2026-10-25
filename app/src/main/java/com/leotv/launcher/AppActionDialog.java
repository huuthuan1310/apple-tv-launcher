package com.leotv.launcher;

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Apple TV tvOS 18 Quick Actions dialog when long-pressing an app tile.
 * Allows pinning/unpinning from the top Dock, viewing app details, or uninstalling.
 */
public class AppActionDialog extends Dialog {

    private boolean centerPressed;
    private final AppAdapter.Entry entry;
    private final Runnable onModified;
    private final Runnable onMove;

    public AppActionDialog(Context context, AppAdapter.Entry entry, Runnable onModified, Runnable onMove) {
        super(context);
        this.entry = entry;
        this.onModified = onModified;
        this.onMove = onMove;
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        int key = event.getKeyCode();
        if (key == android.view.KeyEvent.KEYCODE_DPAD_CENTER || key == android.view.KeyEvent.KEYCODE_ENTER) {
            if (event.getAction() == android.view.KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) centerPressed = true;
            if (event.getAction() == android.view.KeyEvent.ACTION_UP) {
                if (!centerPressed) return true; // Release from the hold that opened this menu.
                centerPressed = false;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override protected void onStart() {
        super.onStart();
        if (getWindow() != null) {
            android.util.DisplayMetrics metrics=getContext().getResources().getDisplayMetrics();
            int width=Math.min(Math.round(440*metrics.density),metrics.widthPixels-Math.round(48*metrics.density));
            getWindow().setLayout(width,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            getWindow().setGravity(android.view.Gravity.CENTER);
        }
        findViewById(R.id.btnMove).requestFocus();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_app_action);

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.8f);
        }

        ImageView appIcon = findViewById(R.id.actionAppIcon);
        TextView appName = findViewById(R.id.actionAppName);
        TextView appStatus = findViewById(R.id.actionAppStatus);
        TextView btnMove = findViewById(R.id.btnMove);
        TextView btnToggleDock = findViewById(R.id.btnToggleDock);
        TextView btnAppDetails = findViewById(R.id.btnAppDetails);
        TextView btnUninstall = findViewById(R.id.btnUninstall);
        TextView btnDismiss = findViewById(R.id.btnDismiss);

        if (entry.banner != null) {
            appIcon.setImageDrawable(entry.banner);
        } else if (entry.icon != null) {
            appIcon.setImageDrawable(entry.icon);
        }

        appName.setText(entry.label);

        java.util.List<String> dockList = Config.getDockApps(getContext());
        int capacity=Config.load(getContext()).columns;
        if (dockList.size()>capacity) dockList=new java.util.ArrayList<>(dockList.subList(0,capacity));
        final java.util.List<String> visibleDock=dockList;
        int dockIndex = dockList.indexOf(entry.pkg);
        boolean isDocked = dockIndex >= 0;

        appStatus.setText(isDocked ? getContext().getString(R.string.leo_dock_app_status) : getContext().getString(R.string.leo_home_app_status));
        btnToggleDock.setText(isDocked ? getContext().getString(R.string.leo_remove_dock) : getContext().getString(R.string.leo_pin_dock_short));
        btnMove.setOnClickListener(v -> {
            dismiss();
            if (onMove != null) onMove.run();
        });

        btnToggleDock.setOnClickListener(v -> {
            if (isDocked) {
                Config.removeDockApp(getContext(), entry.pkg);
                Toast.makeText(getContext(), getContext().getString(R.string.leo_removed_prefix) + entry.label + getContext().getString(R.string.leo_removed_suffix), Toast.LENGTH_SHORT).show();
            } else {
                if (visibleDock.size() >= capacity) {
                    Toast.makeText(getContext(), getContext().getString(R.string.leo_dock_full_prefix) + capacity + getContext().getString(R.string.leo_dock_full_suffix), Toast.LENGTH_LONG).show();
                    return;
                }
                Config.addDockApp(getContext(), entry.pkg);
                Toast.makeText(getContext(), getContext().getString(R.string.leo_pinned_prefix) + entry.label + getContext().getString(R.string.leo_pinned_suffix), Toast.LENGTH_SHORT).show();
            }
            if (onModified != null) onModified.run();
            dismiss();
        });

        btnAppDetails.setOnClickListener(v -> {
            try {
                Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + entry.pkg));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(i);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(getContext(), getContext().getString(R.string.leo_app_settings_missing), Toast.LENGTH_SHORT).show();
            }
            dismiss();
        });

        // Hide uninstall option for system apps
        boolean isSystemApp = false;
        try {
            ApplicationInfo ai = getContext().getPackageManager().getApplicationInfo(entry.pkg, 0);
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                isSystemApp = true;
            }
        } catch (Exception ignored) {
        }

        if (isSystemApp) {
            btnUninstall.setVisibility(View.GONE);
        } else {
            btnUninstall.setOnClickListener(v -> {
                try {
                    Intent i = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + entry.pkg));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    getContext().startActivity(i);
                } catch (Exception e) {
                    Toast.makeText(getContext(), getContext().getString(R.string.leo_uninstall_failed), Toast.LENGTH_SHORT).show();
                }
                dismiss();
            });
        }

        btnDismiss.setOnClickListener(v -> dismiss());

        Typography.apply(getWindow().getDecorView());
        btnMove.requestFocus();
    }
}
