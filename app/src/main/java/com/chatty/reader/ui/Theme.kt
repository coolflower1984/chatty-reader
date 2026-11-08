package com.chatty.reader.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2C6E63),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB6E9DC),
    onPrimaryContainer = Color(0xFF00201B),
    secondary = Color(0xFFB0542C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDBCC),
    onSecondaryContainer = Color(0xFF3B0B00),
    tertiary = Color(0xFF4F6358),
    background = Color(0xFFFCF9F5),
    onBackground = Color(0xFF1C1B19),
    surface = Color(0xFFFCF9F5),
    onSurface = Color(0xFF1C1B19),
    surfaceVariant = Color(0xFFECE6DE),
    onSurfaceVariant = Color(0xFF4A453F),
    surfaceContainer = Color(0xFFF4EFE9),
    surfaceContainerHigh = Color(0xFFEFE9E2),
    outline = Color(0xFF7C756E),
    outlineVariant = Color(0xFFD5CEC5),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FD4C4),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF1B4F46),
    onPrimaryContainer = Color(0xFFB6E9DC),
    secondary = Color(0xFFFFB59A),
    onSecondary = Color(0xFF5A1B04),
    secondaryContainer = Color(0xFF7A3418),
    onSecondaryContainer = Color(0xFFFFDBCC),
    background = Color(0xFF14120F),
    onBackground = Color(0xFFE8E3DB),
    surface = Color(0xFF14120F),
    onSurface = Color(0xFFE8E3DB),
    surfaceVariant = Color(0xFF4A453F),
    onSurfaceVariant = Color(0xFFD2CBC2),
    surfaceContainer = Color(0xFF1E1B18),
    surfaceContainerHigh = Color(0xFF292520),
    outline = Color(0xFF978F86),
    outlineVariant = Color(0xFF4A453F),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)

@Composable
fun ChattyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
