package com.leotv.launcher;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.List;

/**
 * Apple TV tvOS 18 Slideshow Transition Chooser & Live Preview Dialog.
 */
public class SlideshowPreviewDialog extends Dialog {

    public interface OnAppliedListener {
        void onApplied(SlideshowTransition transition);
    }

    private final OnAppliedListener onAppliedListener;
    private final SharedPreferences choices;
    private SlideshowTransition selectedTransition;

    private ImageView previewA;
    private ImageView previewB;
    private int activeLayer = 0;
    private Bitmap sampleBmp1;
    private Bitmap sampleBmp2;

    private TextView optFade;
    private TextView optKenBurns;
    private TextView optSlide;
    private TextView optZoomFade;
    private TextView previewDescription;

    public SlideshowPreviewDialog(Context context, OnAppliedListener listener) {
        super(context);
        this.onAppliedListener = listener;
        this.choices = context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        String currentKey = choices.getString("slideshow_transition", SlideshowTransition.FADE.name());
        this.selectedTransition = SlideshowTransition.fromKey(currentKey);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_slideshow_preview);

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.82f);
            window.setGravity(Gravity.CENTER);
        }

        FrameLayout previewContainer = findViewById(R.id.previewContainer);
        if (Build.VERSION.SDK_INT >= 21 && previewContainer != null) {
            previewContainer.setClipToOutline(true);
        }

        previewA = findViewById(R.id.previewA);
        previewB = findViewById(R.id.previewB);
        previewDescription = findViewById(R.id.previewDescription);

        optFade = findViewById(R.id.optFade);
        optKenBurns = findViewById(R.id.optKenBurns);
        optSlide = findViewById(R.id.optSlide);
        optZoomFade = findViewById(R.id.optZoomFade);

        loadSampleBitmaps();

        optFade.setOnClickListener(v -> applyTransition(SlideshowTransition.FADE));
        optKenBurns.setOnClickListener(v -> applyTransition(SlideshowTransition.KEN_BURNS));
        optSlide.setOnClickListener(v -> applyTransition(SlideshowTransition.SLIDE_HORIZONTAL));
        optZoomFade.setOnClickListener(v -> applyTransition(SlideshowTransition.ZOOM_FADE));

        optFade.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) previewTransition(SlideshowTransition.FADE);
        });
        optKenBurns.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) previewTransition(SlideshowTransition.KEN_BURNS);
        });
        optSlide.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) previewTransition(SlideshowTransition.SLIDE_HORIZONTAL);
        });
        optZoomFade.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) previewTransition(SlideshowTransition.ZOOM_FADE);
        });

        findViewById(R.id.btnReplay).setOnClickListener(v -> runPreview(selectedTransition));

        updateSelectionUI();

        setOnShowListener(dialogInterface -> {
            TextView target;
            switch (selectedTransition) {
                case KEN_BURNS: target = optKenBurns; break;
                case SLIDE_HORIZONTAL: target = optSlide; break;
                case ZOOM_FADE: target = optZoomFade; break;
                default: target = optFade; break;
            }
            if (target != null) {
                target.requestFocus();
            }
            previewTransition(selectedTransition);
        });

        findViewById(R.id.btnReplay).postDelayed(() -> runPreview(selectedTransition), 300);
    }

    private void loadSampleBitmaps() {
        List<File> files = GooglePhotosWallpaperLoader.getSlideshowFiles(getContext());
        if (files != null && files.size() >= 2) {
            sampleBmp1 = decodeSample(files.get(0));
            sampleBmp2 = decodeSample(files.get(1));
        } else if (files != null && files.size() == 1) {
            sampleBmp1 = decodeSample(files.get(0));
            sampleBmp2 = sampleBmp1;
        }

        if (sampleBmp1 == null) {
            sampleBmp1 = createFallbackBitmap(0xFF1E2638);
        }
        if (sampleBmp2 == null) {
            sampleBmp2 = createFallbackBitmap(0xFF381E24);
        }

        previewA.setImageBitmap(sampleBmp1);
        previewA.setAlpha(1f);
        previewB.setAlpha(0f);
        activeLayer = 0;
    }

    private Bitmap decodeSample(File file) {
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = 2; // Preview is smaller, downsample for speed
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(file.getPath(), opts);
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    private Bitmap createFallbackBitmap(int color) {
        Bitmap b = Bitmap.createBitmap(480, 270, Bitmap.Config.RGB_565);
        b.eraseColor(color);
        return b;
    }

    private void previewTransition(SlideshowTransition transition) {
        if (transition == null) return;
        previewDescription.setText(transition.description);
        runPreview(transition);
    }

    private void applyTransition(SlideshowTransition transition) {
        if (transition == null) return;
        selectedTransition = transition;
        choices.edit().putString("slideshow_transition", selectedTransition.name()).apply();
        updateSelectionUI();
        Toast.makeText(getContext(), getContext().getString(R.string.leo_transition_saved) + getContext().getString(selectedTransition.title), Toast.LENGTH_SHORT).show();
        if (onAppliedListener != null) {
            onAppliedListener.onApplied(selectedTransition);
        }
        dismiss();
    }

    private void updateSelectionUI() {
        optFade.setSelected(selectedTransition == SlideshowTransition.FADE);
        optKenBurns.setSelected(selectedTransition == SlideshowTransition.KEN_BURNS);
        optSlide.setSelected(selectedTransition == SlideshowTransition.SLIDE_HORIZONTAL);
        optZoomFade.setSelected(selectedTransition == SlideshowTransition.ZOOM_FADE);

        optFade.setText((selectedTransition == SlideshowTransition.FADE ? "✓ " : "   ") + getContext().getString(SlideshowTransition.FADE.title));
        optKenBurns.setText((selectedTransition == SlideshowTransition.KEN_BURNS ? "✓ " : "   ") + getContext().getString(SlideshowTransition.KEN_BURNS.title));
        optSlide.setText((selectedTransition == SlideshowTransition.SLIDE_HORIZONTAL ? "✓ " : "   ") + getContext().getString(SlideshowTransition.SLIDE_HORIZONTAL.title));
        optZoomFade.setText((selectedTransition == SlideshowTransition.ZOOM_FADE ? "✓ " : "   ") + getContext().getString(SlideshowTransition.ZOOM_FADE.title));

        previewDescription.setText(selectedTransition.description);
    }

    private void runPreview(SlideshowTransition transition) {
        ImageView current = (activeLayer == 0) ? previewA : previewB;
        ImageView next = (activeLayer == 0) ? previewB : previewA;
        activeLayer = 1 - activeLayer;

        Bitmap nextBmp = (activeLayer == 1) ? sampleBmp2 : sampleBmp1;
        SlideshowAnimator.apply(current, next, nextBmp, transition, null);
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (previewA != null) previewA.animate().cancel();
        if (previewB != null) previewB.animate().cancel();
    }
}
