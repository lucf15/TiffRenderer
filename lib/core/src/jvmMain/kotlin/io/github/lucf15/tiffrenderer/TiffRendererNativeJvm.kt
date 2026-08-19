package io.github.lucf15.tiffrenderer

import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Thin JNI binding to `tiff_renderer_jni_jvm.cpp`. Extracts the bundled per-OS/arch native
 * library from classpath resources to a temp file and loads it, since a plain classpath resource
 * can't be handed to `System.load()` directly. */
internal object TiffRendererNativeJvm {
    init {
        loadNativeLibrary()
    }

    external fun nativeOpen(path: String): Long

    external fun nativeOpenBytes(bytes: ByteArray): Long

    external fun nativeClose(documentPtr: Long)

    external fun nativeGetPageCount(documentPtr: Long): Int

    external fun nativeOpenPage(documentPtr: Long, pageIndex: Int, outSize: IntArray)

    external fun nativeRenderPage(
        documentPtr: Long,
        pageIndex: Int,
        destination: ByteBuffer,
        dstWidth: Int,
        dstHeight: Int,
        clipLeft: Int,
        clipTop: Int,
        clipRight: Int,
        clipBottom: Int,
        matrixValues: FloatArray,
        renderMode: Int,
    )

    external fun nativeRetainRaster(documentPtr: Long, pageIndex: Int)

    external fun nativeReleaseRaster(documentPtr: Long)
}

private fun loadNativeLibrary() {
    val (os, libFileName) = osAndLibFileName(System.getProperty("os.name"))
    val arch = archName(System.getProperty("os.arch"))
    val resourcePath = "/natives/$os-$arch/$libFileName"
    val resource = TiffRendererNativeJvm::class.java.getResourceAsStream(resourcePath)
        ?: throw UnsatisfiedLinkError("no bundled native library at $resourcePath")

    val tempFile = Files.createTempFile("tiffrenderer_jni_jvm", ".${libFileName.substringAfterLast('.')}")
    tempFile.toFile().deleteOnExit()
    resource.use { input -> Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING) }
    System.load(tempFile.toAbsolutePath().toString())
}

/** Pure so the branch match (including the unsupported-OS fallthrough) is directly testable
 * without faking `System.getProperty`. */
internal fun osAndLibFileName(rawOsName: String): Pair<String, String> {
    val osName = rawOsName.lowercase()
    return when {
        osName.contains("mac") -> "macos" to "libtiffrenderer_jni_jvm.dylib"
        osName.contains("win") -> "windows" to "tiffrenderer_jni_jvm.dll"
        osName.contains("linux") -> "linux" to "libtiffrenderer_jni_jvm.so"
        else -> throw UnsatisfiedLinkError("unsupported OS: $osName")
    }
}

/** Pure so the branch match (including the unsupported-arch fallthrough) is directly testable
 * without faking `System.getProperty`. */
internal fun archName(rawArchName: String): String =
    when (val archNameLower = rawArchName.lowercase()) {
        "aarch64", "arm64" -> "aarch64"
        "x86_64", "amd64" -> "x86_64"
        else -> throw UnsatisfiedLinkError("unsupported architecture: $archNameLower")
    }
