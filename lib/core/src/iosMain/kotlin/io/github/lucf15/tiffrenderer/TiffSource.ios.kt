package io.github.lucf15.tiffrenderer

import platform.Foundation.NSLock
import platform.posix.close

public actual class TiffSource private constructor(internal val fd: Int, internal val size: Long) {
    private val consumedLock = NSLock()
    private var consumedFlag = false
    private val releasedLock = NSLock()
    private var releasedFlag = false

    internal actual fun markConsumed(): Boolean {
        consumedLock.lock()
        try {
            if (consumedFlag) return false
            consumedFlag = true
            return true
        } finally {
            consumedLock.unlock()
        }
    }

    internal actual fun release() {
        releasedLock.lock()
        try {
            if (releasedFlag) return
            releasedFlag = true
        } finally {
            releasedLock.unlock()
        }
        close(fd)
    }

    public companion object {
        public fun fromFileDescriptor(fd: Int, size: Long): TiffSource = TiffSource(fd, size)
    }
}
