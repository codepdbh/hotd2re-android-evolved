package com.codepdbh.hotd2evolved;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DataActivity extends Activity {
    private static final int PICK_DATA = 1;
    private static final String SCENE = "scenes/st1_play.scene";
    private static final String VIEWPORT_SETTING = "display_format";
    private static final String AUDIO_SETTING = "game_audio";
    private static final String LANGUAGE_PREFS = "launcher_language";
    private static final String LANGUAGE_SETTING = "language";
    private static final String[] LANGUAGES = {"es", "en"};
    private static final String[] VIEWPORTS = {"4:3", "16:9", "window"};
    private static final int TEXT = 0xFFF8EDE2;
    private static final int MUTED = 0xFFC7B9AF;
    private static final int GOLD = 0xFFE4BC85;
    private static final int EDGE = 0xFF54403B;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Path files;
    private TextView status;
    private Button select;
    private Button play;
    private Button online;
    private Spinner displayFormat;
    private Spinner language;
    private Switch audio;
    private int languageIndex;

    @Override
    protected void attachBaseContext(Context base) {
        String saved = base.getSharedPreferences(LANGUAGE_PREFS, MODE_PRIVATE).getString(LANGUAGE_SETTING, "es");
        languageIndex = "en".equals(saved) ? 1 : 0;
        Configuration localized = new Configuration(base.getResources().getConfiguration());
        localized.setLocale(Locale.forLanguageTag(LANGUAGES[languageIndex]));
        super.attachBaseContext(base.createConfigurationContext(localized));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        files = getFilesDir().toPath();
        SharedPreferences settings = getPreferences(MODE_PRIVATE);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF271C1C, 0xFF131117, 0xFF0C0B10}));
        FrameLayout container = new FrameLayout(this);
        scroll.addView(container, new ScrollView.LayoutParams(-1, -2));
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(0, dp(26), 0, dp(24));
        FrameLayout.LayoutParams centered = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        container.addView(panel, centered);
        container.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int width = Math.min(dp(520), right - left - view.getPaddingLeft() - view.getPaddingRight());
            if (width > 0 && centered.width != width) {
                centered.width = width;
                panel.setLayoutParams(centered);
            }
        });
        int gutter = dp(22);
        container.setPadding(gutter, 0, gutter, 0);
        container.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(gutter + safe.left, safe.top, gutter + safe.right, safe.bottom);
            }
            return insets;
        });

        ImageView artwork = new ImageView(this);
        artwork.setImageResource(R.drawable.launcher_art);
        artwork.setScaleType(ImageView.ScaleType.FIT_CENTER);
        artwork.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams image = new LinearLayout.LayoutParams(dp(138), dp(138));
        image.gravity = Gravity.CENTER_HORIZONTAL;
        panel.addView(artwork, image);
        TextView title = text("The House of the Dead 2", 28, TEXT);
        title.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        title.setGravity(Gravity.CENTER);
        addRow(panel, title, 12);
        TextView edition = text("ANDROID EVOLVED", 12, GOLD);
        edition.setLetterSpacing(0.22f);
        edition.setTypeface(Typeface.DEFAULT_BOLD);
        edition.setGravity(Gravity.CENTER);
        addRow(panel, edition, 4);

        LinearLayout actions = new LinearLayout(this);
        play = action(getString(R.string.launcher_play), R.drawable.ic_game_continue, true);
        online = action(getString(R.string.launcher_online), R.drawable.ic_launcher_online, false);
        play.setOnClickListener(view -> launch(false));
        online.setOnClickListener(view -> launch(true));
        actions.addView(play, new LinearLayout.LayoutParams(0, dp(54), 1.2f));
        LinearLayout.LayoutParams onlineParams = new LinearLayout.LayoutParams(0, dp(54), 1);
        onlineParams.setMarginStart(dp(10));
        actions.addView(online, onlineParams);
        addRow(panel, actions, 22);

        LinearLayout options = card();
        TextView heading = text(getString(R.string.launcher_settings), 18, TEXT);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        options.addView(heading);

        language = addSelector(options, R.string.launcher_language, new String[]{"Español", "English"});
        language.setSelection(languageIndex);
        language.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (languageIndex == position) return;
                languageIndex = position;
                getSharedPreferences(LANGUAGE_PREFS, MODE_PRIVATE).edit().putString(LANGUAGE_SETTING, LANGUAGES[position]).apply();
                recreate();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        displayFormat = addSelector(options, R.string.launcher_format, getResources().getStringArray(R.array.launcher_formats));
        int savedFormat = settings.getInt(VIEWPORT_SETTING, 0);
        displayFormat.setSelection(savedFormat >= 0 && savedFormat < VIEWPORTS.length ? savedFormat : 0);
        displayFormat.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                settings.edit().putInt(VIEWPORT_SETTING, position).apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        View divider = new View(this);
        divider.setBackgroundColor(0xFF493633);
        options.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        audio = new Switch(this);
        audio.setText(R.string.launcher_sound);
        audio.setTextColor(TEXT);
        audio.setTextSize(15);
        audio.setMinHeight(dp(48));
        audio.setChecked(settings.getBoolean(AUDIO_SETTING, true));
        audio.setThumbTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[0]}, new int[]{GOLD, MUTED}));
        audio.setOnCheckedChangeListener((view, enabled) -> settings.edit().putBoolean(AUDIO_SETTING, enabled).apply());
        options.addView(audio, new LinearLayout.LayoutParams(-1, -2));
        Button report = action(getString(R.string.report_option), 0, false);
        report.setMinHeight(dp(48));
        report.setOnClickListener(view -> reviewReport());
        addRow(options, report, 7);
        addRow(panel, options, 16);

        LinearLayout data = card();
        TextView dataTitle = text(getString(R.string.launcher_data), 16, TEXT);
        dataTitle.setTypeface(Typeface.DEFAULT_BOLD);
        data.addView(dataTitle);
        status = text("", 14, MUTED);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        addRow(data, status, 5);
        select = action(getString(R.string.launcher_import), 0, false);
        select.setMinHeight(dp(48));
        select.setOnClickListener(view -> pick());
        addRow(data, select, 12);
        addRow(panel, data, 12);

        Button help = action(getString(R.string.launcher_online_help), 0, false);
        help.setMinHeight(dp(48));
        help.setOnClickListener(view -> new AlertDialog.Builder(this)
                .setTitle(R.string.launcher_code_title)
                .setMessage(R.string.launcher_code_help)
                .setPositiveButton(R.string.launcher_ok, null).show());
        addRow(panel, help, 12);
        setContentView(scroll);
        updateButtons();
    }

    private int dp(int size) {
        return Math.round(size * getResources().getDisplayMetrics().density);
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private Spinner addSelector(LinearLayout panel, int labelRes, String[] choices) {
        Spinner selector = new Spinner(this);
        selector.setId(View.generateViewId());
        selector.setBackgroundTintList(ColorStateList.valueOf(GOLD));
        TextView label = text(getString(labelRes), 13, MUTED);
        label.setLabelFor(selector.getId());
        addRow(panel, label, 12);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, choices);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        selector.setAdapter(adapter);
        panel.addView(selector, new LinearLayout.LayoutParams(-1, dp(45)));
        return selector;
    }

    private void addRow(LinearLayout parent, View child, int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(top);
        parent.addView(child, params);
    }

    private GradientDrawable surface(int top, int bottom, int stroke) {
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
        background.setCornerRadius(dp(15));
        background.setStroke(dp(1), stroke);
        return background;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(17), dp(15), dp(17), dp(11));
        card.setBackground(surface(0xFF211C20, 0xFF1A171D, EDGE));
        return card;
    }

    private Button action(String label, int icon, boolean primary) {
        Button view = new Button(this);
        view.setText(label);
        view.setAllCaps(false);
        view.setTextSize(16);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setTextColor(new ColorStateList(
                new int[][]{new int[]{-android.R.attr.state_enabled}, new int[0]}, new int[]{0xFFB9AFA9, TEXT}));
        view.setBackgroundTintList(null);
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x36FFFFFF),
                primary ? surface(0xFFA63432, 0xFF7C2228, 0xFFC2554A) : surface(0xFF302427, 0xFF211B22, EDGE), null));
        view.setPaddingRelative(dp(16), dp(8), dp(16), dp(8));
        if (icon != 0) {
            Drawable drawable = getDrawable(icon);
            drawable.setTint(primary ? TEXT : GOLD);
            drawable.setBounds(0, 0, dp(21), dp(21));
            view.setCompoundDrawablesRelative(drawable, null, null, null);
            view.setCompoundDrawablePadding(dp(8));
        }
        return view;
    }

    private void updateButtons() {
        boolean available = GameDataImport.available(files.resolve("game"));
        status.setText(available ? R.string.launcher_ready : R.string.launcher_needs_data);
        status.setTextColor(available ? 0xFFADD5B7 : MUTED);
        select.setVisibility(available ? View.GONE : View.VISIBLE);
        select.setEnabled(!available);
        language.setEnabled(true);
        play.setEnabled(available);
        online.setEnabled(available);
    }

    private void pick() {
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(picker, PICK_DATA);
        } catch (ActivityNotFoundException error) {
            status.setText(R.string.launcher_no_picker);
            GameDiagnostics.record(this, "Folder picker unavailable");
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_DATA || result != RESULT_OK || data == null) return;
        Uri tree = data.getData();
        if (tree == null || !DocumentsContract.isTreeUri(tree)) return;
        select.setEnabled(false);
        language.setEnabled(false);
        status.setText(R.string.launcher_copying);
        worker.execute(() -> {
            try {
                int count = GameDataImport.copy(getContentResolver(), tree, files.resolve("game"), filesCopied ->
                        runOnUiThread(() -> status.setText(getString(R.string.launcher_copied, filesCopied))));
                Log.i("HOTD2RE", "Imported " + count + " game data files");
                GameDiagnostics.record(this, "Imported game files: " + count);
                runOnUiThread(this::updateButtons);
            } catch (IOException | RuntimeException error) {
                GameDataImport.Failure failure = error instanceof GameDataImport.Failure
                        ? (GameDataImport.Failure) error : null;
                String reason = failure == null ? error.getClass().getSimpleName() : getResources().getResourceEntryName(failure.message);
                if (failure != null && failure.message == R.string.import_missing_folder) reason += " (" + failure.arguments[0] + ")";
                Log.e("HOTD2RE", "Data import failed: " + reason, error);
                GameDiagnostics.record(this, "Game data import failed: " + reason);
                runOnUiThread(() -> {
                    status.setText(failure == null ? getString(R.string.launcher_import_error) : getString(failure.message, failure.arguments));
                    select.setEnabled(true);
                    language.setEnabled(true);
                });
            }
        });
    }

    private void launch(boolean openOnline) {
        try (InputStream input = getAssets().open(SCENE)) {
            Path scene = files.resolve(SCENE);
            Files.createDirectories(scene.getParent());
            Files.copy(input, scene, StandardCopyOption.REPLACE_EXISTING);
            int selection = Math.max(0, displayFormat.getSelectedItemPosition());
            GameDiagnostics.record(this, "Launch " + (openOnline ? "online" : "game") + ", display " + VIEWPORTS[selection]
                    + ", audio " + (audio.isChecked() ? "on" : "off"));
            startActivity(new Intent(this, GameActivity.class)
                    .putExtra(GameActivity.EXTRA_VIEWPORT, VIEWPORTS[selection])
                    .putExtra(GameActivity.EXTRA_AUDIO, audio.isChecked())
                    .putExtra(GameActivity.EXTRA_ONLINE, openOnline));
        } catch (IOException error) {
            Log.e("HOTD2RE", "Could not prepare the packaged scene descriptor", error);
            status.setText(R.string.launcher_scene_error);
            GameDiagnostics.record(this, "Starting scene preparation failed: " + error.getClass().getSimpleName());
        }
    }

    private void reviewReport() {
        worker.execute(() -> {
            String body = getString(R.string.report_issue_intro) + "\n\n" + GameDiagnostics.report(this);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                new AlertDialog.Builder(this)
                        .setTitle(R.string.report_title)
                        .setMessage(getString(R.string.report_instruction) + "\n\n" + body)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setNeutralButton(R.string.report_copy, (dialog, which) -> copyReport(body, R.string.report_copied))
                        .setPositiveButton(R.string.report_open, (dialog, which) -> openIssue(body))
                        .show();
            });
        });
    }

    private void openIssue(String body) {
        Uri issue = Uri.parse("https://github.com/codepdbh/hotd2re-android-evolved/issues/new").buildUpon()
                .appendQueryParameter("title", getString(R.string.report_issue_title))
                .appendQueryParameter("body", body)
                .build();
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, issue).addCategory(Intent.CATEGORY_BROWSABLE));
        } catch (ActivityNotFoundException error) {
            copyReport(body, R.string.report_no_browser);
        }
    }

    private void copyReport(String body, int feedback) {
        ClipboardManager clipboard = getSystemService(ClipboardManager.class);
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.report_title), body));
        Toast.makeText(this, feedback, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        worker.shutdown();
        super.onDestroy();
    }
}
