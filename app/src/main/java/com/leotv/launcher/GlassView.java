package com.leotv.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * Authentic Apple TV (tvOS 18) Active Focus Sheen:
 * - Pure 3D liquid glass specular reflection across the upper surface ("sáng loáng")
 * - Zero stroke border (clean Apple TV floating card aesthetic)
 */
public class GlassView extends View {

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint specular = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;
    private float radius;

    public GlassView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        radius = 18f * density;

        setFocusable(false);
        setClickable(false);
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    void setAccent(int argb) {
        invalidate();
    }

    void setRadius(float px) {
        radius = px;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) return;

        // Subtle crystal glass body reflection
        fill.setShader(new LinearGradient(
                0, 0, 0, h,
                new int[]{0x22FFFFFF, 0x05FFFFFF},
                new float[]{0.0f, 1.0f},
                Shader.TileMode.CLAMP));

        // Brilliant upper specular sheen: liquid glass reflection across top 45%
        specular.setShader(new LinearGradient(
                0, 0, 0, h * 0.45f,
                new int[]{0x85FFFFFF, 0x00FFFFFF},
                new float[]{0.0f, 1.0f},
                Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float inset = 5f * density;
        rect.set(inset, inset, getWidth() - inset, getHeight() - inset);

        // 1. Crystal Translucent Fill
        canvas.drawRoundRect(rect, radius, radius, fill);

        // 2. Upper Specular Sheen (authentic liquid glass gloss, zero border)
        canvas.save();
        canvas.clipRect(rect.left, rect.top, rect.right, rect.top + rect.height() * 0.45f);
        canvas.drawRoundRect(rect, radius, radius, specular);
        canvas.restore();
    }
}
