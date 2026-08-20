package io.github.lucf15.tiffrenderer

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest

/** Thin JNI binding to `tiff_renderer_jni_jvm.cpp`; extracts the bundled native library to a content-addressed path since a classpath resource can't be handed to `System.load()` directly. */
internal object TiffRendererNativeJvm {
    init {
        loadNativeLibrary()
    }

    external fun nativeOpen(path: String): Long

    external fun nativeOpenBytes(bytes: ByteArray): Long

    external fun nativeClose(documentPtr: Long)

    external fun nativeGetPageCount(documentPtr: Long): Int

    external fun nativeOpenPage(documentPtr: Long, pageIndex: Int, outSize: IntArray)

    /** Returns `true` if libtiff tolerated a partial decode error somewhere in the page. */
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
    ): Boolean

    /** Returns `true` if libtiff tolerated a partial decode error somewhere in the page. */
    external fun nativeRetainRaster(documentPtr: Long, pageIndex: Int): Boolean

    external fun nativeReleaseRaster(documentPtr: Long)
}

private fun loadNativeLibrary() {
    val (os, libFileName) = osAndLibFileName(System.getProperty("os.name"))
    val arch = archName(System.getProperty("os.arch"))
    val resourceDir = "/natives/$os-$arch"

    val libraryBytes = readResource("$resourceDir/$libFileName")
    val expectedSha256 = readResource("$resourceDir/$libFileName.sha256").decodeToString().trim()
    check(sha256Hex(libraryBytes) == expectedSha256) {
        "bundled native library $resourceDir/$libFileName failed its own checksum"
    }

    val ownerOnly = os != "windows"
    val targetDir = stableNativeDir(System.getProperty("user.home"), expectedSha256.take(16), os, arch)
    createTargetDir(targetDir, ownerOnly)
    val targetFile = targetDir.resolve(libFileName)

    if (!Files.exists(targetFile) || sha256Hex(Files.readAllBytes(targetFile)) != expectedSha256) {
        extractAtomically(targetDir, targetFile, libraryBytes, ownerOnly)
        check(sha256Hex(Files.readAllBytes(targetFile)) == expectedSha256) {
            "extracted native library $targetFile failed its own checksum"
        }
    }

    System.load(targetFile.toAbsolutePath().toString())
}

private fun readResource(resourcePath: String): ByteArray =
    TiffRendererNativeJvm::class.java.getResourceAsStream(resourcePath)?.use { it.readBytes() }
        ?: throw UnsatisfiedLinkError("no bundled resource at $resourcePath")

private fun createTargetDir(targetDir: Path, ownerOnly: Boolean) {
    if (ownerOnly) {
        val ownerOnlyAttr = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))
        Files.createDirectories(targetDir, ownerOnlyAttr)
    } else {
        Files.createDirectories(targetDir)
    }
}

private fun extractAtomically(targetDir: Path, targetFile: Path, bytes: ByteArray, ownerOnly: Boolean) {
    val tempFile = Files.createTempFile(targetDir, "${targetFile.fileName}.", ".tmp")
    try {
        Files.write(tempFile, bytes)
        if (ownerOnly) {
            Files.setPosixFilePermissions(tempFile, PosixFilePermissions.fromString("rwx------"))
        }
        try {
            Files.move(tempFile, targetFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: IOException) {
            val existingHash = runCatching { sha256Hex(Files.readAllBytes(targetFile)) }.getOrDefault("<unreadable>")
            throw UnsatisfiedLinkError(
                "failed to replace $targetFile (existing sha256=$existingHash, expected sha256=${sha256Hex(bytes)}): ${e.message}",
            )
        }
    } finally {
        Files.deleteIfExists(tempFile)
    }
}

private fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/** Pure so it's directly testable without touching the real filesystem. */
internal fun stableNativeDir(homeDir: String, contentHashPrefix: String, os: String, arch: String): Path =
    Paths.get(homeDir, ".tiffrenderer", contentHashPrefix, "$os-$arch")

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
