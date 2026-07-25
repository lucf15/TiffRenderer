package io.github.lucf15.tiffrenderer.sample.theme

import androidx.compose.ui.graphics.Color

data class AppColors(
    val background: Color,
    val onBackground: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val surfaceContainer: Color,
    val onSurfaceVariant: Color,
)

val LightAppColors =
    AppColors(
        background = Color(0xFFFFFBFE),
        onBackground = Color(0xFF1C1B1F),
        primary = Color(0xFF6650A4),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFEADDFF),
        surfaceContainer = Color(0xFFECE6F0),
        onSurfaceVariant = Color(0xFF49454F),
    )

val DarkAppColors =
    AppColors(
        background = Color(0xFF1C1B1F),
        onBackground = Color(0xFFE6E1E5),
        primary = Color(0xFFD0BCFF),
        onPrimary = Color(0xFF381E72),
        primaryContainer = Color(0xFF4F378B),
        surfaceContainer = Color(0xFF211F26),
        onSurfaceVariant = Color(0xFFCAC4D0),
    )
