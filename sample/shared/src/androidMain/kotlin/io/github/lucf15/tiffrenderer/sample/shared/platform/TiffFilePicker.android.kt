package io.github.lucf15.tiffrenderer.sample.shared.platform

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import io.github.lucf15.tiffrenderer.TiffSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun rememberTiffPicker(onPicked: (PickedTiff) -> Unit): () -> Unit {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val name = withContext(Dispatchers.IO) {
                try { context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                } } catch (_: Exception) { null }
            } ?: "Document.tiff"
            onPicked(PickedTiff(name, TiffSource.fromUri(context, uri)))
        }
    }
    return { launcher.launch(arrayOf("image/tiff", "image/x-tiff")) }
}
