package io.morgan.idonthaveyourtime.core.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.morgan.idonthaveyourtime.core.designsystem.IdntTheme
import io.morgan.idonthaveyourtime.core.designsystem.reducedMotionEnabled

enum class IdntButtonVariant {
    Primary,
    Secondary,
    Ghost,
}

@Composable
fun IdntButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: IdntButtonVariant = IdntButtonVariant.Primary,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
) {
    val reducedMotion = reducedMotionEnabled()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val scaleTarget = if (pressed && enabled) 0.985f else 1f
    val scale by animateFloatAsState(
        targetValue = scaleTarget,
        animationSpec = if (reducedMotion) {
            snap()
        } else {
            spring(dampingRatio = 0.85f, stiffness = 800f)
        },
        label = "idntButtonScale",
    )

    val baseAlpha = if (enabled) 1f else 0.5f
    val alpha by animateFloatAsState(
        targetValue = baseAlpha,
        animationSpec = if (reducedMotion) snap() else spring(dampingRatio = 0.9f, stiffness = 1_000f),
        label = "idntButtonAlpha",
    )

    val (backgroundColor, contentColor, borderColor) = when (variant) {
        IdntButtonVariant.Primary -> Triple(IdntTheme.colors.accent, IdntTheme.colors.onAccent, Color.Transparent)
        IdntButtonVariant.Secondary -> Triple(IdntTheme.colors.surfaceMuted, IdntTheme.colors.textPrimary, IdntTheme.colors.outline)
        IdntButtonVariant.Ghost -> Triple(Color.Transparent, IdntTheme.colors.textPrimary, IdntTheme.colors.outline)
    }

    val shape = RoundedCornerShape(999.dp)
    IdntText(
        text = text,
        style = IdntTheme.typography.label,
        color = contentColor,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .clip(shape)
            .background(backgroundColor)
            .then(
                if (borderColor != Color.Transparent) {
                    Modifier.border(width = 1.dp, color = borderColor, shape = shape)
                } else {
                    Modifier
                }
            )
            .clickable(
                enabled = enabled,
                role = Role.Button,
                indication = null,
                interactionSource = interactionSource,
                onClick = onClick,
            )
            .padding(contentPadding),
    )
}

