package com.example.gpayexpensetracker.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
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

private val LightColorScheme = lightColorScheme(
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
fun GPayExpenseTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
