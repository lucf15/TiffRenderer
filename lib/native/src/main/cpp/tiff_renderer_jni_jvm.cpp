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

#include <jni.h>

#include <cstdint>

#include "tiff_core.h"

namespace tiffrenderer {

namespace {

constexpr size_t kErrBufSize = 512;

TiffCoreDocument* asDocument(jlong documentPtr) {
    return reinterpret_cast<TiffCoreDocument*>(static_cast<intptr_t>(documentPtr));
}

void throwException(JNIEnv* env, const char* className, const char* message) {
    jclass clazz = env->FindClass(className);
    if (clazz == nullptr) {
        return;
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

jlong nativeOpen(JNIEnv* env, jclass /*clazz*/, jstring path) {
    if (path == nullptr) {
        throwException(env, "java/lang/IllegalArgumentException", "path cannot be null");
        return 0;
    }

    TiffCoreDocument* doc = nullptr;
    char errBuf[kErrBufSize] = {};
    TiffCoreStatus status;

#ifdef _WIN32
    // JNI strings are natively UTF-16, same as Windows wchar_t: pass straight through to
    // TIFFOpenW rather than round-tripping through UTF-8, which mangles non-ASCII paths on
    // Windows narrow fopen().
    const jchar* pathChars = env->GetStringChars(path, nullptr);
    if (pathChars == nullptr) {
        return 0;
    }
    status = tiffcore_open_path_w(reinterpret_cast<const wchar_t*>(pathChars), &doc, errBuf,
            sizeof(errBuf));
    env->ReleaseStringChars(path, pathChars);
#else
    const char* utf8Path = env->GetStringUTFChars(path, nullptr);
    if (utf8Path == nullptr) {
        return 0;
    }
    status = tiffcore_open_path(utf8Path, &doc, errBuf, sizeof(errBuf));
    env->ReleaseStringUTFChars(path, utf8Path);
#endif

    if (status != TIFFCORE_OK) {
        throwForStatus(env, status, errBuf, "cannot open TIFF");
        return 0;
    }
    return static_cast<jlong>(reinterpret_cast<intptr_t>(doc));
}

jlong nativeOpenBytes(JNIEnv* env, jclass /*clazz*/, jbyteArray bytes) {
    if (bytes == nullptr) {
        throwException(env, "java/lang/IllegalArgumentException", "bytes cannot be null");
        return 0;
    }
    const jsize length = env->GetArrayLength(bytes);

    jbyte* data = env->GetByteArrayElements(bytes, nullptr);
    if (data == nullptr) {
        return 0;
    }

    TiffCoreDocument* doc = nullptr;
    char errBuf[kErrBufSize] = {};
    const TiffCoreStatus status = tiffcore_open_memory(reinterpret_cast<const uint8_t*>(data),
            static_cast<int64_t>(length), &doc, errBuf, sizeof(errBuf));

    env->ReleaseByteArrayElements(bytes, data, JNI_ABORT);

    if (status != TIFFCORE_OK) {
        throwForStatus(env, status, errBuf, "cannot open TIFF");
        return 0;
    }
    return static_cast<jlong>(reinterpret_cast<intptr_t>(doc));
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

void nativeRenderPage(JNIEnv* env, jclass /*clazz*/, jlong documentPtr, jint pageIndex,
        jintArray destination, jint dstWidth, jint dstHeight, jint clipLeft, jint clipTop,
        jint clipRight, jint clipBottom, jfloatArray matrixValues, jint renderMode) {
    if (!requireDocument(env, documentPtr)) {
        return;
    }
    if (destination == nullptr) {
        throwException(env, "java/lang/IllegalArgumentException", "destination cannot be null");
        return;
    }
    if (matrixValues == nullptr || env->GetArrayLength(matrixValues) < 6) {
        throwException(env, "java/lang/IllegalArgumentException",
                "matrixValues must have 6 elements");
        return;
    }
    if (dstWidth <= 0 || dstHeight <= 0) {
        throwException(env, "java/lang/IllegalArgumentException",
                "dstWidth/dstHeight must be positive");
        return;
    }
    const int64_t requiredLength = static_cast<int64_t>(dstWidth) * static_cast<int64_t>(dstHeight);
    if (env->GetArrayLength(destination) < requiredLength) {
        throwException(env, "java/lang/IllegalArgumentException",
                "destination smaller than dstWidth * dstHeight");
        return;
    }

    jfloat matrix[6];
    env->GetFloatArrayRegion(matrixValues, 0, 6, matrix);

    jint* pixels = static_cast<jint*>(env->GetPrimitiveArrayCritical(destination, nullptr));
    if (pixels == nullptr) {
        return;
    }

    char errBuf[kErrBufSize] = {};
    const TiffCoreStatus status = tiffcore_render_page(asDocument(documentPtr), pageIndex,
            reinterpret_cast<uint32_t*>(pixels), dstWidth, dstWidth, dstHeight, clipLeft, clipTop,
            clipRight, clipBottom, matrix, static_cast<TiffCoreRenderMode>(renderMode), errBuf,
            sizeof(errBuf));

    env->ReleasePrimitiveArrayCritical(destination, pixels, status == TIFFCORE_OK ? 0 : JNI_ABORT);

    if (status != TIFFCORE_OK) {
        throwForStatus(env, status, errBuf, "failed to render TIFF page");
    }
}

void nativeRetainRaster(JNIEnv* env, jclass /*clazz*/, jlong documentPtr, jint pageIndex) {
    if (!requireDocument(env, documentPtr)) {
        return;
    }
    char errBuf[kErrBufSize] = {};
    const TiffCoreStatus status = tiffcore_retain_raster(asDocument(documentPtr), pageIndex,
            errBuf, sizeof(errBuf));
    if (status != TIFFCORE_OK) {
        throwForStatus(env, status, errBuf, "failed to decode TIFF page");
    }
}

void nativeReleaseRaster(JNIEnv* /*env*/, jclass /*clazz*/, jlong documentPtr) {
    if (documentPtr == 0) {
        return;
    }
    tiffcore_release_raster(asDocument(documentPtr));
}

const JNINativeMethod gMethods[] = {
        {"nativeOpen", "(Ljava/lang/String;)J", reinterpret_cast<void*>(nativeOpen)},
        {"nativeOpenBytes", "([B)J", reinterpret_cast<void*>(nativeOpenBytes)},
        {"nativeClose", "(J)V", reinterpret_cast<void*>(nativeClose)},
        {"nativeGetPageCount", "(J)I", reinterpret_cast<void*>(nativeGetPageCount)},
        {"nativeOpenPage", "(JI[I)V", reinterpret_cast<void*>(nativeOpenPage)},
        {"nativeRenderPage", "(JI[IIIIIII[FI)V", reinterpret_cast<void*>(nativeRenderPage)},
        {"nativeRetainRaster", "(JI)V", reinterpret_cast<void*>(nativeRetainRaster)},
        {"nativeReleaseRaster", "(J)V", reinterpret_cast<void*>(nativeReleaseRaster)},
};

}  // namespace

}  // namespace tiffrenderer

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* /*reserved*/) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    tiffcore_global_init();

    jclass clazz = env->FindClass("io/github/lucf15/tiffrenderer/TiffRendererNativeJvm");
    if (clazz == nullptr) {
        return JNI_ERR;
    }
    const jint result = env->RegisterNatives(clazz, tiffrenderer::gMethods,
            sizeof(tiffrenderer::gMethods) / sizeof(tiffrenderer::gMethods[0]));
    env->DeleteLocalRef(clazz);
    if (result != JNI_OK) {
        return JNI_ERR;
    }

    return JNI_VERSION_1_6;
}
