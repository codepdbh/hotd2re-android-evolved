#include <SDL3/SDL.h>
#include <SDL3/SDL_main.h>
#include <SDL3/SDL_system.h>

#include <adrenotools/driver.h>
#include <android/log.h>
#include <atomic>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <dlfcn.h>
#include <fcntl.h>
#include <jni.h>
#include <string>
#include <thread>
#include <unistd.h>
#include <vector>

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

struct VulkanDriverRequest {
    std::string directory;  // ends with '/'
    std::string library;
    std::string hooks;      // nativeLibraryDir, ends with '/'
};

std::string WithSlash(std::string path) {
    if (!path.empty() && path.back() != '/') path += '/';
    return path;
}

// Removes the launcher's driver arguments, which PlayMain does not accept.
VulkanDriverRequest TakeVulkanDriverArguments(std::vector<char*>& args) {
    VulkanDriverRequest request;
    for (size_t i = 1; i < args.size();) {
        const std::string name = args[i];
        if (i + 1 < args.size() && (name == "--vulkan-driver-dir" || name == "--vulkan-driver-lib" || name == "--native-lib-dir")) {
            (name == "--vulkan-driver-dir" ? request.directory : name == "--vulkan-driver-lib" ? request.library : request.hooks) =
                args[i + 1];
            args.erase(args.begin() + static_cast<std::ptrdiff_t>(i), args.begin() + static_cast<std::ptrdiff_t>(i) + 2);
        } else {
            ++i;
        }
    }
    request.directory = WithSlash(request.directory);
    request.hooks = WithSlash(request.hooks);
    return request;
}

// The launcher reads this file to revert to the system driver after a failed start.
constexpr char kVulkanDriverState[] = "vulkan-driver.state";

void WriteVulkanDriverState(const char* state, const std::string& detail) {
    if (FILE* file = fopen(kVulkanDriverState, "w")) {
        fprintf(file, "%s\n%s\n", state, detail.c_str());
        fclose(file);
    }
}

// Opens a separate libvulkan instance that loads the custom driver instead of the
// vendor HAL, then points SDL at a forwarding library. Any failure keeps the system driver.
void UseCustomVulkanDriver(const VulkanDriverRequest& request) {
    if (request.library.empty() || request.directory.size() < 2 || request.hooks.size() < 2) return;
    const std::string name = request.directory + request.library;
    if (request.library.find('/') != std::string::npos || access(name.c_str(), R_OK) != 0) {
        fprintf(stderr, "Custom Vulkan driver %s is missing; using the system driver\n", name.c_str());
        WriteVulkanDriverState("failed", "missing " + request.library);
        return;
    }
    WriteVulkanDriverState("loading", request.library);
    void* loader = adrenotools_open_libvulkan(RTLD_NOW, ADRENOTOOLS_DRIVER_CUSTOM, nullptr, request.hooks.c_str(),
                                              request.directory.c_str(), request.library.c_str(), nullptr, nullptr);
    auto* procAddr = loader ? dlsym(loader, "vkGetInstanceProcAddr") : nullptr;
    const std::string shimPath = request.hooks + "libhotd2_vulkan_shim.so";
    void* shim = procAddr ? dlopen(shimPath.c_str(), RTLD_NOW) : nullptr;
    using SetForward = void (*)(void*);
    auto* setForward = shim ? reinterpret_cast<SetForward>(dlsym(shim, "hotd2_vulkan_shim_set")) : nullptr;
    if (!setForward) {
        fprintf(stderr, "Custom Vulkan driver %s could not be loaded (%s); using the system driver\n", request.library.c_str(),
                loader ? "no vkGetInstanceProcAddr or forwarding library" : "libadrenotools failed");
        WriteVulkanDriverState("failed", "load " + request.library);
        return;
    }
    setForward(procAddr);
    SDL_SetHint(SDL_HINT_VULKAN_LIBRARY, shimPath.c_str());
    SDL_SetHint("HOTD2_VULKAN_DRIVER_STATE", kVulkanDriverState);
    fprintf(stderr, "Custom Vulkan driver: %s\n", name.c_str());
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
    std::vector<char*> args(argv, argv + argc);
    const VulkanDriverRequest driver = TakeVulkanDriverArguments(args);
    remove(kVulkanDriverState);
    args.push_back(nullptr);
    argc = static_cast<int>(args.size()) - 1;
    argv = args.data();

    int output[2];
    if (pipe2(output, O_CLOEXEC) != 0) {
        UseCustomVulkanDriver(driver);
        return PlayMain(argc, argv);
    }
    const int oldStdout = dup(STDOUT_FILENO);
    const int oldStderr = dup(STDERR_FILENO);
    dup2(output[1], STDOUT_FILENO);
    dup2(output[1], STDERR_FILENO);
    close(output[1]);
    setvbuf(stdout, nullptr, _IOLBF, 0);
    std::thread logger(LogOutput, output[0]);
    UseCustomVulkanDriver(driver);
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
