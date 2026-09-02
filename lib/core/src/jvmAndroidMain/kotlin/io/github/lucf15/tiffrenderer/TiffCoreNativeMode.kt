package io.github.lucf15.tiffrenderer

// Mirrors tiff_core.h's TiffCoreRenderMode enum: no cinterop on JVM/Android to bind it
// automatically the way iOS does.
private const val TIFFCORE_RENDER_MODE_DISPLAY = 1
private const val TIFFCORE_RENDER_MODE_PRINT = 2

internal fun TiffRenderMode.toNativeMode(): Int = when (this) {
    TiffRenderMode.FOR_DISPLAY -> TIFFCORE_RENDER_MODE_DISPLAY
    TiffRenderMode.FOR_PRINT -> TIFFCORE_RENDER_MODE_PRINT
}
