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

#include "jni_shared.h"
#include "tiff_core.h"

// asDocument/throwException/requireDocument/throwForStatus, and every native method whose
// signature doesn't depend on the destination-pixel type (nativeClose/nativeGetPageCount/
// nativeOpenPage/nativeRetainRaster/nativeReleaseRaster), live in jni_shared.cpp instead, shared
// verbatim with tiff_renderer_jni.cpp: only nativeOpen/nativeOpenBytes and nativeRenderPage
// genuinely differ per platform.

namespace tiffrenderer {

namespace {

constexpr size_t kErrBufSize = 512;

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

jboolean nativeRenderPage(JNIEnv* env, jclass /*clazz*/, jlong documentPtr, jint pageIndex,
        jobject destination, jint dstWidth, jint dstHeight, jint clipLeft, jint clipTop,
        jint clipRight, jint clipBottom, jfloatArray matrixValues, jint renderMode) {
    if (!requireDocument(env, documentPtr)) {
        return JNI_FALSE;
    }
    if (destination == nullptr) {
        throwException(env, "java/lang/IllegalArgumentException", "destination cannot be null");
        return JNI_FALSE;
    }
    if (matrixValues == nullptr || env->GetArrayLength(matrixValues) < 6) {
        throwException(env, "java/lang/IllegalArgumentException",
                "matrixValues must have 6 elements");
        return JNI_FALSE;
    }
    if (dstWidth <= 0 || dstHeight <= 0) {
        throwException(env, "java/lang/IllegalArgumentException",
                "dstWidth/dstHeight must be positive");
        return JNI_FALSE;
    }

    // destination is a direct java.nio.ByteBuffer (TiffBitmap.jvm's own off-heap storage): the
    // raw address is stable for as long as the Java-side ByteBuffer object is reachable, so unlike
    // an on-heap array there's nothing here for the JVM to pin, copy, or have GC work around.
    void* bufferAddress = env->GetDirectBufferAddress(destination);
    if (bufferAddress == nullptr) {
        throwException(env, "java/lang/IllegalArgumentException",
                "destination must be a direct ByteBuffer");
        return JNI_FALSE;
    }
    const jlong bufferCapacity = env->GetDirectBufferCapacity(destination);
    // dstWidth/dstHeight are only checked > 0 above, not upper-bounded: divide before multiplying
    // so an extreme (but individually valid) jint pair can't silently overflow int64_t and slip
    // past the capacity check below.
    constexpr int64_t kBytesPerPixel = 4;
    constexpr int64_t kInt64Max = INT64_MAX;
    const int64_t w = static_cast<int64_t>(dstWidth);
    const int64_t h = static_cast<int64_t>(dstHeight);
    if (w > (kInt64Max / kBytesPerPixel) / h) {
        throwException(env, "java/lang/IllegalArgumentException", "destination dimensions too large");
        return JNI_FALSE;
    }
    const int64_t requiredBytes = w * h * kBytesPerPixel;
    if (bufferCapacity < 0 || bufferCapacity < requiredBytes) {
        throwException(env, "java/lang/IllegalArgumentException",
                "destination smaller than dstWidth * dstHeight * 4 bytes");
        return JNI_FALSE;
    }

    jfloat matrix[6];
    env->GetFloatArrayRegion(matrixValues, 0, 6, matrix);

    char errBuf[kErrBufSize] = {};
    const TiffCoreStatus status = tiffcore_render_page(asDocument(documentPtr), pageIndex,
            reinterpret_cast<uint32_t*>(bufferAddress), dstWidth, dstWidth, dstHeight, clipLeft,
            clipTop, clipRight, clipBottom, matrix, static_cast<TiffCoreRenderMode>(renderMode),
            errBuf, sizeof(errBuf));

    // TIFFCORE_OK_PARTIAL means libtiff tolerated a decode error in part of the page (e.g. one bad
    // strip) and returned the rest of the raster anyway; treated as success, reported to the
    // caller via the return value instead of an exception.
    if (status != TIFFCORE_OK && status != TIFFCORE_OK_PARTIAL) {
        throwForStatus(env, status, errBuf, "failed to render TIFF page");
        return JNI_FALSE;
    }
    return status == TIFFCORE_OK_PARTIAL ? JNI_TRUE : JNI_FALSE;
}

// JNINativeMethod's name/signature fields are plain char* per the JNI spec (not const char*), so
// every string literal below needs an explicit const_cast rather than an implicit (and, under
// -Werror, rejected) literal-to-char* conversion.
#define JNI_STR(s) const_cast<char*>(s)

const JNINativeMethod gMethods[] = {
        {JNI_STR("nativeOpen"), JNI_STR("(Ljava/lang/String;)J"), reinterpret_cast<void*>(nativeOpen)},
        {JNI_STR("nativeOpenBytes"), JNI_STR("([B)J"), reinterpret_cast<void*>(nativeOpenBytes)},
        {JNI_STR("nativeClose"), JNI_STR("(J)V"), reinterpret_cast<void*>(tiffrenderer::nativeClose)},
        {JNI_STR("nativeGetPageCount"), JNI_STR("(J)I"),
                reinterpret_cast<void*>(tiffrenderer::nativeGetPageCount)},
        {JNI_STR("nativeOpenPage"), JNI_STR("(JI[I)V"),
                reinterpret_cast<void*>(tiffrenderer::nativeOpenPage)},
        {JNI_STR("nativeRenderPage"), JNI_STR("(JILjava/nio/ByteBuffer;IIIIII[FI)Z"),
                reinterpret_cast<void*>(nativeRenderPage)},
        {JNI_STR("nativeRetainRaster"), JNI_STR("(JI)Z"),
                reinterpret_cast<void*>(tiffrenderer::nativeRetainRaster)},
        {JNI_STR("nativeReleaseRaster"), JNI_STR("(J)V"),
                reinterpret_cast<void*>(tiffrenderer::nativeReleaseRaster)},
};

#undef JNI_STR

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
