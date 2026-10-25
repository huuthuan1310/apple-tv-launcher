package com.leotv.launcher;

import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/** Bundled static Inter faces also work on the MiBOX's Android 5 font renderer. */
final class Typography {
    private static Typeface regular;
    private static Typeface medium;
    private Typography() {}
    static void apply(View view) {
        if (regular == null) {
            regular = Typeface.createFromAsset(view.getContext().getAssets(), "fonts/Inter-Regular.ttf");
            medium = Typeface.createFromAsset(view.getContext().getAssets(), "fonts/Inter-Medium.ttf");
        }
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            boolean emphasized = text.isFocusable() || (text.getTypeface() != null && text.getTypeface().isBold())
                    || text.getTextSize() / text.getResources().getDisplayMetrics().scaledDensity >= 19f;
            text.setTypeface(emphasized ? medium : regular);
            text.setLetterSpacing(-0.015f);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) apply(group.getChildAt(i));
        }
    }
}
