#pragma once

#include <jni.h>

namespace android_storage {

// Opens a browser-selected document for reading. The caller owns the fd.
// Returns -1 with a pending Java exception if the provider rejects the request.
int OpenReadOnly(JNIEnv* env, jobject storageTree, jstring relativePath);

}  // namespace android_storage
