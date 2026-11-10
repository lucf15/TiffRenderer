package io.github.lucf15.tiffrenderer.sample.shared.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.uikit.LocalUIViewController
import io.github.lucf15.tiffrenderer.TiffSource
import platform.Foundation.NSURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypeTIFF
import platform.darwin.NSObject

private class PickerDelegate(val picked: (PickedTiff) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return
        picked(PickedTiff(url.lastPathComponent ?: "Document.tiff", TiffSource.fromUrl(url)))
    }
}

@Composable
actual fun rememberTiffPicker(onPicked: (PickedTiff) -> Unit): () -> Unit {
    val controller = LocalUIViewController.current
    val delegate = remember(onPicked) { PickerDelegate(onPicked) }
    return { controller.presentViewController(UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeTIFF)).also { it.delegate = delegate }, true, null) }
}
