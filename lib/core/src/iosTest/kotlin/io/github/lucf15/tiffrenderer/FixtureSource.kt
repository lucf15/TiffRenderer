package io.github.lucf15.tiffrenderer

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSTemporaryDirectory
import platform.posix.O_RDONLY
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite
import platform.posix.open

@OptIn(ExperimentalForeignApi::class)
internal actual fun openFixtureSource(name: String, bytes: ByteArray): TiffSource {
    val path = NSTemporaryDirectory() + name

    val file = fopen(path, "wb") ?: error("failed to open $path for writing")
    bytes.usePinned { pinned ->
        val written = fwrite(pinned.addressOf(0), 1u.convert(), bytes.size.convert(), file)
        check(written.toLong() == bytes.size.toLong()) {
            "short write to $path: wrote $written of ${bytes.size} bytes"
        }
    }
    fclose(file)

    val fd = open(path, O_RDONLY)
    check(fd >= 0) { "failed to open $path" }
    return TiffSource.fromFileDescriptor(fd, bytes.size.toLong())
}
