package io.morgan.idonthaveyourtime.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.morgan.idonthaveyourtime.core.designsystem.IdntTheme

@Composable
fun IdntSurface(
    modifier: Modifier = Modifier,
    safeDrawingPadding: Boolean = true,
    content: @Composable () -> Unit,
) {
    val paddingValues = if (safeDrawingPadding) {
        WindowInsets.safeDrawing.asPaddingValues()
    } else {
        PaddingValues(0.dp)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(IdntTheme.colors.background)
            .padding(paddingValues),
    ) {
        content()
    }
}

