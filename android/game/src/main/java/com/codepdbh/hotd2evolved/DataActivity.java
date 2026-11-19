package com.codepdbh.hotd2evolved;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.View;
import android.view.WindowInsets;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DataActivity extends Activity {
    private static final int PICK_DATA = 1;
    private static final String SCENE = "scenes/st1_play.scene";
    private static final String VIEWPORT_SETTING = "display_format";
    private static final String[] VIEWPORTS = {"4:3", "16:9", "window"};
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Path files;
    private TextView status;
    private Button select;
    private Button play;
    private Spinner displayFormat;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        files = getFilesDir().toPath();
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(24 * getResources().getDisplayMetrics().density);
        panel.setPadding(padding, padding, padding, padding);
        panel.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(padding + safe.left, padding + safe.top, padding + safe.right, padding + safe.bottom);
            }
            return insets;
        });

        TextView title = new TextView(this);
        title.setText("The House of the Dead 2 — Android Evolved");
        title.setTextSize(23);
        panel.addView(title);
        status = new TextView(this);
        status.setAccessibilityLiveRegion(TextView.ACCESSIBILITY_LIVE_REGION_POLITE);
        panel.addView(status);
        select = new Button(this);
        select.setText("Importar carpeta del juego");
        select.setOnClickListener(view -> pick());
        panel.addView(select);

        displayFormat = new Spinner(this);
        displayFormat.setId(View.generateViewId());
        TextView displayLabel = new TextView(this);
        displayLabel.setText("Formato de pantalla");
        displayLabel.setLabelFor(displayFormat.getId());
        panel.addView(displayLabel);
        ArrayAdapter<String> formats = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                new String[]{"Clásico (4:3)", "Panorámico (16:9)", "Ancho del teléfono"});
        formats.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        displayFormat.setAdapter(formats);
        int savedFormat = getPreferences(MODE_PRIVATE).getInt(VIEWPORT_SETTING, 0);
        displayFormat.setSelection(savedFormat >= 0 && savedFormat < VIEWPORTS.length ? savedFormat : 0);
        panel.addView(displayFormat);

        play = new Button(this);
        play.setText("Jugar");
        play.setOnClickListener(view -> launch());
        panel.addView(play);
        setContentView(panel);
        updateButtons();
    }

    private void updateButtons() {
        boolean available = GameDataImport.available(files.resolve("game"));
        status.setText(available ? "Datos listos para iniciar HOTD2RE." : "Importa la carpeta que contiene Hod2.exe.");
        select.setEnabled(!available);
        play.setEnabled(available);
    }

    private void pick() {
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(picker, PICK_DATA);
        } catch (ActivityNotFoundException error) {
            status.setText("El teléfono no dispone de selector de carpetas.");
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_DATA || result != RESULT_OK || data == null) return;
        Uri tree = data.getData();
        if (tree == null || !DocumentsContract.isTreeUri(tree)) return;
        select.setEnabled(false);
        status.setText("Copiando los datos al almacenamiento privado de la app…");
        worker.execute(() -> {
            try {
                int count = GameDataImport.copy(getContentResolver(), tree, files.resolve("game"), filesCopied ->
                        runOnUiThread(() -> status.setText("Archivos copiados: " + filesCopied)));
                Log.i("HOTD2RE", "Imported " + count + " game data files");
                runOnUiThread(this::updateButtons);
            } catch (IOException | RuntimeException error) {
                Log.e("HOTD2RE", "Data import failed", error);
                runOnUiThread(() -> {
                    status.setText(error.getMessage());
                    select.setEnabled(true);
                });
            }
        });
    }

    private void launch() {
        try (InputStream input = getAssets().open(SCENE)) {
            Path scene = files.resolve(SCENE);
            Files.createDirectories(scene.getParent());
            Files.copy(input, scene, StandardCopyOption.REPLACE_EXISTING);
            int selection = Math.max(0, displayFormat.getSelectedItemPosition());
            getPreferences(MODE_PRIVATE).edit().putInt(VIEWPORT_SETTING, selection).apply();
            startActivity(new Intent(this, GameActivity.class).putExtra(GameActivity.EXTRA_VIEWPORT, VIEWPORTS[selection]));
        } catch (IOException error) {
            Log.e("HOTD2RE", "Could not prepare the packaged scene descriptor", error);
            status.setText("No se pudo preparar la escena de inicio.");
        }
    }

    @Override
    protected void onDestroy() {
        worker.shutdown();
        super.onDestroy();
    }
}
