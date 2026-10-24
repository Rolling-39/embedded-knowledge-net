package com.rolling.learningguider.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightScheme = lightColorScheme(
    primary = Blue600,
    onPrimary = Slate50,
    primaryContainer = Blue100,
    onPrimaryContainer = Blue900,
    secondary = Sky500,
    onSecondary = Slate50,
    secondaryContainer = Sky200,
    onSecondaryContainer = Slate900,
    tertiary = Violet500,
    onTertiary = Slate50,
    background = Slate50,
    onBackground = Slate800,
    surface = Color.White,
    onSurface = Slate800,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate500,
    outline = Slate200,
    error = Red500,
)

private val DarkScheme = darkColorScheme(
    primary = Blue300,
    onPrimary = Slate900,
    primaryContainer = Blue900,
    onPrimaryContainer = Blue100,
    secondary = Sky200,
    onSecondary = Slate900,
    tertiary = Teal500,
    background = Slate900,
    onBackground = Slate200,
    surface = Slate800,
    onSurface = Slate200,
    surfaceVariant = Slate700,
    onSurfaceVariant = Slate400,
    outline = Slate700,
    error = Red500,
)

@Composable
fun LearningGuiderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = AppTypography,
        content = content,
    )
}
