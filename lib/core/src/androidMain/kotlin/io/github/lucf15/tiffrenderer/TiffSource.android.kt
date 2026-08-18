package io.github.lucf15.tiffrenderer

import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.IOException

actual class TiffSource private constructor(
    internal val pfd: ParcelFileDescriptor,
    internal val size: Long,
) {
    internal val fd: Int get() = pfd.fd

    actual fun release() {
        try {
            pfd.close()
        } catch (ignored: IOException) {
        }
    }

    companion object {
        fun fromFileDescriptor(fd: Int, size: Long): TiffSource =
            TiffSource(ParcelFileDescriptor.adoptFd(fd), size)

        /** Android-only convenience: wrap an existing [ParcelFileDescriptor] directly, avoiding
         * an fd adopt/detach round-trip for callers (e.g. from a `ContentResolver`) that already
         * have one. Stats it to recover the size, since this entry point (unlike
         * [fromFileDescriptor]) doesn't take one as a parameter. */
        fun fromParcelFileDescriptor(pfd: ParcelFileDescriptor): TiffSource {
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
