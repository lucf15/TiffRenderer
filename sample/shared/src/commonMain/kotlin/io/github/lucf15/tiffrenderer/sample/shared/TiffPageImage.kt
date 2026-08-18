package io.github.lucf15.tiffrenderer.sample.shared

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import io.github.lucf15.tiffrenderer.TiffPage
import io.github.lucf15.tiffrenderer.TiffRenderMode
import io.github.lucf15.tiffrenderer.createTiffBitmap

/** Renders [page] and displays it. Renders once per distinct [page] (keyed by [TiffPage.index]);
 * a thrown [io.github.lucf15.tiffrenderer.TiffIOException] propagates out of the composition
 * like any other Compose side-effect exception. */
@Composable
fun TiffPageImage(page: TiffPage, modifier: Modifier = Modifier) {
    val imageBitmap by produceState<ImageBitmap?>(initialValue = null, page.index) {
        val bitmap = createTiffBitmap(page.width, page.height)
        page.render(bitmap, renderMode = TiffRenderMode.FOR_DISPLAY)
        value = bitmap.toImageBitmap()
    }

    imageBitmap?.let { bitmap ->
        Image(
            bitmap = bitmap,
            contentDescription = "Page ${page.index + 1}",
            modifier = modifier.fillMaxWidth(),
            contentScale = ContentScale.FillWidth,
        )
    }
}
