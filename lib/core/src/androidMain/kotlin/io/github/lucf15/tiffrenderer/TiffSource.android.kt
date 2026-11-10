package io.github.lucf15.tiffrenderer

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import java.io.File

public actual class TiffSource private constructor(private val opener: () -> TiffOpenedSource) {
    internal actual fun acquire(): TiffOpenedSource = try { opener() } catch (error: Exception) {
        if (error is TiffIOException) throw error
        throw TiffIOException("Cannot open input file", TiffError.INPUT_UNAVAILABLE, error)
    }

    public actual companion object {
        public actual fun fromByteArray(bytes: ByteArray): TiffSource {
            requireTiffInputSize(bytes.size.toLong())
            val snapshot = bytes.copyOf()
            return TiffSource { TiffOpenedSource(null, snapshot, snapshot.size.toLong()) }
        }
        public fun fromFile(file: File): TiffSource = TiffSource {
            owned(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))
        }
        public fun fromUri(context: Context, uri: Uri): TiffSource {
            val resolver = context.applicationContext.contentResolver
            return TiffSource { owned(resolver.openFileDescriptor(uri, "r") ?: throw TiffIOException("Cannot open URI", TiffError.INPUT_UNAVAILABLE)) }
        }
        internal fun borrowed(pfd: ParcelFileDescriptor): TiffSource = TiffSource { owned(ParcelFileDescriptor.dup(pfd.fileDescriptor)) }
        private fun owned(pfd: ParcelFileDescriptor): TiffOpenedSource = try {
            Os.lseek(pfd.fileDescriptor, 0, OsConstants.SEEK_CUR)
            TiffOpenedSource(pfd, null, Os.fstat(pfd.fileDescriptor).st_size)
        } catch (t: Throwable) {
            pfd.close()
            throw t
        }
    }
}

internal actual class TiffOpenedSource(internal val pfd: ParcelFileDescriptor?, internal var bytes: ByteArray?, actual val size: Long) {
    internal val fd: Int get() = checkNotNull(pfd).fd
    actual val inMemory: Boolean = bytes != null
    actual fun close() { bytes = null; pfd?.close() }
}
