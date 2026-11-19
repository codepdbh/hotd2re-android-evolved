package com.codepdbh.hotd2evolved;

import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.FrameLayout;

import org.libsdl.app.SDLActivity;

public final class GameActivity extends SDLActivity {
    static final String EXTRA_VIEWPORT = "viewport";

    private Button continueButton;
    private boolean continueAvailable;
    private boolean continueVolumeHeld;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (isFinishing()) return;
        continueButton = new Button(this);
        continueButton.setText("Continuar");
        continueButton.setVisibility(View.GONE);
        continueButton.setFocusable(false);
        continueButton.setOnClickListener(view -> nativeRequestContinue());
        int margin = Math.round(24 * getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.RIGHT | Gravity.BOTTOM);
        params.rightMargin = params.bottomMargin = margin;
        addContentView(continueButton, params);
        continueButton.setOnApplyWindowInsetsListener((view, insets) -> {
            Insets safe = Build.VERSION.SDK_INT >= 30
                    ? insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout())
                    : Insets.of(0, 0, insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            if (params.rightMargin != margin + safe.right || params.bottomMargin != margin + safe.bottom) {
                params.rightMargin = margin + safe.right;
                params.bottomMargin = margin + safe.bottom;
                view.setLayoutParams(params);
            }
            return insets;
        });
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP) {
            if (event.getAction() == KeyEvent.ACTION_UP && continueVolumeHeld) {
                continueVolumeHeld = false;
                return true;
            }
            if (event.getAction() == KeyEvent.ACTION_DOWN && (continueVolumeHeld || continueAvailable && hasWindowFocus())) {
                if (!continueVolumeHeld && event.getRepeatCount() == 0) nativeRequestContinue();
                continueVolumeHeld = true;
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    // Called by the SDL thread only when the native continue state changes.
    public void setContinueAvailable(boolean available) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed() || continueButton == null) return;
            continueAvailable = available;
            continueButton.setVisibility(available ? View.VISIBLE : View.GONE);
            if (available) continueButton.requestApplyInsets();
        });
    }

    @Override
    protected String[] getArguments() {
        String format = getIntent().getStringExtra(EXTRA_VIEWPORT);
        if (format == null) format = "4:3";
        return new String[]{"game", "--audio", "--renderer", "sdlgpu", "--input", "touch", "--no-update-check",
                "--viewport", format};
    }

    private static native void nativeRequestContinue();
}
