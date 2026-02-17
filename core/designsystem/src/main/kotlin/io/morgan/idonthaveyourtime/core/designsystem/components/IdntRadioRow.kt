package io.morgan.idonthaveyourtime.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.morgan.idonthaveyourtime.core.designsystem.IdntTheme

@Composable
fun IdntRadioRow(
    selected: Boolean,
    label: String,
    description: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(14.dp)
    val containerColor = if (selected) IdntTheme.colors.accentMuted else Color.Transparent

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(containerColor, shape)
            .clickable(
                role = Role.RadioButton,
                indication = null,
                interactionSource = interactionSource,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioIndicator(selected = selected)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            IdntText(text = label, style = IdntTheme.typography.body)
            if (!description.isNullOrBlank()) {
                IdntText(text = description, style = IdntTheme.typography.bodyS, color = IdntTheme.colors.textSecondary)
            }
        }
    }
}

@Composable
private fun RadioIndicator(selected: Boolean) {
    val borderColor = if (selected) IdntTheme.colors.accent else IdntTheme.colors.outline
    Box(
        modifier = Modifier
            .size(18.dp)
            .border(width = 1.dp, color = borderColor, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(IdntTheme.colors.accent, CircleShape),
            )
        }
    }
}

