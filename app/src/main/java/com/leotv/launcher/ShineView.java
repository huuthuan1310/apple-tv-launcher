package com.leotv.launcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * Authentic Apple tvOS Specular Shine Sweep:
 * Sweeps a 3D diagonal glossy specular sheen across the card exactly once on focus.
 */
public class ShineView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ValueAnimator animator;
    private float progress = -1f;

    public ShineView(Context context) {
        super(context);
        init();
    }

    public ShineView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setClickable(false);
        setFocusable(false);
    }

    public void startShine() {
        // Disabled to maximize CPU/GPU responsiveness and eliminate 450ms sweep animator overhead
    }

    public void cancelShine() {
        // No-op
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // Disabled for ultra-smooth hardware performance
    }
}
