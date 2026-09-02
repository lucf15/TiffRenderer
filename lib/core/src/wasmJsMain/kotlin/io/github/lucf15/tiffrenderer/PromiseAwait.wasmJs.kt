@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.lucf15.tiffrenderer

import kotlin.coroutines.resumeWithException
import kotlin.js.Promise
import kotlinx.coroutines.suspendCancellableCoroutine

internal suspend fun <T : JsAny?> Promise<T>.await(): T =
    suspendCancellableCoroutine { continuation ->
        then<JsAny?>(
            onFulfilled = { value: T -> continuation.resume(value) { _, _, _ -> }; null },
            onRejected = { reason: JsAny ->
                continuation.resumeWithException(RuntimeException(reason.toString()))
                null
            },
        )
    }
