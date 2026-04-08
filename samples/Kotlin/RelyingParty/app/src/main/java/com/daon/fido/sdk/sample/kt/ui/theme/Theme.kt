package com.daon.fido.sdk.sample.kt.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorPalette =
    darkColorScheme(
        primary = Black, // Button background - black
        onPrimary = White, // Button text - white
        primaryContainer = DarkGray, // Container backgrounds - dark gray
        onPrimaryContainer = White, // Container text - white
        secondary = DarkGray, // Secondary button background - dark gray
        onSecondary = White, // Secondary button text - white
        secondaryContainer = DarkGray, // Secondary container - dark gray
        onSecondaryContainer = White, // Secondary container text - white
        tertiary = DarkGray,
        onTertiary = White,
        background = Black, // App background - black
        onBackground = White, // App text - white
        surface = DarkGray, // Surface background - dark gray
        onSurface = White, // Surface text - white
        surfaceVariant = DarkGray, // Variant surfaces - dark gray
        onSurfaceVariant = White, // Variant surface text - white
        outline = White, // White borders for buttons in dark mode
        outlineVariant = White, // White borders for button variants in dark mode
        error = Color(0xFFFF5555),
        onError = White,
    )

private val LightColorPalette =
    lightColorScheme(
        primary = White, // Button background - white
        onPrimary = Black, // Button text - black
        primaryContainer = White, // Container backgrounds - white
        onPrimaryContainer = Black, // Container text - black
        secondary = White, // Secondary button background - white
        onSecondary = Black, // Secondary button text - black
        secondaryContainer = White, // Secondary container - white
        onSecondaryContainer = Black, // Secondary container text - black
        tertiary = DarkGray,
        onTertiary = White,
        background = White, // App background - white
        onBackground = Black, // App text - black
        surface = White, // Surface background - white
        onSurface = Black, // Surface text - black
        surfaceVariant = LightGray, // Variant surfaces - light gray
        onSurfaceVariant = Black, // Variant surface text - black
        outline = DarkGray, // Dark grey borders for buttons
        outlineVariant = DarkGray, // Dark grey borders for button variants
        error = Color(0xFFCC0000),
        onError = White,
    )

@Composable
fun IdentityxandroidsdkfidoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors =
        if (darkTheme) {
            DarkColorPalette
        } else {
            LightColorPalette
        }

    MaterialTheme(colorScheme = colors, typography = Typography, shapes = Shapes, content = content)
}
