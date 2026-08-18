package io.github.lucf15.tiffrenderer.sample.shared.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

object AppTheme {
    val colors: AppColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAppColors.current

    val textStyles: AppTextStyles
        @Composable
        @ReadOnlyComposable
        get() = LocalAppTextStyles.current
}

@Composable
fun ProvideAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkAppColors else LightAppColors
    val textStyles = remember(colors) { appTextStyles(colors) }
    CompositionLocalProvider(
        LocalAppColors provides colors,
        LocalAppTextStyles provides textStyles,
        content = content,
    )
}

private val LocalAppColors = staticCompositionLocalOf { LightAppColors }
private val LocalAppTextStyles = staticCompositionLocalOf { appTextStyles(LightAppColors) }
