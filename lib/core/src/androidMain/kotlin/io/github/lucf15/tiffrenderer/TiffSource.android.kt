package io.github.lucf15.tiffrenderer

import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

public actual class TiffSource private constructor(
    internal val pfd: ParcelFileDescriptor,
    internal val size: Long,
) {
    internal val fd: Int get() = pfd.fd
    private val consumedFlag = AtomicBoolean(false)
    private val releasedFlag = AtomicBoolean(false)

    internal actual fun markConsumed(): Boolean = consumedFlag.compareAndSet(false, true)

    public actual fun release() {
        if (!releasedFlag.compareAndSet(false, true)) return
        try {
            pfd.close()
        } catch (ignored: IOException) {
        }
    }

    public companion object {
        public fun fromFileDescriptor(fd: Int, size: Long): TiffSource {
            require(size >= 0) { "size cannot be negative, was $size" }
            return TiffSource(ParcelFileDescriptor.adoptFd(fd), size)
        }

        /** Android-only convenience: wraps an existing [ParcelFileDescriptor] (e.g. from a
         * `ContentResolver`), stat'd for its size. Takes ownership: the owning [TiffRenderer]'s
         * close path closes this [pfd] too. */
        public fun fromParcelFileDescriptor(pfd: ParcelFileDescriptor): TiffSource {
            val size = try {
                Os.lseek(pfd.fileDescriptor, 0, OsConstants.SEEK_SET)
                Os.fstat(pfd.fileDescriptor).st_size
            } catch (e: ErrnoException) {
                throw IllegalArgumentException("file descriptor not seekable", e)
            }
            return TiffSource(pfd, size)
        }
    }
}
