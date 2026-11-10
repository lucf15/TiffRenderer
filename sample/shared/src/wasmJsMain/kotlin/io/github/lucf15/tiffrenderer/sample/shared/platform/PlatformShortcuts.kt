@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.lucf15.tiffrenderer.sample.shared.platform

internal actual val primaryShortcutLabel: String
    get() = if (macKeyboard()) "⌘" else "Ctrl"

private fun macKeyboard(): Boolean = js("/Mac|iPhone|iPad/.test(navigator.platform)")

internal actual val nativeWindowControlsWidth: Float = 0f
