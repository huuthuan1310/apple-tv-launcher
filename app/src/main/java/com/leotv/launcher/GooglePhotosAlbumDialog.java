package com.leotv.launcher;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Apple TV tvOS 18 Google Photos Album Chooser & Validator Dialog.
 * Allows changing album link, validates Google Photos domain, ensures album is public
 * and contains downloadable photos before saving into preferences.
 */
public class GooglePhotosAlbumDialog extends Dialog {

    private final Runnable onSaved;
    private EditText editAlbumUrl;
    private TextView btnPasteUrl;
    private TextView btnClearUrl;
    private ProgressBar albumProgressBar;
    private TextView albumStatusMessage;
    private TextView btnSaveAlbum;
    private TextView btnSyncNow;
    private TextView btnCloseAlbum;

    public GooglePhotosAlbumDialog(Context context, Runnable onSaved) {
        super(context);
        this.onSaved = onSaved;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_google_photos_album);
        Typography.apply(getWindow().getDecorView());

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.82f);
        }

        editAlbumUrl = findViewById(R.id.editAlbumUrl);
        btnPasteUrl = findViewById(R.id.btnPasteUrl);
        btnClearUrl = findViewById(R.id.btnClearUrl);
        albumProgressBar = findViewById(R.id.albumProgressBar);
        albumStatusMessage = findViewById(R.id.albumStatusMessage);
        btnSaveAlbum = findViewById(R.id.btnSaveAlbum);
        btnSyncNow = findViewById(R.id.btnSyncNow);
        btnCloseAlbum = findViewById(R.id.btnCloseAlbum);

        Config cfg = Config.load(getContext());
        if (cfg.googlePhotosAlbum != null && !cfg.googlePhotosAlbum.trim().isEmpty()) {
            editAlbumUrl.setText(cfg.googlePhotosAlbum.trim());
            editAlbumUrl.setSelection(editAlbumUrl.getText().length());
        }

        btnPasteUrl.setOnClickListener(v -> handlePaste());
        btnClearUrl.setOnClickListener(v -> {
            editAlbumUrl.setText("");
            setStatus(getContext().getString(R.string.leo_album_cleared), 0x88FFFFFF, false);
            editAlbumUrl.requestFocus();
        });

        btnSaveAlbum.setOnClickListener(v -> startValidationAndSave(false));
        btnSyncNow.setOnClickListener(v -> startValidationAndSave(true));
        btnCloseAlbum.setOnClickListener(v -> dismiss());

        setOnShowListener(dialogInterface -> {
            if (editAlbumUrl != null) {
                editAlbumUrl.requestFocus();
            }
        });
    }

    private void handlePaste() {
        try {
            ClipboardManager cm = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
                ClipData.Item item = cm.getPrimaryClip().getItemAt(0);
                CharSequence text = item != null ? item.getText() : null;
                if (text != null && text.length() > 0) {
                    String pasted = text.toString().trim();
                    editAlbumUrl.setText(pasted);
                    editAlbumUrl.setSelection(editAlbumUrl.getText().length());
                    setStatus(getContext().getString(R.string.leo_album_pasted), 0xFF34C759, false);
                    btnSaveAlbum.requestFocus();
                    return;
                }
            }
            setStatus(getContext().getString(R.string.leo_clipboard_empty), 0xFFFF9500, false);
        } catch (Exception e) {
            setStatus(getContext().getString(R.string.leo_clipboard_failed) + e.getMessage(), 0xFFFF453A, false);
        }
    }

    private void setStatus(String message, int textColor, boolean showLoading) {
        if (albumStatusMessage != null) {
            albumStatusMessage.setText(message);
            albumStatusMessage.setTextColor(textColor);
        }
        if (albumProgressBar != null) {
            albumProgressBar.setVisibility(showLoading ? View.VISIBLE : View.GONE);
        }
    }

    private void setControlsEnabled(boolean enabled) {
        if (btnSaveAlbum != null) {
            btnSaveAlbum.setEnabled(enabled);
            btnSaveAlbum.setAlpha(enabled ? 1f : 0.5f);
        }
        if (btnSyncNow != null) {
            btnSyncNow.setEnabled(enabled);
            btnSyncNow.setAlpha(enabled ? 1f : 0.5f);
        }
        if (btnPasteUrl != null) btnPasteUrl.setEnabled(enabled);
        if (btnClearUrl != null) btnClearUrl.setEnabled(enabled);
        if (editAlbumUrl != null) editAlbumUrl.setEnabled(enabled);
    }

    private void startValidationAndSave(boolean isSyncOnly) {
        String input = editAlbumUrl.getText().toString().trim();
        if (input.isEmpty()) {
            setStatus(getContext().getString(R.string.leo_album_input_error), 0xFFFF453A, false);
            editAlbumUrl.requestFocus();
            return;
        }

        String normalized = GooglePhotosWallpaperLoader.normalizeUrl(input);
        if (!GooglePhotosWallpaperLoader.isValidGooglePhotosDomain(normalized)) {
            setStatus(getContext().getString(R.string.leo_album_domain_error), 0xFFFF453A, false);
            return;
        }

        setControlsEnabled(false);
        setStatus(getContext().getString(R.string.leo_album_checking), 0xFFFFFFFF, true);

        GooglePhotosWallpaperLoader.validateAndSyncAlbum(getContext(), normalized, new GooglePhotosWallpaperLoader.ValidateCallback() {
            @Override
            public void onSuccess(String normalizedUrl, int photoCount) {
                setControlsEnabled(true);
                String successMsg = getContext().getString(R.string.leo_album_success_prefix) + photoCount + getContext().getString(R.string.leo_album_success_suffix);
                setStatus(successMsg, 0xFF34C759, false);
                Toast.makeText(getContext(), getContext().getString(R.string.leo_album_updated_prefix) + photoCount + getContext().getString(R.string.leo_album_updated_suffix), Toast.LENGTH_LONG).show();

                if (onSaved != null) {
                    onSaved.run();
                }

                // Smoothly dismiss dialog after short success glance
                if (btnSaveAlbum != null) {
                    btnSaveAlbum.postDelayed(GooglePhotosAlbumDialog.this::dismiss, 1000);
                } else {
                    dismiss();
                }
            }

            @Override
            public void onError(String errorMessage) {
                setControlsEnabled(true);
                setStatus(getContext().getString(R.string.leo_album_save_failed) + errorMessage, 0xFFFF453A, false);
                Toast.makeText(getContext(), getContext().getString(R.string.leo_album_error) + errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }
}
