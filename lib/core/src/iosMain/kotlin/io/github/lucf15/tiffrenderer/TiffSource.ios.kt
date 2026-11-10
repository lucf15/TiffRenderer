@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.lucf15.tiffrenderer

import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.Foundation.NSURL
import platform.posix.O_RDONLY
import platform.posix.SEEK_CUR
import platform.posix.close
import platform.posix.fstat
import platform.posix.lseek
import platform.posix.open
import platform.posix.stat

public actual class TiffSource private constructor(private val file: NSURL?, private val snapshot: ByteArray?) {
    internal actual fun acquire(): TiffOpenedSource {
        snapshot?.let { return TiffOpenedSource(-1, it, it.size.toLong(), null) }
        val url = checkNotNull(file)
        val scoped = url.startAccessingSecurityScopedResource()
        var fd = -1
        try {
            fd = open(checkNotNull(url.path), O_RDONLY)
            if (fd < 0 || lseek(fd, 0, SEEK_CUR) < 0) throw TiffIOException("Cannot open seekable file", TiffError.INPUT_UNAVAILABLE)
            val size = memScoped {
                val info = alloc<stat>()
                if (fstat(fd, info.ptr) != 0) throw TiffIOException("Cannot inspect file", TiffError.INPUT_UNAVAILABLE)
                info.st_size
            }
            return TiffOpenedSource(fd, null, size, if (scoped) url else null)
        } catch (t: Throwable) {
            if (fd >= 0) close(fd)
            if (scoped) url.stopAccessingSecurityScopedResource()
            throw t
        }
    }
    public actual companion object {
        public actual fun fromByteArray(bytes: ByteArray): TiffSource {
            requireTiffInputSize(bytes.size.toLong())
            return TiffSource(null, bytes.copyOf())
        }
        public fun fromPath(path: String): TiffSource = fromUrl(NSURL.fileURLWithPath(path))
        public fun fromUrl(url: NSURL): TiffSource {
            require(url.isFileURL()) { "Expected a file URL" }
            return TiffSource(url, null)
        }
    }
}

internal actual class TiffOpenedSource(internal val fd: Int, internal var bytes: ByteArray?, actual val size: Long, private val scopedUrl: NSURL?) {
    actual val inMemory: Boolean = bytes != null
    actual fun close() {
        bytes = null
        try { if (fd >= 0) close(fd) } finally { scopedUrl?.stopAccessingSecurityScopedResource() }
    }
}
