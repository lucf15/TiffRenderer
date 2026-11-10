package io.github.lucf15.tiffrenderer.sample.shared.platform

internal actual val primaryShortcutLabel: String =
    if (System.getProperty("os.name").startsWith("Mac", ignoreCase = true)) "⌘" else "Ctrl"

internal actual val nativeWindowControlsWidth: Float = if (System.getProperty("os.name").startsWith("Mac", ignoreCase=true)) 80f else 0f
