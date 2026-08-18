package io.github.lucf15.tiffrenderer

import java.io.File

actual class TiffSource private constructor(internal val path: String) {
    actual fun release() {
    }

    companion object {
        fun fromPath(path: String): TiffSource = TiffSource(path)

        fun fromFile(file: File): TiffSource = TiffSource(file.absolutePath)
    }
}
