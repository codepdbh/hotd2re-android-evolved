package com.codepdbh.hotd2evolved;

import android.content.res.ColorStateList;
import android.graphics.Insets;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.libsdl.app.SDLActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class GameActivity extends SDLActivity {
    static final String EXTRA_VIEWPORT = "viewport";
    static final String EXTRA_RENDER_HEIGHT = "render_height";
    static final String EXTRA_GRAPHICS_MODE = "graphics_mode";
    static final String EXTRA_AUDIO = "audio";
    static final String EXTRA_ONLINE = "online";
    static final String EXTRA_DRIVER_DIRECTORY = "vulkan_driver_directory";
    static final String EXTRA_DRIVER_LIBRARY = "vulkan_driver_library";

    // Shared with android_controls.h.
    private static final int CONTROL_CONTINUE = 1;
    private static final int CONTROL_RELOAD = 2;
    private static final int CONTROL_ESCAPE = 4;

    private LinearLayout controlsRow;
    private ImageButton continueButton;
    private ImageButton reloadButton;
    private ImageButton escapeButton;
    private int availableControls;
    private boolean continueVolumeHeld;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (isFinishing()) return;
        getWindow().getDecorView().post(this::hideSystemBars);
        controlsRow = new LinearLayout(this);
        controlsRow.setVisibility(View.GONE);
        escapeButton = addControl("Pausar", CONTROL_ESCAPE, R.drawable.ic_game_pause, 0xFFF1DDD2);
        reloadButton = addControl("Recargar", CONTROL_RELOAD, R.drawable.ic_game_reload, 0xFFF4D699);
        continueButton = addControl("Continuar", CONTROL_CONTINUE, R.drawable.ic_game_continue, 0xFFFFB9AC);
        int margin = dp(16);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.RIGHT | Gravity.BOTTOM);
        params.rightMargin = params.bottomMargin = margin;
        addContentView(controlsRow, params);
        controlsRow.setOnApplyWindowInsetsListener((view, insets) -> {
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
    public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused && !isFinishing()) hideSystemBars();
    }

    private void hideSystemBars() {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.hide(WindowInsets.Type.systemBars());
            }
        } else {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    private int dp(int size) {
        return Math.round(size * getResources().getDisplayMetrics().density);
    }

    private ImageButton addControl(String description, int control, int icon, int accent) {
        ImageButton button = new ImageButton(this);
        button.setContentDescription(description);
        button.setImageResource(icon);
        button.setImageTintList(ColorStateList.valueOf(accent));
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setBackgroundTintList(null);
        GradientDrawable panel = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xE52B2220, 0xF2151110});
        panel.setCornerRadius(dp(10));
        panel.setStroke(dp(1), (accent & 0x00FFFFFF) | 0x89000000);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x38FFFFFF), panel, null));
        button.setPadding(0, 0, 0, 0);
        button.setElevation(dp(3));
        button.setFocusable(false);
        button.setVisibility(View.GONE);
        button.setOnClickListener(view -> nativeRequestControl(control));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(48), dp(48));
        params.setMarginStart(dp(8));
        controlsRow.addView(button, params);
        return button;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP) {
            if (event.getAction() == KeyEvent.ACTION_UP && continueVolumeHeld) {
                continueVolumeHeld = false;
                return true;
            }
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() > 0 && continueVolumeHeld) return true;
                continueVolumeHeld = false;
                if (event.getRepeatCount() == 0 && (availableControls & CONTROL_CONTINUE) != 0 && hasWindowFocus()) {
                    continueVolumeHeld = true;
                    nativeRequestControl(CONTROL_CONTINUE);
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    // Called by the SDL thread when the available actions change.
    public void setAvailableControls(int controls) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed() || controlsRow == null) return;
            availableControls = controls;
            boolean escapeAvailable = (controls & CONTROL_ESCAPE) != 0;
            boolean reloadAvailable = (controls & CONTROL_RELOAD) != 0;
            if (escapeAvailable) {
                escapeButton.setImageResource(reloadAvailable ? R.drawable.ic_game_pause : R.drawable.ic_game_continue);
                escapeButton.setContentDescription(reloadAvailable ? "Pausar" : "Reanudar");
            }
            escapeButton.setVisibility(escapeAvailable ? View.VISIBLE : View.GONE);
            // Leave Esc in the same place when it changes from pause to resume.
            int reloadHidden = escapeAvailable ? View.INVISIBLE : View.GONE;
            reloadButton.setVisibility(reloadAvailable ? View.VISIBLE : reloadHidden);
            continueButton.setVisibility((controls & CONTROL_CONTINUE) != 0 ? View.VISIBLE : View.GONE);
            controlsRow.setVisibility(controls != 0 ? View.VISIBLE : View.GONE);
            if (controls != 0) controlsRow.requestApplyInsets();
        });
    }

    @Override
    protected String[] getArguments() {
        String format = getIntent().getStringExtra(EXTRA_VIEWPORT);
        if (format == null) format = "4:3";
        List<String> args = new ArrayList<>(Arrays.asList("game", "--renderer", "sdlgpu", "--input", "touch",
                "--no-update-check", "--viewport", format, "--android-render-height",
                Integer.toString(getIntent().getIntExtra(EXTRA_RENDER_HEIGHT, 480))));
        String graphicsMode = getIntent().getStringExtra(EXTRA_GRAPHICS_MODE);
        if ("pc".equals(graphicsMode) || "naomi".equals(graphicsMode)) {
            args.add("--graphics-mode");
            args.add(graphicsMode);
        }
        if (getIntent().getBooleanExtra(EXTRA_AUDIO, true)) args.add("--audio");
        if (getIntent().getBooleanExtra(EXTRA_ONLINE, false)) args.add("--online");
        String driverDirectory = getIntent().getStringExtra(EXTRA_DRIVER_DIRECTORY);
        String driverLibrary = getIntent().getStringExtra(EXTRA_DRIVER_LIBRARY);
        if (driverDirectory != null && driverLibrary != null) {
            // Consumed by android_entry.cpp before the engine parses its arguments.
            args.addAll(Arrays.asList("--vulkan-driver-dir", driverDirectory, "--vulkan-driver-lib", driverLibrary,
                    "--native-lib-dir", getApplicationInfo().nativeLibraryDir));
        }
        return args.toArray(new String[0]);
    }

    private static native void nativeRequestControl(int control);
}
