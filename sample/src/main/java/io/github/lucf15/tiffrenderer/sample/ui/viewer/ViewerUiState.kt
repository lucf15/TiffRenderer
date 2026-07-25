package io.github.lucf15.tiffrenderer.sample.ui.viewer

import androidx.compose.ui.unit.IntSize

sealed interface ViewerUiState {
    data object Loading : ViewerUiState

    data class Loaded(
        val pageCount: Int,
        val pageSizes: List<IntSize>,
    ) : ViewerUiState

    data class Failed(
        val message: String,
    ) : ViewerUiState
}
