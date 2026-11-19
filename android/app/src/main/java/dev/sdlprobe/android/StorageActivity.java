package dev.sdlprobe.android;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.text.InputType;
import android.util.Log;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class StorageActivity extends Activity {
    private static final int PICK_FOLDER = 41;
    private static final int READ_GRANT = Intent.FLAG_GRANT_READ_URI_PERMISSION;
    private static final String TAG = "StorageAdapter";
    private static final String TREE_KEY = "read_tree";

    static {
        System.loadLibrary("main");
    }

    private final ExecutorService reader = Executors.newSingleThreadExecutor();
    private Uri tree;
    private TextView status;
    private EditText path;
    private Button select;
    private Button inspect;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        String saved = preferences().getString(TREE_KEY, null);
        if (saved != null) {
            Uri candidate = Uri.parse(saved);
            if (getContentResolver().getPersistedUriPermissions().stream()
                    .anyMatch(grant -> grant.isReadPermission() && candidate.equals(grant.getUri()))) {
                tree = candidate;
            }
        }

        ScrollView scroll = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);
        scroll.addView(layout);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });

        select = new Button(this);
        select.setId(R.id.storage_select);
        select.setText("Elegir carpeta");
        select.setOnClickListener(view -> pickFolder());
        layout.addView(select);

        path = new EditText(this);
        path.setId(R.id.storage_path);
        path.setSingleLine(true);
        path.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        path.setHint("Ruta relativa, por ejemplo carpeta/archivo.bin");
        layout.addView(path);

        inspect = new Button(this);
        inspect.setId(R.id.storage_read);
        inspect.setText("Comprobar lectura");
        inspect.setEnabled(tree != null);
        inspect.setOnClickListener(view -> inspectFile());
        layout.addView(inspect);

        status = new TextView(this);
        status.setId(R.id.storage_status);
        status.setAccessibilityLiveRegion(TextView.ACCESSIBILITY_LIVE_REGION_POLITE);
        status.setText(tree == null ? "Elige una carpeta para conceder acceso de lectura." : selectedLabel());
        layout.addView(status);
        setContentView(scroll);
        select.setFocusableInTouchMode(true);
        select.requestFocus();
    }

    private SharedPreferences preferences() {
        return getSharedPreferences("storage_adapter", MODE_PRIVATE);
    }

    private String selectedLabel() {
        return "Carpeta elegida. " + DocumentsContract.getTreeDocumentId(tree);
    }

    private void pickFolder() {
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        picker.addFlags(READ_GRANT | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        if (tree != null) picker.putExtra(DocumentsContract.EXTRA_INITIAL_URI, tree);
        try {
            startActivityForResult(picker, PICK_FOLDER);
        } catch (ActivityNotFoundException error) {
            status.setText("Android no ofrece un selector de carpetas en este dispositivo.");
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_FOLDER || result != RESULT_OK || data == null) return;
        Uri selected = data.getData();
        if (selected == null || !DocumentsContract.isTreeUri(selected) || (data.getFlags() & READ_GRANT) == 0) {
            status.setText("Android no concedió acceso de lectura a la carpeta.");
            return;
        }
        try {
            getContentResolver().takePersistableUriPermission(selected, READ_GRANT);
            String previous = preferences().getString(TREE_KEY, null);
            preferences().edit().putString(TREE_KEY, selected.toString()).apply();
            if (previous != null && !previous.equals(selected.toString())) {
                try {
                    getContentResolver().releasePersistableUriPermission(Uri.parse(previous), READ_GRANT);
                } catch (SecurityException ignored) {
                    // The old grant was already revoked.
                }
            }
            tree = selected;
            status.setText(selectedLabel());
            Log.i(TAG, "storage.selected persistent=true");
        } catch (SecurityException error) {
            tree = selected;
            status.setText(selectedLabel() + ". El permiso es temporal.");
            Log.i(TAG, "storage.selected persistent=false");
        }
        inspect.setEnabled(true);
    }

    private void inspectFile() {
        final Uri folder = tree;
        final String relative = path.getText().toString();
        if (relative.isEmpty()) {
            status.setText("Escribe la ruta del archivo dentro de la carpeta elegida.");
            return;
        }
        select.setEnabled(false);
        inspect.setEnabled(false);
        status.setText("Leyendo…");
        reader.execute(() -> {
            String message;
            try {
                long[] result = nativeInspect(new StorageTree(getContentResolver(), folder), relative);
                String fingerprint = String.format(Locale.ROOT, "%016x", result[2]);
                Log.i(TAG, "storage.read size=" + result[0] + " sample=" + result[1] + " fnv=" + fingerprint);
                message = "Lectura verificada desde C++. Bytes de muestra: " + result[1]
                        + (result[0] >= 0 ? ". Tamaño del archivo: " + result[0] + " bytes." : ".");
            } catch (IOException | RuntimeException error) {
                Log.i(TAG, "storage.read_rejected " + error.getClass().getSimpleName());
                message = "No se pudo leer el archivo. " + error.getMessage();
            }
            String output = message;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                status.setText(output);
                select.setEnabled(true);
                inspect.setEnabled(true);
            });
        });
    }

    @Override
    protected void onDestroy() {
        reader.shutdownNow();
        super.onDestroy();
    }

    private static native long[] nativeInspect(StorageTree tree, String path) throws IOException;
}
