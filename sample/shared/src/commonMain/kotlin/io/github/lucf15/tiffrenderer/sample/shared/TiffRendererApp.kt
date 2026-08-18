package io.github.lucf15.tiffrenderer.sample.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.lucf15.tiffrenderer.sample.shared.navigation.MainNavigation
import io.github.lucf15.tiffrenderer.sample.shared.theme.AppTheme
import io.github.lucf15.tiffrenderer.sample.shared.theme.ProvideAppTheme

/** App root, shared verbatim by both platform entry points (`MainActivity` on Android,
 * `MainViewController` on iOS) so there's exactly one place that wires theme + navigation
 * together. */
@Composable
fun TiffRendererApp() {
    ProvideAppTheme {
        MainNavigation(
            modifier = Modifier.fillMaxSize().background(AppTheme.colors.background),
        )
    }
}
