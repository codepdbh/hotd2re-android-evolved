package dev.sdlprobe.android;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.function.Predicate;

final class StorageAdapterUiTest {
    private StorageAdapterUiTest() {}

    static void verify(Instrumentation test, Bundle options) {
        String folder = options.getString("folder");
        String file = options.getString("file");
        if (folder == null || file == null) throw new AssertionError("Supply folder and file test arguments");
        UiAutomation ui = test.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        String ids = test.getTargetContext().getPackageName() + ":id/storage_";

        AccessibilityNodeInfo status = await(ui, node -> (ids + "status").equals(node.getViewIdResourceName()));
        if (!text(status).contains("Carpeta elegida. ") || !text(status).endsWith(folder)) {
            click(await(ui, node -> (ids + "select").equals(node.getViewIdResourceName())));
            AccessibilityNodeInfo row = await(ui, node -> folder.equals(text(node)) && isPicker(node));
            if (!("" + row.getViewIdResourceName()).endsWith(":id/breadcrumb_text")) click(row);
            AccessibilityNodeInfo choose = await(ui, node -> isPicker(node) && node.isEnabled()
                    && "android:id/button1".equals(node.getViewIdResourceName()));
            String chooseLabel = text(choose);
            click(choose);
            click(await(ui, node -> isPicker(node) && "android:id/button1".equals(node.getViewIdResourceName())
                    && node.isEnabled() && !text(node).equals(chooseLabel)));
            await(ui, node -> (ids + "status").equals(node.getViewIdResourceName())
                    && text(node).contains("Carpeta elegida. ") && text(node).endsWith(folder));
        }

        read(ui, ids, file, "Lectura verificada desde C++");
        read(ui, ids, "../" + file, "Usa una ruta relativa");
        String nested = options.getString("nested");
        if (nested != null) read(ui, ids, nested, "Lectura verificada desde C++");
    }

    private static boolean isPicker(AccessibilityNodeInfo node) {
        return ("" + node.getPackageName()).endsWith("documentsui");
    }

    private static void read(UiAutomation ui, String ids, String path, String expected) {
        Bundle value = new Bundle();
        value.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, path);
        if (!await(ui, node -> (ids + "path").equals(node.getViewIdResourceName()))
                .performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, value)) {
            throw new AssertionError("Could not enter the relative file path");
        }
        click(await(ui, node -> (ids + "read").equals(node.getViewIdResourceName()) && node.isEnabled()));
        await(ui, node -> (ids + "status").equals(node.getViewIdResourceName()) && text(node).contains(expected));
    }

    private static String text(AccessibilityNodeInfo node) {
        return node.getText() == null ? "" : node.getText().toString();
    }

    private static void click(AccessibilityNodeInfo node) {
        while (node != null && !node.isClickable()) node = node.getParent();
        if (node == null || !node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            throw new AssertionError("Android did not activate the selected control");
        }
    }

    private static AccessibilityNodeInfo await(UiAutomation ui, Predicate<AccessibilityNodeInfo> matches) {
        long deadline = SystemClock.uptimeMillis() + 7000;
        do {
            AccessibilityNodeInfo node = find(ui.getRootInActiveWindow(), matches);
            if (node != null) return node;
            SystemClock.sleep(100);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("The expected storage control or result did not appear");
    }

    private static AccessibilityNodeInfo find(AccessibilityNodeInfo node, Predicate<AccessibilityNodeInfo> matches) {
        if (node == null || matches.test(node)) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo result = find(node.getChild(i), matches);
            if (result != null) return result;
        }
        return null;
    }
}
