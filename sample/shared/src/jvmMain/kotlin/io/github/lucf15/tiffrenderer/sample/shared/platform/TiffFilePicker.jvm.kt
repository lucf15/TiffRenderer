package io.github.lucf15.tiffrenderer.sample.shared.platform

import androidx.compose.runtime.Composable
import io.github.lucf15.tiffrenderer.TiffSource
import java.awt.FileDialog
import java.awt.Frame
import java.awt.KeyboardFocusManager
import javax.swing.SwingUtilities

@Composable
actual fun rememberTiffPicker(onPicked: (PickedTiff) -> Unit): () -> Unit = {
    SwingUtilities.invokeLater {
        val owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow as? Frame
        val dialog = FileDialog(owner, "Open TIFF", FileDialog.LOAD)
        try {
            dialog.setFilenameFilter { _, name -> name.endsWith(".tif", true) || name.endsWith(".tiff", true) }
            dialog.isVisible = true
            dialog.files.firstOrNull()?.let { file -> onPicked(PickedTiff(file.name, TiffSource.fromFile(file))) }
        } finally {
            dialog.dispose()
        }
    }
}
