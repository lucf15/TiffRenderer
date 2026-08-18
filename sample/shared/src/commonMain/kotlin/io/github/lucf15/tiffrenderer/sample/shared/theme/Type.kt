package io.github.lucf15.tiffrenderer.sample.shared.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

data class AppTextStyles(
    val displayLarge: TextStyle,
    val displayMedium: TextStyle,
    val headlineSmall: TextStyle,
    val bodyMedium: TextStyle,
    val labelLarge: TextStyle,
)

fun appTextStyles(colors: AppColors) =
    AppTextStyles(
        displayLarge = TextStyle(fontSize = 57.sp, color = colors.onBackground),
        displayMedium = TextStyle(fontSize = 45.sp, color = colors.onBackground),
        headlineSmall =
            TextStyle(
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onBackground,
            ),
        bodyMedium = TextStyle(fontSize = 14.sp, color = colors.onBackground),
        labelLarge =
            TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = colors.onBackground,
            ),
    )
