/*
 * Copyright 2026 lucf15
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "jni_shared.h"

#include <cstdint>

namespace tiffrenderer {

namespace {
constexpr size_t kErrBufSize = 512;
}  // namespace

TiffCoreDocument* asDocument(jlong documentPtr) {
    return reinterpret_cast<TiffCoreDocument*>(static_cast<intptr_t>(documentPtr));
}

void throwException(JNIEnv* env, const char* className, const char* message) {
    jclass clazz = env->FindClass(className);
    if (clazz == nullptr) {
        return;  // FindClass already threw NoClassDefFoundError.
    }
    env->ThrowNew(clazz, message);
    env->DeleteLocalRef(clazz);
}

bool requireDocument(JNIEnv* env, jlong documentPtr) {
    if (documentPtr == 0) {
        throwException(env, "java/lang/IllegalStateException", "TIFF document is not open");
        return false;
    }
    return true;
}

void throwForStatus(JNIEnv* env, TiffCoreStatus status, const char* errBuf,
        const char* fallbackMessage) {
    const char* message = (errBuf != nullptr && errBuf[0] != '\0') ? errBuf : fallbackMessage;
    switch (status) {
        case TIFFCORE_ERROR_INVALID_ARG:
            throwException(env, "java/lang/IllegalArgumentException", message);
            break;
        case TIFFCORE_ERROR_ILLEGAL_STATE:
            throwException(env, "java/lang/IllegalStateException", message);
            break;
        case TIFFCORE_OK:
        case TIFFCORE_ERROR_IO:
        default:
            throwException(env, "java/io/IOException", message);
            break;
    }
}

void nativeClose(JNIEnv* /*env*/, jclass /*clazz*/, jlong documentPtr) {
    if (documentPtr == 0) {
        return;
    }
    tiffcore_close(asDocument(documentPtr));
}

jint nativeGetPageCount(JNIEnv* env, jclass /*clazz*/, jlong documentPtr) {
    if (!requireDocument(env, documentPtr)) {
        return 0;
    }
    return static_cast<jint>(tiffcore_get_page_count(asDocument(documentPtr)));
}

void nativeOpenPage(JNIEnv* env, jclass /*clazz*/, jlong documentPtr, jint pageIndex,
        jintArray outSize) {
    if (!requireDocument(env, documentPtr)) {
        return;
    }
    if (outSize == nullptr) {
        throwException(env, "java/lang/IllegalArgumentException", "outSize cannot be null");
        return;
    }

    uint32_t width = 0;
    uint32_t height = 0;
    char errBuf[kErrBufSize] = {};
    const TiffCoreStatus status = tiffcore_open_page(asDocument(documentPtr), pageIndex, &width,
            &height, errBuf, sizeof(errBuf));
    if (status != TIFFCORE_OK) {
        throwForStatus(env, status, errBuf, "cannot open TIFF page");
        return;
    }

    const jint size[2] = {static_cast<jint>(width), static_cast<jint>(height)};
    env->SetIntArrayRegion(outSize, 0, 2, size);
}

jboolean nativeRetainRaster(JNIEnv* env, jclass /*clazz*/, jlong documentPtr, jint pageIndex) {
    if (!requireDocument(env, documentPtr)) {
        return JNI_FALSE;
    }
    char errBuf[kErrBufSize] = {};
    const TiffCoreStatus status = tiffcore_retain_raster(asDocument(documentPtr), pageIndex,
            errBuf, sizeof(errBuf));
    if (status != TIFFCORE_OK && status != TIFFCORE_OK_PARTIAL) {
        throwForStatus(env, status, errBuf, "failed to decode TIFF page");
        return JNI_FALSE;
    }
    return status == TIFFCORE_OK_PARTIAL ? JNI_TRUE : JNI_FALSE;
}

void nativeReleaseRaster(JNIEnv* /*env*/, jclass /*clazz*/, jlong documentPtr) {
    if (documentPtr == 0) {
        return;
    }
    tiffcore_release_raster(asDocument(documentPtr));
}

}  // namespace tiffrenderer
