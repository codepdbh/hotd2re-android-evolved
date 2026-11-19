#include <SDL3/SDL.h>
#include <SDL3/SDL_main.h>
#include <SDL3/SDL_system.h>

#include <android/log.h>
#include <atomic>
#include <cstdio>
#include <cstdlib>
#include <fcntl.h>
#include <jni.h>
#include <thread>
#include <unistd.h>

#include "android_controls.h"

int PlayMain(int argc, char** argv);

namespace {

std::atomic_uint requestedControls = 0;
constexpr char kEngineLog[] = "android-engine.log";
constexpr long kEngineLogLimit = 24 * 1024;

void LogOutput(int fd) {
    FILE* stream = fdopen(fd, "r");
    if (!stream) {
        close(fd);
        return;
    }
    FILE* report = fopen(kEngineLog, "w");
    char line[1024];
    while (fgets(line, sizeof(line), stream)) {
        __android_log_write(ANDROID_LOG_INFO, "HOTD2RE", line);
        if (report && ftell(report) >= kEngineLogLimit) report = freopen(kEngineLog, "w", report);
        if (report) {
            fputs(line, report);
            fflush(report);
        }
    }
    if (report) fclose(report);
    fclose(stream);
}

} // namespace

extern "C" JNIEXPORT void JNICALL
Java_com_codepdbh_hotd2evolved_GameActivity_nativeRequestControl(JNIEnv*, jclass, jint control) {
    requestedControls.fetch_or(static_cast<unsigned>(control));
}

unsigned TakeAndroidControlRequests() {
    return requestedControls.exchange(0);
}

void SetAndroidAvailableControls(unsigned controls) {
    requestedControls.fetch_and(controls);
    auto* env = static_cast<JNIEnv*>(SDL_GetAndroidJNIEnv());
    if (!env) return;
    auto activity = static_cast<jobject>(SDL_GetAndroidActivity());
    if (!activity) return;
    jclass type = env->GetObjectClass(activity);
    if (type) {
        const auto method = env->GetMethodID(type, "setAvailableControls", "(I)V");
        if (method) env->CallVoidMethod(activity, method, static_cast<jint>(controls));
        env->DeleteLocalRef(type);
    }
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        SDL_LogError(SDL_LOG_CATEGORY_APPLICATION, "Could not update the Android game controls");
    }
    env->DeleteLocalRef(activity);
}

int main(int argc, char** argv) {
    requestedControls.store(0);
    const char* files = SDL_GetAndroidInternalStoragePath();
    if (!files || chdir(files) != 0) {
        __android_log_write(ANDROID_LOG_ERROR, "HOTD2RE", "Cannot enter the app's private data directory");
        return 1;
    }

    SDL_SetHint(SDL_HINT_ORIENTATIONS, "LandscapeLeft LandscapeRight");
    SDL_SetHint(SDL_HINT_TOUCH_MOUSE_EVENTS, "0");
    SDL_SetHint(SDL_HINT_ANDROID_TRAP_BACK_BUTTON, "1");
    setenv("TMPDIR", files, 0);

    int output[2];
    if (pipe2(output, O_CLOEXEC) != 0) return PlayMain(argc, argv);
    const int oldStdout = dup(STDOUT_FILENO);
    const int oldStderr = dup(STDERR_FILENO);
    dup2(output[1], STDOUT_FILENO);
    dup2(output[1], STDERR_FILENO);
    close(output[1]);
    setvbuf(stdout, nullptr, _IOLBF, 0);
    std::thread logger(LogOutput, output[0]);
    const int result = PlayMain(argc, argv);
    fflush(stdout);
    fflush(stderr);
    if (oldStdout >= 0) {
        dup2(oldStdout, STDOUT_FILENO);
        close(oldStdout);
    } else {
        close(STDOUT_FILENO);
    }
    if (oldStderr >= 0) {
        dup2(oldStderr, STDERR_FILENO);
        close(oldStderr);
    } else {
        close(STDERR_FILENO);
    }
    logger.join();
    if (FILE* report = fopen(kEngineLog, "a")) {
        fprintf(report, "\nNative main returned %d\n", result);
        fclose(report);
    }
    __android_log_print(ANDROID_LOG_INFO, "HOTD2RE", "Native main returned %d", result);
    return result;
}
