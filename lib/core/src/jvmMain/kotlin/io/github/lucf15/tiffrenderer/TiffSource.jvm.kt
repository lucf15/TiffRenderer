package io.github.lucf15.tiffrenderer

import java.io.File

actual class TiffSource private constructor(
    internal val path: String?,
    internal var bytes: ByteArray?,
) {
    internal actual var consumed: Boolean = false

    actual fun release() {
    }

    companion object {
        fun fromPath(path: String): TiffSource = TiffSource(path, null)

        fun fromFile(file: File): TiffSource = TiffSource(file.absolutePath, null)

        fun fromByteArray(bytes: ByteArray): TiffSource = TiffSource(null, bytes)
    }
}
