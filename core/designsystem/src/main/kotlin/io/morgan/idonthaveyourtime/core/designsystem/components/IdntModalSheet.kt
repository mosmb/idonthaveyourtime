package io.morgan.idonthaveyourtime.core.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.morgan.idonthaveyourtime.core.designsystem.IdntTheme
import io.morgan.idonthaveyourtime.core.designsystem.reducedMotionEnabled

@Composable
fun IdntModalSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = IdntTheme.spacing.m, vertical = IdntTheme.spacing.l),
    content: @Composable ColumnScope.() -> Unit,
) {
    val reducedMotion = reducedMotionEnabled()
    val overlayEnter = if (reducedMotion) EnterTransition.None else fadeIn(tween(IdntTheme.motion.quickMs))
    val overlayExit = if (reducedMotion) ExitTransition.None else fadeOut(tween(IdntTheme.motion.quickMs))
    val sheetEnter = if (reducedMotion) {
        EnterTransition.None
    } else {
        slideInVertically(
            initialOffsetY = { fullHeight -> fullHeight },
            animationSpec = spring(dampingRatio = 0.9f, stiffness = 600f),
        ) + fadeIn(tween(IdntTheme.motion.quickMs))
    }
    val sheetExit = if (reducedMotion) {
        ExitTransition.None
    } else {
        slideOutVertically(
            targetOffsetY = { fullHeight -> fullHeight },
            animationSpec = tween(IdntTheme.motion.quickMs),
        ) + fadeOut(tween(IdntTheme.motion.quickMs))
    }

    val interactionSource = remember { MutableInteractionSource() }
    val sheetInteractionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = overlayEnter,
            exit = overlayExit,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        indication = null,
                        interactionSource = interactionSource,
                        onClick = onDismissRequest,
                    ),
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = sheetEnter,
            exit = sheetExit,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .sizeIn(maxHeight = 640.dp)
                    .clip(shape)
                    .background(IdntTheme.colors.surface)
                    .border(width = 1.dp, color = IdntTheme.colors.outline, shape = shape)
                    .clickable(
                        indication = null,
                        interactionSource = sheetInteractionSource,
                        onClick = { },
                    )
                    .padding(contentPadding)
                    .padding(
                        bottom = WindowInsets.navigationBars
                            .asPaddingValues()
                            .calculateBottomPadding(),
                    ),
                content = content,
            )
        }
    }
}
