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

@Composable
fun IdntChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
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
        label = "idntChipScale",
    )

    val containerColor = when {
        selected -> IdntTheme.colors.accentMuted
        else -> Color.Transparent
    }
    val borderColor = if (selected) IdntTheme.colors.accent else IdntTheme.colors.outline
    val contentColor = if (selected) IdntTheme.colors.textPrimary else IdntTheme.colors.textSecondary
    val shape = RoundedCornerShape(999.dp)

    IdntText(
        text = text,
        style = IdntTheme.typography.label,
        color = contentColor,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(containerColor)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                indication = null,
                interactionSource = interactionSource,
                onClick = onClick,
            )
            .padding(contentPadding),
        maxLines = 1,
    )
}

