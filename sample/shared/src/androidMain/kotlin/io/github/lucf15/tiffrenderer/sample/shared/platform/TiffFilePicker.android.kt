package io.github.lucf15.tiffrenderer.sample.shared.platform

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import io.github.lucf15.tiffrenderer.TiffSource
import java.io.IOException

@Composable
actual fun rememberTiffFilePickerLauncher(onPicked: (String) -> Unit): () -> Unit {
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) onPicked(uri.toString())
        }
    return { launcher.launch(arrayOf("*/*")) }
}

@Composable
actual fun rememberTiffSourceOpener(): (String) -> TiffSource {
    val context = LocalContext.current
    return { uriString ->
        val pfd =
            context.contentResolver.openFileDescriptor(Uri.parse(uriString), "r")
                ?: throw IOException("cannot open $uriString")
        TiffSource.fromParcelFileDescriptor(pfd)
    }
}
