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

#include <android/bitmap.h>
#include <jni.h>

#include <cstdint>

#include "jni_shared.h"
#include "tiff_core.h"

// Thin JNI shim: arg marshaling, AndroidBitmap pixel access, and translating TiffCoreStatus into
// Java exceptions. All libtiff-facing logic lives in tiff_core.cpp/.h, which has no JNI or
// Android dependency, so it's also bound directly from the iOS cinterop layer. Scaffolding shared
// with the JVM desktop shim lives in jni_shared.h/.cpp instead; only nativeOpen and
// nativeRenderPage genuinely differ per platform.

namespace tiffrenderer {

namespace {

constexpr size_t kErrBufSize = 512;

jlong nativeOpen(JNIEnv* env, jclass /*clazz*/, jint fd, jlong size) {
    TiffCoreDocument* doc = nullptr;
    char errBuf[kErrBufSize] = {};
    const TiffCoreStatus status = tiffcore_open(fd, size, &doc, errBuf, sizeof(errBuf));
    if (status != TIFFCORE_OK) {
        throwForStatus(env, status, errBuf, "cannot open TIFF");
        return 0;
    }
    return static_cast<jlong>(reinterpret_cast<intptr_t>(doc));
}

jboolean nativeRenderPage(JNIEnv* env, jclass /*clazz*/, jlong documentPtr, jint pageIndex,
        jobject bitmap, jint clipLeft, jint clipTop, jint clipRight, jint clipBottom,
        jfloatArray matrixValues, jint renderMode) {
    if (!requireDocument(env, documentPtr)) {
        return JNI_FALSE;
    }
    if (bitmap == nullptr) {
        throwException(env, "java/lang/IllegalArgumentException", "destination cannot be null");
        return JNI_FALSE;
    }
    if (matrixValues == nullptr || env->GetArrayLength(matrixValues) < 9) {
        throwException(env, "java/lang/IllegalArgumentException",
                "matrixValues must have 9 elements");
        return JNI_FALSE;
    }

    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) {
        throwException(env, "java/lang/IllegalStateException", "cannot read destination bitmap info");
        return JNI_FALSE;
    }
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        throwException(env, "java/lang/IllegalArgumentException",
                "destination bitmap must be ARGB_8888");
        return JNI_FALSE;
    }

    jfloat matrix[9];
    env->GetFloatArrayRegion(matrixValues, 0, 9, matrix);

    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
        throwException(env, "java/lang/IllegalStateException", "cannot lock destination bitmap");
        return JNI_FALSE;
    }

    char errBuf[kErrBufSize] = {};
    // matrix[] is Matrix#getValues() order (9 elements); tiff_core's affine API only needs the
    // first 6 (the row-3 perspective terms are already rejected as non-affine on the Java side).
    const TiffCoreStatus status = tiffcore_render_page(asDocument(documentPtr), pageIndex,
            reinterpret_cast<uint32_t*>(pixels), static_cast<int32_t>(info.stride) / 4,
            static_cast<int32_t>(info.width), static_cast<int32_t>(info.height), clipLeft, clipTop,
            clipRight, clipBottom, matrix, static_cast<TiffCoreRenderMode>(renderMode), errBuf,
            sizeof(errBuf));

    AndroidBitmap_unlockPixels(env, bitmap);

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
        {JNI_STR("nativeOpen"), JNI_STR("(IJ)J"), reinterpret_cast<void*>(nativeOpen)},
        {JNI_STR("nativeClose"), JNI_STR("(J)V"), reinterpret_cast<void*>(tiffrenderer::nativeClose)},
        {JNI_STR("nativeGetPageCount"), JNI_STR("(J)I"),
                reinterpret_cast<void*>(tiffrenderer::nativeGetPageCount)},
        {JNI_STR("nativeOpenPage"), JNI_STR("(JI[I)V"),
                reinterpret_cast<void*>(tiffrenderer::nativeOpenPage)},
        {JNI_STR("nativeRenderPage"),
                JNI_STR("(JILandroid/graphics/Bitmap;IIII[FI)Z"),
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

    jclass clazz = env->FindClass("io/github/lucf15/tiffrenderer/TiffRendererNative");
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
