package io.github.lucf15.tiffrenderer.sample.desktop

import java.nio.file.Files

/** Small AppKit bridge. Invoked only for the macOS presenter, after AWT creates its peer. */
internal object MacWindowControls {
    init {
        val library = Files.createTempFile("tiff-window-controls-", ".dylib")
        MacWindowControls::class.java.getResourceAsStream("/native/macos/libtiffwindow.dylib").use { source ->
            requireNotNull(source) { "Missing AppKit window-controls library" }
            Files.copy(source, library, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
        library.toFile().deleteOnExit()
        System.load(library.toAbsolutePath().toString())
    }
    external fun install(handle: Long)
    external fun activate(handle: Long)
    external fun titleRegion(handle: Long, left: Double, top: Double, width: Double, height: Double)
    external fun positions(handle: Long): DoubleArray
}
