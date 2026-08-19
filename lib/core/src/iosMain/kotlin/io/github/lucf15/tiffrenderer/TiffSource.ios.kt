package io.github.lucf15.tiffrenderer

import platform.posix.close

actual class TiffSource private constructor(internal val fd: Int, internal val size: Long) {
    internal actual var consumed: Boolean = false

    actual fun release() {
        close(fd)
    }

    companion object {
        fun fromFileDescriptor(fd: Int, size: Long): TiffSource = TiffSource(fd, size)
    }
}
