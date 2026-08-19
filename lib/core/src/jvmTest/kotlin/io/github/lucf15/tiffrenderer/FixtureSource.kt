package io.github.lucf15.tiffrenderer

import java.nio.file.Files

internal actual fun openFixtureSource(name: String, bytes: ByteArray): TiffSource {
    val tempFile = Files.createTempFile("tiffrenderer_fixture_", "_$name")
    tempFile.toFile().deleteOnExit()
    Files.write(tempFile, bytes)
    return TiffSource.fromFile(tempFile.toFile())
}
