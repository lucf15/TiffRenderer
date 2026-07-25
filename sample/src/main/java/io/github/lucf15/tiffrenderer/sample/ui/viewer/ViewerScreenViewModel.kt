package io.github.lucf15.tiffrenderer.sample.ui.viewer

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.createBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.lucf15.tiffrenderer.TiffRenderer
import io.github.lucf15.tiffrenderer.sample.data.openTiffFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ViewerScreenViewModel(
    application: Application,
    private val uri: Uri,
) : AndroidViewModel(application) {

    private val pageLock = Mutex()
    private var renderer: TiffRenderer? = null

    var uiState: ViewerUiState by mutableStateOf(ViewerUiState.Loading)
        private set

    init {
        viewModelScope.launch {
            uiState =
                withContext(Dispatchers.IO) {
                    runCatching {
                        val opened = TiffRenderer(openTiffFile(getApplication(), uri))
                        renderer = opened
                        val pageSizes =
                            (0 until opened.pageCount).map { index ->
                                opened.openPage(index).use { page -> IntSize(page.width, page.height) }
                            }
                        pageSizes
                    }
                        .fold(
                            onSuccess = { sizes -> ViewerUiState.Loaded(sizes.size, sizes) },
                            onFailure = { e ->
                                ViewerUiState.Failed(
                                    e.message ?: "failed to open TIFF"
                                )
                            },
                        )
                }
        }
    }

    suspend fun renderPage(index: Int): Bitmap =
        withContext(Dispatchers.IO) {
            pageLock.withLock {
                val document =
                    checkNotNull(renderer) { "renderPage() called before the document opened" }
                document.openPage(index).use { page ->
                    val bitmap = createBitmap(page.width, page.height)
                    page.render(bitmap, null, null, TiffRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }

    override fun onCleared() {
        Log.d(TAG, "onCleared: closing renderer=${renderer != null}")
        renderer?.close()
        renderer = null
    }

    class Factory(
        private val application: Application,
        private val uri: Uri,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ViewerScreenViewModel(application, uri) as T
    }

    private companion object {
        const val TAG = "ViewerScreenViewModel"
    }
}
