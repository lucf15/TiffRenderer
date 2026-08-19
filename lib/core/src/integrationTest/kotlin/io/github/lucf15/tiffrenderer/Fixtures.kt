package io.github.lucf15.tiffrenderer

import com.goncalossilva.resources.Resource

internal expect fun openFixtureSource(name: String, bytes: ByteArray): TiffSource

internal object Fixtures {
    fun open(name: String): TiffSource = openFixtureSource(name, Resource(name).readBytes())
}

internal fun argb(a: Int, r: Int, g: Int, b: Int): Int = (a shl 24) or (r shl 16) or (g shl 8) or b

internal fun TiffBitmap.contentEquals(other: TiffBitmap): Boolean {
    if (width != other.width || height != other.height) return false
    for (y in 0 until height) {
        for (x in 0 until width) {
            if (pixelAt(x, y) != other.pixelAt(x, y)) return false
        }
    }
    return true
}
