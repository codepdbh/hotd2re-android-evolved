package com.codepdbh.hotd2evolved;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

final class GameDiagnostics {
    private static final String LAUNCHER_LOG = "android-launcher.log";
    private static final String ENGINE_LOG = "android-engine.log";
    private static final int LOG_LIMIT = 24 * 1024;
    // Only these native messages are copied verbatim to the report. They contain no room codes,
    // addresses, account names or private paths.
    private static final Pattern SAFE_ENGINE_LINE = Pattern.compile(
            "HOTD2RE [0-9.]{1,12} \\(build [0-9 A-Z]{1,24}\\)"
            + "|play: renderer [A-Za-z0-9_-]{1,24}(?:, input [A-Za-z0-9_ /-]{1,60})?"
            + "|Low latency audio enabled"
            + "|Native main returned -?[0-9]{1,10}"
            + "|netplay: cannot create a UDP socket"
            + "|netplay: DESYNC at frame [0-9]{1,10} \\(ours [A-F0-9]{8}, theirs [A-F0-9]{8}\\); gameplay halted"
            + "|netplay: connected as P[12] \\(lobby\\), input delay [0-9]{1,10} frames, game from frame [0-9]{1,10}, via (?:direct connection|server relay), round trip [0-9]{1,10} ms");

    private GameDiagnostics() {}

    static synchronized void record(Context context, String event) {
        Path file = context.getFilesDir().toPath().resolve(LAUNCHER_LOG);
        try {
            if (Files.exists(file) && Files.size(file) >= LOG_LIMIT) Files.delete(file);
            Files.write(file, (event + "\n").getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException error) {
            Log.w("HOTD2RE", "Could not store launcher diagnostics", error);
        }
    }

    static String report(Context context) {
        String version;
        try {
            version = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException error) {
            version = "unknown";
        }
        Path files = context.getFilesDir().toPath();
        StringBuilder report = new StringBuilder("### Android diagnostics\n\n```");
        report.append("\nApp: ").append(version).append("\nDevice model: ").append(Build.MODEL)
                .append("\nAndroid API: ").append(Build.VERSION.SDK_INT)
                .append("\nGame data present: ").append(GameDataImport.available(files.resolve("game")));
        String abnormalExit = recentAbnormalExit(context);
        if (abnormalExit != null) report.append("\nRecent abnormal exit: ").append(abnormalExit);

        List<String> launcher = readLines(files.resolve(LAUNCHER_LOG));
        List<String> engine = new ArrayList<>();
        for (String raw : readLines(files.resolve(ENGINE_LOG))) {
            String line = normalizeEngineLine(raw.trim());
            if (line != null) engine.add(line);
        }
        appendRecent(report, "Launcher", launcher, 5);
        appendRecent(report, "Engine", engine, 8);
        return report.append("\n```").toString();
    }

    private static String normalizeEngineLine(String line) {
        if (SAFE_ENGINE_LINE.matcher(line).matches()) return line;
        if (line.startsWith("world: ")) return "World initialization failed";
        if (line.startsWith("platform: ")) return "Platform initialization failed";
        if (line.startsWith("renderer: ")) return "Renderer initialization failed";
        if (line.startsWith("audio: ")) return "Game audio initialization failed";
        if (line.startsWith("rankings: ")) return "Rankings connection initialization failed";
        if (line.startsWith("netplay: ") && (line.contains("failed") || line.contains("cannot")))
            return "Network operation failed";
        return null;
    }

    private static List<String> readLines(Path file) {
        try {
            if (Files.exists(file) && Files.size(file) <= LOG_LIMIT + 1024) {
                return Arrays.asList(new String(Files.readAllBytes(file), StandardCharsets.UTF_8).split("\n"));
            }
        } catch (IOException ignored) {
            // A report can still be created while the game rotates its log.
        }
        return Collections.emptyList();
    }

    private static void appendRecent(StringBuilder report, String section, List<String> lines, int count) {
        if (lines.isEmpty()) return;
        report.append("\n\n").append(section).append(':');
        for (int i = Math.max(0, lines.size() - count); i < lines.size(); ++i) report.append('\n').append(lines.get(i));
    }

    private static String recentAbnormalExit(Context context) {
        if (Build.VERSION.SDK_INT < 30) return null;
        ActivityManager activity = context.getSystemService(ActivityManager.class);
        if (activity == null) return null;
        List<ApplicationExitInfo> exits;
        try {
            exits = activity.getHistoricalProcessExitReasons(context.getPackageName(), 0, 4);
        } catch (SecurityException ignored) {
            return null;
        }
        for (ApplicationExitInfo exit : exits) {
            switch (exit.getReason()) {
                case ApplicationExitInfo.REASON_CRASH: return "Java crash";
                case ApplicationExitInfo.REASON_CRASH_NATIVE: return "Native crash";
                case ApplicationExitInfo.REASON_ANR: return "Application not responding";
                case ApplicationExitInfo.REASON_LOW_MEMORY: return "Low memory";
                case ApplicationExitInfo.REASON_INITIALIZATION_FAILURE: return "Initialization failure";
                default: break;
            }
        }
        return null;
    }
}
