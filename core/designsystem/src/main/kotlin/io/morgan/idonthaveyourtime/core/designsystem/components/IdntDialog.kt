package io.morgan.idonthaveyourtime.core.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.morgan.idonthaveyourtime.core.designsystem.IdntTheme
import io.morgan.idonthaveyourtime.core.designsystem.reducedMotionEnabled

@Composable
fun IdntDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(IdntTheme.spacing.m),
    content: @Composable () -> Unit,
) {
    val reducedMotion = reducedMotionEnabled()
    Dialog(onDismissRequest = onDismissRequest) {
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { visible = true }

        val alpha by animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = if (reducedMotion) snap() else tween(IdntTheme.motion.quickMs),
            label = "idntDialogAlpha",
        )
        val scale by animateFloatAsState(
            targetValue = if (visible || reducedMotion) 1f else 0.98f,
            animationSpec = if (reducedMotion) snap() else tween(IdntTheme.motion.quickMs),
            label = "idntDialogScale",
        )

        val shape = RoundedCornerShape(20.dp)
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .graphicsLayer {
                    this.alpha = alpha
                    scaleX = scale
                    scaleY = scale
                }
                .clip(shape)
                .background(IdntTheme.colors.surface)
                .border(width = 1.dp, color = IdntTheme.colors.outline, shape = shape)
                .padding(contentPadding),
        ) {
            content()
        }
    }
}

