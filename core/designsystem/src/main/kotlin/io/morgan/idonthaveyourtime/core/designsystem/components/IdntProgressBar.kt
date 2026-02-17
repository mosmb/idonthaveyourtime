package io.morgan.idonthaveyourtime.core.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.morgan.idonthaveyourtime.core.designsystem.IdntTheme
import io.morgan.idonthaveyourtime.core.designsystem.reducedMotionEnabled

@Composable
fun IdntProgressBar(
    progress: Float?,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = reducedMotionEnabled()
    val shape = RoundedCornerShape(999.dp)
    val trackColor = IdntTheme.colors.surfaceMuted
    val fillColor = IdntTheme.colors.accent

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(shape)
            .background(trackColor),
    ) {
        if (progress != null) {
            val target = progress.coerceIn(0f, 1f)
            val animated by animateFloatAsState(
                targetValue = target,
                animationSpec = if (reducedMotion) snap() else tween(durationMillis = IdntTheme.motion.standardMs),
                label = "idntProgress",
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animated)
                    .background(fillColor),
            )
        } else {
            if (reducedMotion) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.35f)
                        .background(fillColor),
                )
            } else {
                val density = LocalDensity.current
                val containerWidthPx = with(density) { maxWidth.toPx() }
                val barWidthPx = containerWidthPx * 0.35f
                val transition = rememberInfiniteTransition(label = "idntIndeterminate")
                val offsetPx by transition.animateFloat(
                    initialValue = -barWidthPx,
                    targetValue = containerWidthPx + barWidthPx,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 900, easing = LinearEasing),
                    ),
                    label = "idntIndeterminateOffset",
                )

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(with(density) { barWidthPx.toDp() })
                        .graphicsLayer { translationX = offsetPx }
                        .background(fillColor),
                )
            }
        }
    }
}
