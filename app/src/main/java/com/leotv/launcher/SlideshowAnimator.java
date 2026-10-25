package com.leotv.launcher;

import android.graphics.Bitmap;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;

/**
 * Handles smooth cinematic wallpaper transitions for Apple TV slideshow mode.
 */
public final class SlideshowAnimator {

    private SlideshowAnimator() {}

    public static void apply(ImageView currentView, ImageView nextView, Bitmap nextBmp,
                             SlideshowTransition transition, Runnable onEnd) {
        if (currentView == null || nextView == null || nextBmp == null) {
            if (onEnd != null) onEnd.run();
            return;
        }

        currentView.animate().cancel();
        nextView.animate().cancel();

        switch (transition) {
            case KEN_BURNS:
                nextView.setImageBitmap(nextBmp);
                nextView.setAlpha(0f);
                nextView.setScaleX(1.02f);
                nextView.setScaleY(1.02f);
                nextView.setTranslationX(0f);
                nextView.setTranslationY(0f);

                nextView.animate()
                        .alpha(1f)
                        .scaleX(1.10f)
                        .scaleY(1.10f)
                        .translationX(14f * nextView.getResources().getDisplayMetrics().density)
                        .setDuration(4000)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();

                currentView.animate()
                        .alpha(0f)
                        .setDuration(1400)
                        .withEndAction(() -> {
                            currentView.setScaleX(1f);
                            currentView.setScaleY(1f);
                            currentView.setTranslationX(0f);
                            if (onEnd != null) onEnd.run();
                        }).start();
                break;

            case SLIDE_HORIZONTAL:
                int width = nextView.getWidth();
                if (width <= 0) width = nextView.getResources().getDisplayMetrics().widthPixels;
                nextView.setImageBitmap(nextBmp);
                nextView.setTranslationX(width);
                nextView.setTranslationY(0f);
                nextView.setScaleX(1f);
                nextView.setScaleY(1f);
                nextView.setAlpha(1f);

                currentView.animate()
                        .translationX(-width)
                        .setDuration(850)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();

                nextView.animate()
                        .translationX(0f)
                        .setDuration(850)
                        .setInterpolator(new DecelerateInterpolator())
                        .withEndAction(() -> {
                            currentView.setAlpha(0f);
                            currentView.setTranslationX(0f);
                            if (onEnd != null) onEnd.run();
                        }).start();
                break;

            case ZOOM_FADE:
                nextView.setImageBitmap(nextBmp);
                nextView.setAlpha(0f);
                nextView.setScaleX(1.10f);
                nextView.setScaleY(1.10f);
                nextView.setTranslationX(0f);
                nextView.setTranslationY(0f);

                currentView.animate()
                        .scaleX(0.96f)
                        .scaleY(0.96f)
                        .alpha(0f)
                        .setDuration(1100)
                        .start();

                nextView.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .alpha(1f)
                        .setDuration(1100)
                        .withEndAction(() -> {
                            currentView.setScaleX(1f);
                            currentView.setScaleY(1f);
                            if (onEnd != null) onEnd.run();
                        }).start();
                break;

            case FADE:
            default:
                nextView.setImageBitmap(nextBmp);
                nextView.setAlpha(0f);
                nextView.setScaleX(1f);
                nextView.setScaleY(1f);
                nextView.setTranslationX(0f);
                nextView.setTranslationY(0f);

                nextView.animate()
                        .alpha(1f)
                        .setDuration(1200)
                        .withEndAction(() -> {
                            currentView.setAlpha(0f);
                            if (onEnd != null) onEnd.run();
                        }).start();
                break;
        }
    }
}
