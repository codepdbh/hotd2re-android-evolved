package dev.sdlprobe.android;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.IntPredicate;

/** Drives Android's input pipeline and checks the pixels presented by SDL. */
public final class ProbeTouchInstrumentation extends Instrumentation {
    private final CountDownLatch stopped = new CountDownLatch(1);
    private final CountDownLatch resumed = new CountDownLatch(1);
    // Read and written on the activity's main thread.
    private Activity backgrounded;
    private Bundle options;

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        options = arguments == null ? Bundle.EMPTY : arguments;
        start();
    }

    @Override
    public void callActivityOnStop(Activity activity) {
        super.callActivityOnStop(activity);
        if (activity == backgrounded) stopped.countDown();
    }

    @Override
    public void callActivityOnResume(Activity activity) {
        super.callActivityOnResume(activity);
        if (activity == backgrounded) resumed.countDown();
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        result.putString("processId", Integer.toString(android.os.Process.myPid()));
        Activity activity = null;
        int status = Activity.RESULT_CANCELED;
        try {
            String packageName = getTargetContext().getPackageName();
            boolean storage = "storage".equals(options.getString("mode"));
            String activityName = storage ? "dev.sdlprobe.android.StorageActivity" : "org.libsdl.app.SDLActivity";
            Intent launch = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setClassName(packageName, activityName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity = startActivitySync(launch);
            if (storage) {
                StorageAdapterUiTest.verify(this, options);
                result.putString("stream", "SDL_PROBE_STORAGE_PASS");
            } else {
                verifyTouch(activity, launch);
                result.putString("stream", "SDL_PROBE_MULTITOUCH_PASS");
            }
            status = Activity.RESULT_OK;
        } catch (Throwable failure) {
            result.putString("stream", android.util.Log.getStackTraceString(failure));
        } finally {
            if (activity != null) {
                Activity target = activity;
                runOnMainSync(target::finish);
            }
            finish(status, result);
        }
    }

    private void verifyTouch(Activity activity, Intent launch) throws InterruptedException {
        waitForIdleSync();
        UiAutomation automation = getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        int[] points = new int[3];
        runOnMainSync(() -> {
            View decor = activity.getWindow().getDecorView();
            int[] origin = new int[2];
            decor.getLocationOnScreen(origin);
            points[0] = origin[0] + Math.round(decor.getWidth() * 0.44f);
            points[1] = origin[0] + Math.round(decor.getWidth() * 0.56f);
            points[2] = origin[1] + Math.round(decor.getHeight() * 0.25f);
        });
        int firstX = points[0], secondX = points[1], y = points[2];
        int original = awaitRed(automation, firstX, y, red -> red >= 80 && red <= 115, "initial frame");
        int single = Math.round(original * 0.45f);
        int multi = Math.round(original * 0.80f);
        int secondPointer = 1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT;

        long down = SystemClock.uptimeMillis();
        int held = 0;
        try {
            if (!inject(automation, down, MotionEvent.ACTION_DOWN, y, firstX)) {
                // A prior interrupted test can leave these virtual pointers down in InputDispatcher.
                // The old window can be gone even though the dispatcher clears the pointers.
                inject(automation, down, MotionEvent.ACTION_CANCEL, y, firstX, secondX);
                down = SystemClock.uptimeMillis();
                send(automation, down, MotionEvent.ACTION_DOWN, y, firstX);
            }
            held = 1;
            awaitRed(automation, firstX, y, red -> near(red, single), "first finger");
            send(automation, down, MotionEvent.ACTION_POINTER_DOWN | secondPointer, y, firstX, secondX);
            held = 2;
            awaitRed(automation, firstX, y, red -> near(red, multi), "two fingers");
            send(automation, down, MotionEvent.ACTION_POINTER_UP | secondPointer, y, firstX, secondX);
            held = 1;
            awaitRed(automation, firstX, y, red -> near(red, single), "release second finger");
            send(automation, down, MotionEvent.ACTION_UP, y, firstX);
            held = 0;

            down = SystemClock.uptimeMillis();
            send(automation, down, MotionEvent.ACTION_DOWN, y, firstX);
            held = 1;
            awaitRed(automation, firstX, y, red -> near(red, original), "next gesture");
            send(automation, down, MotionEvent.ACTION_POINTER_DOWN | secondPointer, y, firstX, secondX);
            held = 2;
            awaitRed(automation, firstX, y, red -> near(red, multi), "two fingers before background");

            boolean[] moved = {false};
            runOnMainSync(() -> {
                backgrounded = activity;
                moved[0] = activity.moveTaskToBack(true);
            });
            if (!moved[0]) throw new AssertionError("Activity did not move to background");
            if (!stopped.await(8, TimeUnit.SECONDS)) throw new AssertionError("Activity did not stop");
            getTargetContext().startActivity(launch);
            if (!resumed.await(8, TimeUnit.SECONDS)) throw new AssertionError("Activity did not resume");
            awaitRed(automation, firstX, y, red -> near(red, original), "contacts cleared on resume");
        } finally {
            if (held == 2) inject(automation, down, MotionEvent.ACTION_CANCEL, y, firstX, secondX);
            if (held == 1) inject(automation, down, MotionEvent.ACTION_CANCEL, y, firstX);
        }
    }

    private static boolean near(int actual, int expected) {
        return Math.abs(actual - expected) <= 4;
    }

    private static int awaitRed(UiAutomation automation, int x, int y, IntPredicate matches, String stage) {
        long deadline = SystemClock.uptimeMillis() + 5000;
        int red = -1;
        do {
            Bitmap screenshot = automation.takeScreenshot();
            if (screenshot != null) {
                try {
                    if (x < screenshot.getWidth() && y < screenshot.getHeight()) {
                        red = Color.red(screenshot.getPixel(x, y));
                        if (matches.test(red)) return red;
                    }
                } finally {
                    screenshot.recycle();
                }
            }
            SystemClock.sleep(75);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError(stage + " did not render the expected pixel (red=" + red + ")");
    }

    private static void send(UiAutomation automation, long down, int action, int y, int... positions) {
        if (!inject(automation, down, action, y, positions)) {
            throw new AssertionError("Android rejected motion event " + action);
        }
    }

    private static boolean inject(UiAutomation automation, long down, int action, int y, int... positions) {
        MotionEvent.PointerProperties[] pointers = new MotionEvent.PointerProperties[positions.length];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[positions.length];
        for (int i = 0; i < positions.length; ++i) {
            pointers[i] = new MotionEvent.PointerProperties();
            pointers[i].id = 2 + i * 5;
            pointers[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coords[i] = new MotionEvent.PointerCoords();
            coords[i].x = positions[i];
            coords[i].y = y;
            coords[i].pressure = 1;
            coords[i].size = 1;
        }
        MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, positions.length,
                pointers, coords, 0, 0, 1, 1, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
        try {
            return automation.injectInputEvent(event, true);
        } finally {
            event.recycle();
        }
    }
}
