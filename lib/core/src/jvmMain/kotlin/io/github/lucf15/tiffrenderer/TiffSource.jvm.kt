package io.github.lucf15.tiffrenderer

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

public actual class TiffSource private constructor(private val file: Path?, private val snapshot: ByteArray?) {
    internal actual fun acquire(): TiffOpenedSource = try {
        TiffOpenedSource(file?.toString(), snapshot, snapshot?.size?.toLong() ?: Files.size(checkNotNull(file)))
    } catch (error: IOException) {
        throw TiffIOException("Cannot open input file", TiffError.INPUT_UNAVAILABLE, error)
    }

    public actual companion object {
        public actual fun fromByteArray(bytes: ByteArray): TiffSource {
            requireTiffInputSize(bytes.size.toLong())
            return TiffSource(null, bytes.copyOf())
        }
        public fun fromPath(path: String): TiffSource = fromPath(Path.of(path))
        public fun fromPath(path: Path): TiffSource = TiffSource(path.toAbsolutePath(), null)
        public fun fromFile(file: File): TiffSource = fromPath(file.toPath())
    }
}

internal actual class TiffOpenedSource(internal val path: String?, internal var bytes: ByteArray?, actual val size: Long) {
    actual val inMemory: Boolean = bytes != null
    actual fun close() { bytes = null }
}
