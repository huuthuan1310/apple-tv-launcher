package com.leotv.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/** Settings tile using Apple's original Apple TV icon artwork, not a hand-drawn wordmark.
 * Source: https://marketing.services.apple/apple-tv-identity-guidelines
 */
public final class AppleTvBrandView extends View {
    private final Paint tile = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint logo = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Bitmap artwork;
    private final RectF bounds = new RectF();
    private final RectF logoBounds = new RectF();

    public AppleTvBrandView(Context context, AttributeSet attributes) {
        super(context, attributes);
        artwork = BitmapFactory.decodeResource(getResources(), R.drawable.official_apple_tv_icon);
        // Use the white wordmark as a mask; discard the source icon's dark tile.
        // Keep Apple's original proportions while matching the supplied settings tile's palette.
        logo.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(new float[]{
                0,0,0,0,28,
                0,0,0,0,38,
                0,0,0,0,57,
                1.5f,0,0,0,-127.5f
        })));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        float side = Math.min(w, h);
        bounds.set((w - side) / 2f, (h - side) / 2f, (w + side) / 2f, (h + side) / 2f);
        tile.setShader(new LinearGradient(0, bounds.top, 0, bounds.bottom,
                0xFF566872, 0xFF575B68, Shader.TileMode.CLAMP));
        float size = side * 0.60f;
        logoBounds.set((w-size)/2f, (h-size)/2f, (w+size)/2f, (h+size)/2f);
    }

    @Override protected void onDraw(Canvas canvas) {
        canvas.drawRoundRect(bounds, bounds.width() * 0.22f, bounds.height() * 0.22f, tile);
        canvas.drawBitmap(artwork, null, logoBounds, logo);
    }
}
