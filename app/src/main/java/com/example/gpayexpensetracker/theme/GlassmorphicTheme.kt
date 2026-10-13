package com.example.gpayexpensetracker.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Color schemes derived directly from Stitch Botanical Glass exports
val DarkGlassColorScheme = darkColorScheme(
    primary = BotanicalOlivePrimary,
    onPrimary = BotanicalDarkBackground,
    primaryContainer = BotanicalSageContainer,
    onPrimaryContainer = BotanicalPistachioAccent,
    secondary = BotanicalPistachioAccent,
    background = BotanicalDarkBackground,
    surface = BotanicalDarkSurface,
    onBackground = BotanicalDarkTextPrimary,
    onSurface = BotanicalDarkTextPrimary,
    onSurfaceVariant = BotanicalDarkTextMuted,
    surfaceContainer = BotanicalDarkContainer
)

val LightGlassColorScheme = lightColorScheme(
    primary = BotanicalSagePrimary,
    onPrimary = BotanicalLightTextPrimary,
    primaryContainer = BotanicalLinenContainer,
    onPrimaryContainer = BotanicalDeepOlivePrimary,
    secondary = BotanicalDeepOlivePrimary,
    background = BotanicalLightBackground,
    surface = BotanicalLightSurface,
    onBackground = BotanicalLightTextPrimary,
    onSurface = BotanicalLightTextPrimary,
    onSurfaceVariant = BotanicalLightTextMuted,
    surfaceContainer = BotanicalLightContainer
)

@Composable
fun GlassmorphicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkGlassColorScheme else LightGlassColorScheme
    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content
    )
}

/**
 * Custom Glassmorphic Modifier extension that applies a frosted translucent background,
 * subtle reflective outline border, blur, and curved corner clipping.
 */
fun Modifier.glassPanel(
    cornerRadius: Dp = 20.dp,
    borderWidth: Dp = 1.dp,
    isDark: Boolean = true
): Modifier {
    val backgroundColor = if (isDark) {
        BotanicalDarkSurface.copy(alpha = 0.78f)
    } else {
        Color.White.copy(alpha = 0.85f)
    }

    val borderColor = if (isDark) {
        Color.White.copy(alpha = 0.08f)
    } else {
        Color.Black.copy(alpha = 0.06f)
    }

    val shape = RoundedCornerShape(cornerRadius)

    return this
        .clip(shape)
        .background(backgroundColor)
        .border(BorderStroke(borderWidth, borderColor), shape)
}

fun Modifier.glassAlertPanel(
    cornerRadius: Dp = 18.dp,
    isDark: Boolean = true
): Modifier {
    val bg = if (isDark) {
        BotanicalAmberAlert.copy(alpha = 0.12f)
    } else {
        Color(0xFFFFF5F1).copy(alpha = 0.95f)
    }
    val border = if (isDark) {
        BotanicalAmberAlert.copy(alpha = 0.35f)
    } else {
        BotanicalWarmOchreAlert.copy(alpha = 0.45f)
    }
    val shape = RoundedCornerShape(cornerRadius)

    return this
        .clip(shape)
        .background(bg)
        .border(BorderStroke(1.dp, border), shape)
}
