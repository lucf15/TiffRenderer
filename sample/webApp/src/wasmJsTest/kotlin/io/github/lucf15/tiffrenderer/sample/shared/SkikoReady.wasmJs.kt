@file:JsModule("./skiko.mjs")
@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.lucf15.tiffrenderer.sample.shared

import kotlin.js.Promise

internal external val awaitSkiko: Promise<JsAny>
