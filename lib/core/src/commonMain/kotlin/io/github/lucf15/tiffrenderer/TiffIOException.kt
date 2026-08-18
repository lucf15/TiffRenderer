package io.github.lucf15.tiffrenderer

/**
 * Thrown when a page's compression scheme isn't supported by this build, or the underlying TIFF
 * data is otherwise corrupt or truncated. Kotlin has no common `IOException` type, so this is
 * what both platforms' native-decode failures get translated to.
 */
class TiffIOException(message: String) : RuntimeException(message)
