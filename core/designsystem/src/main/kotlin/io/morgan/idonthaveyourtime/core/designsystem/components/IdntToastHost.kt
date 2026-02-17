package io.morgan.idonthaveyourtime.core.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.snap
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.morgan.idonthaveyourtime.core.designsystem.IdntTheme
import io.morgan.idonthaveyourtime.core.designsystem.reducedMotionEnabled
import kotlinx.coroutines.delay

@Composable
fun IdntToastHost(
    message: String?,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    bottomPadding: PaddingValues = PaddingValues(bottom = 16.dp),
) {
    var lastMessage by remember { mutableStateOf<String?>(null) }
    if (!message.isNullOrBlank()) {
        lastMessage = message
    }

    val reducedMotion = reducedMotionEnabled()
    val enter: EnterTransition = if (reducedMotion) {
        EnterTransition.None
    } else {
        fadeIn(tween(IdntTheme.motion.quickMs)) + slideInVertically(
            initialOffsetY = { fullHeight -> fullHeight / 2 },
            animationSpec = tween(IdntTheme.motion.quickMs),
        )
    }
    val exit: ExitTransition = if (reducedMotion) {
        ExitTransition.None
    } else {
        fadeOut(tween(IdntTheme.motion.quickMs)) + slideOutVertically(
            targetOffsetY = { fullHeight -> fullHeight / 2 },
            animationSpec = tween(IdntTheme.motion.quickMs),
        )
    }

    LaunchedEffect(message) {
        if (!message.isNullOrBlank()) {
            delay(3_000)
            onDismissRequest()
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = !message.isNullOrBlank(),
            enter = enter,
            exit = exit,
        ) {
            val interactionSource = remember { MutableInteractionSource() }
            val shape = RoundedCornerShape(16.dp)
            Box(
                modifier = Modifier
                    .padding(bottomPadding)
                    .padding(horizontal = 16.dp)
                    .clip(shape)
                    .background(IdntTheme.colors.surface)
                    .border(width = 1.dp, color = IdntTheme.colors.outline, shape = shape)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onDismissRequest,
                    )
                    .padding(contentPadding),
            ) {
                IdntText(
                    text = lastMessage.orEmpty(),
                    style = IdntTheme.typography.bodyS,
                    color = IdntTheme.colors.textPrimary,
                )
            }
        }
    }
}

