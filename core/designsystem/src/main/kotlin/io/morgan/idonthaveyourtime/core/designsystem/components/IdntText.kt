package io.morgan.idonthaveyourtime.core.designsystem.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import io.morgan.idonthaveyourtime.core.designsystem.IdntTheme

@Composable
fun IdntText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = IdntTheme.typography.body,
    color: Color = IdntTheme.colors.textPrimary,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color),
        maxLines = maxLines,
        overflow = overflow,
    )
}

