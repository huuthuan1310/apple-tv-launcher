package com.leotv.launcher;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * tvOS 18 Dock Platter: Lightweight, transparent frosted glass capsule.
 * Purely hardware-accelerated 2D gradient & hairline glass rim with ZERO CPU blur overhead,
 * maintaining 100% 60fps responsiveness.
 */
public class DockDecoration extends RecyclerView.ItemDecoration {

    private final float density;
    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    DockDecoration(float density) {
        this(0, density);
    }

    DockDecoration(int dockSize, float density) {
        this.density = density;
        band.setStyle(Paint.Style.FILL);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(0.75f * density);
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                               @NonNull RecyclerView.State state) {
        int pos = parent.getChildAdapterPosition(view);
        if (pos == 0) {
            outRect.top = (int) (6 * density);
            outRect.bottom = (int) (14 * density);
        } else {
            outRect.bottom = (int) (10 * density);
        }
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            int pos = parent.getChildAdapterPosition(child);
            if (pos == 0) {
                // Dock row item
                RecyclerView dockRecycler = child.findViewById(R.id.dockRecyclerView);
                if (dockRecycler != null && dockRecycler.getChildCount() > 0) {
                    float left = Float.MAX_VALUE;
                    float top = Float.MAX_VALUE;
                    float right = Float.MIN_VALUE;
                    float bottom = Float.MIN_VALUE;

                    for (int j = 0; j < dockRecycler.getChildCount(); j++) {
                        View tile = dockRecycler.getChildAt(j);
                        View card = tile.findViewById(R.id.card);
                        if (card == null) card = tile;

                        float l = child.getLeft() + dockRecycler.getLeft() + tile.getLeft() + card.getLeft();
                        float t = child.getTop() + dockRecycler.getTop() + tile.getTop() + card.getTop() + child.getTranslationY();
                        float r = l + card.getWidth();
                        float b = t + card.getHeight();

                        left = Math.min(left, l);
                        top = Math.min(top, t);
                        right = Math.max(right, r);
                        bottom = Math.max(bottom, b);
                    }

                    if (left < right && top < bottom) {
                        float padY = (bottom - top) * (AppAdapter.FOCUS_SCALE - 1f) / 2f + 12 * density;

                        float minLeft = parent.getPaddingLeft();
                        float maxRight = parent.getWidth() - parent.getPaddingRight();
                        left = minLeft;
                        right = maxRight;

                        rect.set(left, top - padY, right, bottom + padY);
                        float radius = 30 * density;

                        // Glass effect: Pure translucent frosted glass tint with top specular light sheen
                        // Top: 0x33FFFFFF (~20% white highlight), Bottom: 0x14FFFFFF (~8% white sheen)
                        // Absolutely NO black/dark tone (100% transparent wallpaper pass-through)
                        LinearGradient glassShader = new LinearGradient(
                                rect.left, rect.top, rect.left, rect.bottom,
                                0x33FFFFFF, 0x14FFFFFF, Shader.TileMode.CLAMP);
                        band.setShader(glassShader);
                        c.drawRoundRect(rect, radius, radius, band);

                        // Fine glass rim catching light (0x48FFFFFF at top, 0x1AFFFFFF at bottom)
                        LinearGradient edgeShader = new LinearGradient(
                                rect.left, rect.top, rect.left, rect.bottom,
                                0x48FFFFFF, 0x1AFFFFFF, Shader.TileMode.CLAMP);
                        edge.setShader(edgeShader);
                        c.drawRoundRect(rect, radius, radius, edge);
                    }
                }
                break;
            }
        }
    }
}
