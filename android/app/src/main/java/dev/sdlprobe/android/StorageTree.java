package dev.sdlprobe.android;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;

import java.io.FileNotFoundException;
import java.io.IOException;

final class StorageTree {
    private static final String[] COLUMNS = {
        Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE
    };

    private final ContentResolver resolver;
    private final Uri tree;

    StorageTree(ContentResolver resolver, Uri tree) {
        this.resolver = resolver;
        this.tree = tree;
    }

    // Called through JNI. The native caller closes the detached descriptor.
    @SuppressWarnings("unused")
    public int openReadFd(String relativePath) throws IOException {
        String[] parts = relativePath.split("/", -1);
        for (String part : parts) {
            if (part.isEmpty() || part.equals(".") || part.equals("..") || part.indexOf('\\') >= 0
                    || part.indexOf('\0') >= 0) {
                throw new IOException("Usa una ruta relativa a la carpeta seleccionada.");
            }
        }

        String id = DocumentsContract.getTreeDocumentId(tree);
        for (int i = 0; i < parts.length; i++) {
            id = findChild(id, parts[i], i < parts.length - 1);
        }

        Uri file = DocumentsContract.buildDocumentUriUsingTree(tree, id);
        try (ParcelFileDescriptor descriptor = resolver.openFileDescriptor(file, "r")) {
            if (descriptor == null) throw new FileNotFoundException("El proveedor no entregó el archivo.");
            return descriptor.detachFd();
        }
    }

    private String findChild(String parentId, String name, boolean directory) throws IOException {
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId);
        String match = null;
        try (Cursor cursor = resolver.query(children, COLUMNS, null, null, null)) {
            if (cursor == null) throw new IOException("El proveedor no enumeró la carpeta.");
            while (cursor.moveToNext()) {
                boolean isDirectory = Document.MIME_TYPE_DIR.equals(cursor.getString(2));
                if (isDirectory != directory || !name.equals(cursor.getString(1))) continue;
                if (match != null) throw new IOException("Hay varias entradas llamadas " + name);
                match = cursor.getString(0);
            }
        }
        if (match == null) throw new FileNotFoundException("No se encontró " + name);
        return match;
    }
}
