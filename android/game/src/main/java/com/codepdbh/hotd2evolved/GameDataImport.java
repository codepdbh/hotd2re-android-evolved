package com.codepdbh.hotd2evolved;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.function.IntConsumer;
import java.util.stream.Stream;

final class GameDataImport {
    private static final String[] COLUMNS = {
        Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE
    };

    private GameDataImport() {}

    static boolean available(Path game) {
        try {
            validate(game);
            return true;
        } catch (IOException error) {
            return false;
        }
    }

    static int copy(ContentResolver resolver, Uri tree, Path game, IntConsumer progress) throws IOException {
        if (Files.exists(game)) throw new IOException("Ya existe una importación en el almacenamiento de la app.");
        Path staging = Files.createTempDirectory(game.getParent(), "game-import-");
        try {
            int count = copyChildren(resolver, tree, DocumentsContract.getTreeDocumentId(tree), staging, 0, progress);
            validate(staging);
            Files.move(staging, game, StandardCopyOption.ATOMIC_MOVE);
            return count;
        } finally {
            if (Files.exists(staging)) cleanup(staging);
        }
    }

    private static int copyChildren(ContentResolver resolver, Uri tree, String parent, Path destination, int count,
                                    IntConsumer progress) throws IOException {
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent);
        try (Cursor rows = resolver.query(children, COLUMNS, null, null, null)) {
            if (rows == null) throw new IOException("Android no pudo leer una de las carpetas.");
            while (rows.moveToNext()) {
                String document = rows.getString(0);
                String name = rows.getString(1);
                if (name == null || name.isEmpty() || name.equals(".") || name.equals("..") || name.contains("/")
                        || name.contains("\\")) {
                    throw new IOException("El proveedor devolvió un nombre de archivo inválido.");
                }
                Path next = destination.resolve(name);
                if (Document.MIME_TYPE_DIR.equals(rows.getString(2))) {
                    Files.createDirectory(next);
                    count = copyChildren(resolver, tree, document, next, count, progress);
                } else {
                    Uri resource = DocumentsContract.buildDocumentUriUsingTree(tree, document);
                    try (InputStream input = resolver.openInputStream(resource)) {
                        if (input == null) throw new IOException("No se pudo abrir " + name);
                        Files.copy(input, next);
                    }
                    if (++count % 32 == 0) progress.accept(count);
                }
            }
        }
        return count;
    }

    private static void validate(Path game) throws IOException {
        Path executable = game.resolve("Hod2.exe");
        if (!Files.isRegularFile(executable)) throw new IOException("Falta Hod2.exe en la carpeta elegida.");
        try (RandomAccessFile file = new RandomAccessFile(executable.toFile(), "r")) {
            if (file.length() < 0x200 || file.readUnsignedShort() != 0x4d5a) {
                throw new IOException("Hod2.exe no tiene una cabecera PE válida.");
            }
            file.seek(0x3c);
            long header = Integer.toUnsignedLong(Integer.reverseBytes(file.readInt()));
            if (header < 0x40 || header > file.length() - 26) {
                throw new IOException("Hod2.exe tiene un desplazamiento de cabecera inválido.");
            }
            file.seek(header);
            if (file.readInt() != 0x50450000) throw new IOException("Hod2.exe no tiene una firma PE válida.");
            file.seek(header + 24);
            if (file.readUnsignedShort() != 0x0b01) throw new IOException("Se requiere Hod2.exe de 32 bits para PC.");
        }
        for (String dir : new String[]{"cam", "coli", "evt", "mot", "pol", "tex"}) {
            if (!Files.isDirectory(game.resolve(dir))) throw new IOException("Falta la carpeta " + dir + ".");
        }
        if (!Files.isDirectory(game.resolve("sound")) && !Files.isDirectory(game.resolve("Sound"))) {
            throw new IOException("Falta la carpeta de audio del juego.");
        }
    }

    private static void cleanup(Path staging) {
        try (Stream<Path> entries = Files.walk(staging)) {
            Path[] paths = entries.sorted(Comparator.reverseOrder()).toArray(Path[]::new);
            for (Path path : paths) Files.deleteIfExists(path);
        } catch (IOException error) {
            android.util.Log.w("HOTD2RE", "Could not remove the unfinished private import", error);
        }
    }
}
