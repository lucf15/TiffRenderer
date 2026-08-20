package io.github.lucf15.tiffrenderer

/** Thrown when a page's codec isn't supported by this build, or the TIFF data is corrupt or
 * truncated. Kotlin has no common `IOException`, so native-decode failures translate to this. */
public class TiffIOException(message: String) : RuntimeException(message)
