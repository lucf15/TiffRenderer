package io.github.lucf15.tiffrenderer.sample.shared.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.uikit.LocalUIViewController
import io.github.lucf15.tiffrenderer.TiffSource
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.Foundation.NSURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.darwin.NSObject
import platform.posix.O_RDONLY
import platform.posix.open
import platform.posix.stat

@OptIn(ExperimentalForeignApi::class)
private class TiffDocumentPickerDelegate(
    private val onPicked: (String) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return
        val path = url.path ?: return
        onPicked(path)
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberTiffFilePickerLauncher(onPicked: (String) -> Unit): () -> Unit {
    val viewController = LocalUIViewController.current
    val delegate = remember { TiffDocumentPickerDelegate(onPicked) }
    return {
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeItem))
        picker.delegate = delegate
        viewController.presentViewController(picker, animated = true, completion = null)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun fileSize(path: String): Long = memScoped {
    val st = alloc<stat>()
    check(stat(path, st.ptr) == 0) { "cannot stat $path" }
    st.st_size
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberTiffSourceOpener(): (String) -> TiffSource = { path ->
    val fd = open(path, O_RDONLY)
    check(fd >= 0) { "cannot open $path" }
    TiffSource.fromFileDescriptor(fd, fileSize(path))
}
