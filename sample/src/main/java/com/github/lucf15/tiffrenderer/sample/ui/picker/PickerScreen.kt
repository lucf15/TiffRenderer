package com.github.lucf15.tiffrenderer.sample.ui.picker

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.lucf15.tiffrenderer.sample.theme.AppTheme
import com.github.lucf15.tiffrenderer.sample.theme.ProvideAppTheme
import com.github.lucf15.tiffrenderer.sample.ui.components.AppButton

@Composable
fun PickerScreen(
    onPicked: (Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pickTiffLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) onPicked(uri)
        }

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
            BasicText(
                text = "🖼️",
                style = AppTheme.textStyles.displayLarge,
            )
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
                onClick = { pickTiffLauncher.launch(arrayOf("*/*")) },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PickerScreenPreview() {
    ProvideAppTheme {
        PickerScreen(onPicked = {})
    }
}
