package com.leotv.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;

/** Event-only Home redirect for Xiaomi firmware. Never filters keys or reads window contents. */
public class HomeKeyService extends AccessibilityService {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable redirect = new Runnable() {
        @Override public void run() {
            try {
                startActivity(new Intent(HomeKeyService.this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
                android.util.Log.i("LeoHome", "Redirected Leanback to Leo TV");
            } catch (Exception e) {
                android.util.Log.e("LeoHome", "Home redirect failed", e);
            }
        }
    };
    @Override protected void onServiceConnected() {
        android.util.Log.i("LeoHome", "Home helper connected");
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        handler.removeCallbacks(redirect);
        if ("com.google.android.leanbacklauncher".contentEquals(event.getPackageName() == null ? "" : event.getPackageName())) {
            // Debounce Xiaomi's repeated Home launches instead of dropping the second event.
            handler.postDelayed(redirect, 250);
        }
    }
    @Override public void onInterrupt() { handler.removeCallbacks(redirect); }
    @Override public void onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy(); }
}
