package io.github.lucf15.tiffrenderer.sample.shared.platform

import androidx.compose.runtime.Composable
import io.github.lucf15.tiffrenderer.TiffSource
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
actual fun rememberTiffFilePickerLauncher(onPicked: (String) -> Unit): () -> Unit = {
    val chooser = JFileChooser().apply {
        fileFilter = FileNameExtensionFilter("TIFF files", "tif", "tiff")
    }
    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        onPicked(chooser.selectedFile.absolutePath)
    }
}

@Composable
actual fun rememberTiffSourceOpener(): (String) -> TiffSource = { path -> TiffSource.fromPath(path) }
