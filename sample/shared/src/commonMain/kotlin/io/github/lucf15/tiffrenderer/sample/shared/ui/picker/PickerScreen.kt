package io.github.lucf15.tiffrenderer.sample.shared.ui.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import io.github.lucf15.tiffrenderer.sample.shared.platform.rememberTiffFilePickerLauncher
import io.github.lucf15.tiffrenderer.sample.shared.theme.AppTheme
import io.github.lucf15.tiffrenderer.sample.shared.ui.components.AppButton
import io.github.lucf15.tiffrenderer.sample.shared.ui.components.PictureIcon

@Composable
fun PickerScreen(
    onPicked: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pickTiff = rememberTiffFilePickerLauncher(onPicked = onPicked)

    val background =
        Brush.radialGradient(
            colors =
                listOf(
                    AppTheme.colors.primaryContainer.copy(alpha = 0.35f),
                    AppTheme.colors.background,
                ),
        )

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(background)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            PictureIcon()
            Spacer(Modifier.height(16.dp))
            BasicText(
                text = "tiffrenderer demo",
                style = AppTheme.textStyles.headlineSmall,
            )
            Spacer(Modifier.height(8.dp))
            BasicText(
                text = "Pick any TIFF to page through it, page by page.",
                style = AppTheme.textStyles.bodyMedium.copy(color = AppTheme.colors.onSurfaceVariant),
            )
            Spacer(Modifier.height(24.dp))
            AppButton(
                text = "Choose TIFF file",
                onClick = pickTiff,
            )
        }
    }
}
