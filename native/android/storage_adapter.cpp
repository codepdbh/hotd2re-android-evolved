#include "storage_adapter.h"

#include <array>
#include <cerrno>
#include <cstdint>
#include <cstring>
#include <sys/stat.h>
#include <unistd.h>

int android_storage::OpenReadOnly(JNIEnv* env, jobject storageTree, jstring relativePath) {
    jclass treeClass = env->GetObjectClass(storageTree);
    if (!treeClass) return -1;
    const jmethodID open = env->GetMethodID(treeClass, "openReadFd", "(Ljava/lang/String;)I");
    env->DeleteLocalRef(treeClass);
    if (!open) return -1;
    const jint fd = env->CallIntMethod(storageTree, open, relativePath);
    return env->ExceptionCheck() ? -1 : fd;
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_dev_sdlprobe_android_StorageActivity_nativeInspect(JNIEnv* env, jclass, jobject tree, jstring path) {
    const int fd = android_storage::OpenReadOnly(env, tree, path);
    if (fd < 0) return nullptr;

    std::array<std::uint8_t, 4096> sample{};
    std::size_t size = 0;
    int readError = 0;
    while (size < sample.size()) {
        const ssize_t count = read(fd, sample.data() + size, sample.size() - size);
        if (count == 0) break;
        if (count < 0) {
            if (errno == EINTR) continue;
            readError = errno;
            break;
        }
        size += static_cast<std::size_t>(count);
    }

    struct stat info{};
    const jlong fileSize = fstat(fd, &info) == 0 && S_ISREG(info.st_mode) ? info.st_size : -1;
    close(fd);
    if (readError) {
        if (jclass type = env->FindClass("java/io/IOException")) {
            env->ThrowNew(type, std::strerror(readError));
        }
        return nullptr;
    }

    // FNV-1a over the bytes read gives the diagnostic a compact test value.
    std::uint64_t fingerprint = 14695981039346656037ull;
    for (std::size_t i = 0; i < size; ++i) {
        fingerprint ^= sample[i];
        fingerprint *= 1099511628211ull;
    }
    const jlong values[]{fileSize, static_cast<jlong>(size), static_cast<jlong>(fingerprint)};
    jlongArray result = env->NewLongArray(3);
    if (result) env->SetLongArrayRegion(result, 0, 3, values);
    return result;
}
