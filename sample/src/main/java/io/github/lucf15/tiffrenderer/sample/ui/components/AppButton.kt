package io.github.lucf15.tiffrenderer.sample.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.lucf15.tiffrenderer.sample.theme.AppTheme

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(50))
                .background(AppTheme.colors.primary)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 28.dp, vertical = 14.dp),
    ) {
        BasicText(
            text = text,
            style = AppTheme.textStyles.labelLarge.copy(color = AppTheme.colors.onPrimary),
        )
    }
}
