package io.morgan.idonthaveyourtime.core.designsystem

import android.animation.ValueAnimator
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Stable
data class IdntColors(
    val background: Color,
    val surface: Color,
    val surfaceMuted: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val outline: Color,
    val accent: Color,
    val accentMuted: Color,
    val danger: Color,
    val onAccent: Color,
)

@Stable
data class IdntTypography(
    val titleL: TextStyle,
    val titleM: TextStyle,
    val body: TextStyle,
    val bodyS: TextStyle,
    val label: TextStyle,
    val mono: TextStyle,
)

@Stable
data class IdntSpacing(
    val xs: Dp,
    val s: Dp,
    val m: Dp,
    val l: Dp,
    val xl: Dp,
)

@Stable
data class IdntMotion(
    val quickMs: Int,
    val standardMs: Int,
    val emphasizedMs: Int,
)

private val LightColors = IdntColors(
    background = Color(0xFFFAFAFA),
    surface = Color(0xFFFFFFFF),
    surfaceMuted = Color(0xFFF4F4F5),
    textPrimary = Color(0xFF09090B),
    textSecondary = Color(0xFF3F3F46),
    outline = Color(0x1A000000),
    accent = Color(0xFF2563EB),
    accentMuted = Color(0xFFE8F0FF),
    danger = Color(0xFFE5484D),
    onAccent = Color(0xFFFFFFFF),
)

private val DarkColors = IdntColors(
    background = Color(0xFF0B0B0D),
    surface = Color(0xFF121216),
    surfaceMuted = Color(0xFF1A1A1F),
    textPrimary = Color(0xFFF8FAFC),
    textSecondary = Color(0xFFA1A1AA),
    outline = Color(0x1AFFFFFF),
    accent = Color(0xFF6AA2FF),
    accentMuted = Color(0xFF172340),
    danger = Color(0xFFFF6B6B),
    onAccent = Color(0xFF0B0B0D),
)

private val DefaultTypography = IdntTypography(
    titleL = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleM = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium),
    body = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal),
    bodyS = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    label = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    mono = TextStyle(fontSize = 13.sp, fontFamily = FontFamily.Monospace),
)

private val DefaultSpacing = IdntSpacing(
    xs = 6.dp,
    s = 10.dp,
    m = 16.dp,
    l = 24.dp,
    xl = 32.dp,
)

private val DefaultMotion = IdntMotion(
    quickMs = 180,
    standardMs = 220,
    emphasizedMs = 260,
)

private val LocalIdntColors = staticCompositionLocalOf { LightColors }
private val LocalIdntTypography = staticCompositionLocalOf { DefaultTypography }
private val LocalIdntSpacing = staticCompositionLocalOf { DefaultSpacing }
private val LocalIdntMotion = staticCompositionLocalOf { DefaultMotion }

object IdntTheme {
    val colors: IdntColors
        @Composable get() = LocalIdntColors.current

    val typography: IdntTypography
        @Composable get() = LocalIdntTypography.current

    val spacing: IdntSpacing
        @Composable get() = LocalIdntSpacing.current

    val motion: IdntMotion
        @Composable get() = LocalIdntMotion.current
}

@Composable
fun reducedMotionEnabled(): Boolean = !ValueAnimator.areAnimatorsEnabled()

@Composable
fun IdonthaveyourtimeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val selectionColors = TextSelectionColors(
        handleColor = colors.accent,
        backgroundColor = colors.accent.copy(alpha = 0.28f),
    )

    CompositionLocalProvider(
        LocalIdntColors provides colors,
        LocalIdntTypography provides DefaultTypography,
        LocalIdntSpacing provides DefaultSpacing,
        LocalIdntMotion provides DefaultMotion,
        LocalTextSelectionColors provides selectionColors,
        content = content,
    )
}
