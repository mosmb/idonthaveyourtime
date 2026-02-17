package io.morgan.idonthaveyourtime.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.morgan.idonthaveyourtime.core.designsystem.IdntTheme

@Composable
fun IdntChip(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = IdntTheme.colors.surfaceMuted,
    contentColor: Color = IdntTheme.colors.textSecondary,
    borderColor: Color = IdntTheme.colors.outline,
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
) {
    val shape = RoundedCornerShape(999.dp)
    IdntText(
        text = text,
        style = IdntTheme.typography.label,
        color = contentColor,
        modifier = modifier
            .clip(shape)
            .background(containerColor)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .padding(contentPadding),
        maxLines = 1,
    )
}

