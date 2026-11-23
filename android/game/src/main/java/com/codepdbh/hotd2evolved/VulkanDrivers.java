package com.codepdbh.hotd2evolved;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * User-installed Vulkan drivers. A package is either the common adrenotools ZIP
 * (meta.json with "libraryName" plus the driver library) or a bare .so file.
 * Any vendor driver works as long as the system Vulkan loader can use it as its
 * HAL: Turnip for Adreno, PanVK for Mali, or other Mesa builds for PowerVR or
 * MediaTek devices.
 */
final class VulkanDrivers {
    static final String SYSTEM = "";
    private static final String DIRECTORY = "vulkan_drivers";
    private static final String META = "meta.json";
    private static final long MAX_LIBRARY_BYTES = 256L * 1024 * 1024;

    static final class Driver {
        final String id;
        final String name;
        final String description;
        final String library;
        final Path directory;

        Driver(String id, String name, String description, String library, Path directory) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.library = library;
            this.directory = directory;
        }

        String label() {
            return description.isEmpty() ? name : name + " — " + description;
        }
    }

    static final class Failure extends IOException {
        final int message;

        Failure(int message) {
            super("Vulkan driver import failed");
            this.message = message;
        }
    }

    private VulkanDrivers() {}

    static Path root(Path files) {
        return files.resolve(DIRECTORY);
    }

    static List<Driver> installed(Path files) {
        List<Driver> drivers = new ArrayList<>();
        Path root = root(files);
        if (!Files.isDirectory(root)) return drivers;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(root)) {
            for (Path directory : entries) {
                Driver driver = read(directory);
                if (driver != null) drivers.add(driver);
            }
        } catch (IOException ignored) {
            // An unreadable folder just lists no drivers; the system driver stays available.
        }
        drivers.sort(Comparator.comparing(driver -> driver.name.toLowerCase(Locale.ROOT)));
        return drivers;
    }

    static Driver find(Path files, String id) {
        if (id == null || id.isEmpty()) return null;
        for (Driver driver : installed(files)) if (driver.id.equals(id)) return driver;
        return null;
    }

    private static Driver read(Path directory) {
        try {
            JSONObject meta = new JSONObject(new String(Files.readAllBytes(directory.resolve(META)), StandardCharsets.UTF_8));
            String library = meta.optString("libraryName", "");
            if (!validLibraryName(library) || !Files.isRegularFile(directory.resolve(library))) return null;
            String name = meta.optString("name", library);
            String version = meta.optString("driverVersion", meta.optString("packageVersion", ""));
            String vendor = meta.optString("vendor", "");
            String description = (vendor + " " + version).trim();
            return new Driver(directory.getFileName().toString(), name.isEmpty() ? library : name, description, library, directory);
        } catch (IOException | JSONException error) {
            return null;
        }
    }

    static void delete(Driver driver) throws IOException {
        deleteTree(driver.directory);
    }

    /** Imports a ZIP or .so document and returns the installed driver. */
    static Driver install(ContentResolver resolver, Uri document, Path files) throws IOException {
        String display = displayName(resolver, document);
        Path root = root(files);
        Files.createDirectories(root);
        Path staging = Files.createTempDirectory(root, ".import-");
        try {
            String library;
            String name;
            JSONObject meta = null;
            if (display.toLowerCase(Locale.ROOT).endsWith(".so")) {
                library = display;
                if (!validLibraryName(library)) throw new Failure(R.string.driver_invalid_package);
                try (InputStream input = resolver.openInputStream(document)) {
                    if (input == null) throw new Failure(R.string.driver_unreadable);
                    copyLimited(input, staging.resolve(library));
                }
                name = library;
            } else {
                meta = extractZip(resolver, document, staging);
                library = meta == null ? findSingleLibrary(staging) : meta.optString("libraryName", "");
                if (library == null || !validLibraryName(library) || !Files.isRegularFile(staging.resolve(library)))
                    throw new Failure(R.string.driver_invalid_package);
                name = meta == null ? library : meta.optString("name", library);
            }
            if (!isArm64Elf(staging.resolve(library))) throw new Failure(R.string.driver_wrong_arch);
            if (meta == null) {
                meta = new JSONObject();
                try {
                    meta.put("schemaVersion", 1);
                    meta.put("name", name);
                    meta.put("libraryName", library);
                } catch (JSONException error) {
                    throw new IOException(error);
                }
            }
            Files.write(staging.resolve(META), meta.toString().getBytes(StandardCharsets.UTF_8));
            String id = uniqueId(root, name);
            Path target = root.resolve(id);
            Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
            Driver driver = read(target);
            if (driver == null) {
                deleteTree(target);
                throw new Failure(R.string.driver_invalid_package);
            }
            return driver;
        } finally {
            if (Files.exists(staging)) deleteTree(staging);
        }
    }

    private static JSONObject extractZip(ContentResolver resolver, Uri document, Path staging) throws IOException {
        JSONObject meta = null;
        try (InputStream raw = resolver.openInputStream(document)) {
            if (raw == null) throw new Failure(R.string.driver_unreadable);
            try (ZipInputStream zip = new ZipInputStream(raw)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (entry.isDirectory()) continue;
                    // Driver packages are flat. Nested files keep only their base name, and
                    // anything that is not the metadata or a library is ignored.
                    String base = entry.getName();
                    base = base.substring(base.lastIndexOf('/') + 1);
                    if (base.equals(META)) {
                        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = zip.read(buffer)) > 0) {
                            if (bytes.size() + read > 64 * 1024) throw new Failure(R.string.driver_invalid_package);
                            bytes.write(buffer, 0, read);
                        }
                        try {
                            meta = new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
                        } catch (JSONException error) {
                            throw new Failure(R.string.driver_invalid_package);
                        }
                    } else if (base.endsWith(".so") && validLibraryName(base)) {
                        copyLimited(zip, staging.resolve(base));
                    }
                }
            }
        }
        return meta;
    }

    private static String findSingleLibrary(Path staging) throws IOException {
        String found = null;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(staging, "*.so")) {
            for (Path library : entries) {
                if (found != null) return null;
                found = library.getFileName().toString();
            }
        }
        return found;
    }

    private static void copyLimited(InputStream input, Path target) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long total = 0;
        try (OutputStream output = Files.newOutputStream(target)) {
            int read;
            while ((read = input.read(buffer)) > 0) {
                total += read;
                if (total > MAX_LIBRARY_BYTES) throw new Failure(R.string.driver_invalid_package);
                output.write(buffer, 0, read);
            }
        }
    }

    static boolean validLibraryName(String name) {
        return name != null && name.endsWith(".so") && name.length() <= 128 && !name.startsWith(".")
                && name.matches("[A-Za-z0-9._+-]+");
    }

    private static boolean isArm64Elf(Path library) {
        try (RandomAccessFile file = new RandomAccessFile(library.toFile(), "r")) {
            byte[] header = new byte[20];
            file.readFully(header);
            boolean elf = header[0] == 0x7F && header[1] == 'E' && header[2] == 'L' && header[3] == 'F';
            boolean is64 = header[4] == 2;
            int machine = (header[18] & 0xFF) | ((header[19] & 0xFF) << 8);
            return elf && is64 && machine == 183; // EM_AARCH64
        } catch (IOException error) {
            return false;
        }
    }

    private static String uniqueId(Path root, String name) {
        String base = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        if (base.isEmpty()) base = "driver";
        if (base.length() > 48) base = base.substring(0, 48);
        String id = base;
        for (int i = 2; Files.exists(root.resolve(id)); ++i) id = base + "-" + i;
        return id;
    }

    private static String displayName(ContentResolver resolver, Uri document) {
        try (Cursor cursor = resolver.query(document, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null) return name;
            }
        } catch (RuntimeException ignored) {
            // Fall back to the URI's last segment.
        }
        String segment = document.getLastPathSegment();
        return segment == null ? "driver.zip" : segment.substring(segment.lastIndexOf('/') + 1);
    }

    private static void deleteTree(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(path)) {
                for (Path entry : entries) deleteTree(entry);
            }
        }
        Files.deleteIfExists(path);
    }
}
