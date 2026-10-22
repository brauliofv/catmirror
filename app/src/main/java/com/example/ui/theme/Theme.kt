package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CatDarkColorScheme = darkColorScheme(
    primary = CatAmberPrimary,
    onPrimary = Color(0xFF1E1000),
    primaryContainer = Color(0xFF472800),
    onPrimaryContainer = CatAmberLight,
    secondary = CatCyanSecondary,
    onSecondary = Color(0xFF002229),
    secondaryContainer = Color(0xFF003F4B),
    onSecondaryContainer = Color(0xFF9DF6FF),
    background = CatSlateBackground,
    onBackground = CatTextPrimary,
    surface = CatSlateSurface,
    onSurface = CatTextPrimary,
    surfaceVariant = CatSlateSurfaceVariant,
    onSurfaceVariant = CatTextSecondary,
    error = CatCoralRed,
    onError = Color.White
)

private val CatLightColorScheme = lightColorScheme(
    primary = CatAmberDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDDB8),
    onPrimaryContainer = Color(0xFF2C1600),
    secondary = CatCyanDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFBCEBFF),
    onSecondaryContainer = Color(0xFF001F25),
    background = Color(0xFFF1F5F9),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF475569),
    error = CatCoralRed,
    onError = Color.White
)

@Composable
fun CatMirrorTheme(
    darkTheme: Boolean = true, // Default to sleek cyber dark theme for low-latency streaming
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) CatDarkColorScheme else CatLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    CatMirrorTheme(darkTheme = true, content = content)
}
