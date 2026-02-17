package io.morgan.idonthaveyourtime.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
fun IdntCard(
    modifier: Modifier = Modifier,
    containerColor: Color = IdntTheme.colors.surface,
    contentPadding: PaddingValues = PaddingValues(IdntTheme.spacing.m),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(containerColor)
            .border(width = 1.dp, color = IdntTheme.colors.outline, shape = shape)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(IdntTheme.spacing.s),
        content = content,
    )
}

