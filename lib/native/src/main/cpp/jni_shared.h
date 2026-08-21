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

#ifndef TIFFRENDERER_JNI_SHARED_H
#define TIFFRENDERER_JNI_SHARED_H

#include <jni.h>

#include "tiff_core.h"

// JNI scaffolding shared between the Android (tiff_renderer_jni.cpp) and desktop JVM
// (tiff_renderer_jni_jvm.cpp) shims: documentPtr/pageIndex are always jlong/jint regardless of
// platform, so these are identical on both. Only nativeOpen*/nativeRenderPage differ per platform
// (fd vs. path/bytes; android.graphics.Bitmap vs. java.nio.ByteBuffer), so those stay in each
// shim's own file and are registered alongside these in each file's own gMethods table.

namespace tiffrenderer {

TiffCoreDocument* asDocument(jlong documentPtr);

void throwException(JNIEnv* env, const char* className, const char* message);

// documentPtr == 0 means a caller bypassed the Kotlin wrapper (e.g. via reflection); throw instead
// of segfaulting.
bool requireDocument(JNIEnv* env, jlong documentPtr);

void throwForStatus(JNIEnv* env, TiffCoreStatus status, const char* errBuf,
        const char* fallbackMessage);

void nativeClose(JNIEnv* env, jclass clazz, jlong documentPtr);

jint nativeGetPageCount(JNIEnv* env, jclass clazz, jlong documentPtr);

void nativeOpenPage(JNIEnv* env, jclass clazz, jlong documentPtr, jint pageIndex,
        jintArray outSize);

// Decodes pageIndex now and caches it so subsequent nativeRenderPage calls reuse it; see
// Page#retainRaster(). Returns true if libtiff tolerated a partial decode error somewhere in the
// page.
jboolean nativeRetainRaster(JNIEnv* env, jclass clazz, jlong documentPtr, jint pageIndex);

// Frees whatever nativeRetainRaster cached; safe for Page#close() to call unconditionally.
void nativeReleaseRaster(JNIEnv* env, jclass clazz, jlong documentPtr);

}  // namespace tiffrenderer

#endif  // TIFFRENDERER_JNI_SHARED_H
