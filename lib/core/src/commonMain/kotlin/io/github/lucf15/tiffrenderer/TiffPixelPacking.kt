package io.github.lucf15.tiffrenderer

/** Packs 4 consecutive RGBA8888 bytes starting at [offset] (read via [byteAt]) into this
 * library's packed-ARGB [pixelAt] convention. Shared by every byte-indexed [TiffBitmap] actual
 * (JVM, wasmJs); iOS stores pixels pre-packed as ints instead, so it doesn't need this. */
internal inline fun packArgbFromRgbaBytes(offset: Int, byteAt: (Int) -> Byte): Int {
    val r = byteAt(offset).toInt() and 0xFF
    val g = byteAt(offset + 1).toInt() and 0xFF
    val b = byteAt(offset + 2).toInt() and 0xFF
    val a = byteAt(offset + 3).toInt() and 0xFF
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

/** Same repacking as [packArgbFromRgbaBytes], but from one RGBA8888-packed int (iOS's storage:
 * `r | g<<8 | b<<16 | a<<24`) instead of 4 indexed bytes. */
internal fun packArgbFromRgbaPackedInt(p: Int): Int {
    val r = p and 0xFF
    val g = (p ushr 8) and 0xFF
    val b = (p ushr 16) and 0xFF
    val a = (p ushr 24) and 0xFF
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}
