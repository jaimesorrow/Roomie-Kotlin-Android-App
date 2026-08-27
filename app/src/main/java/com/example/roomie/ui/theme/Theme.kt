package com.example.roomie.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF0061A4),
    onPrimary = Color.White,
    secondary = Color(0xFF006D3B),
    onSecondary = Color.White,
    background = Color(0xFFF6F6F6),
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color.Black,
    background = Color(0xFF101010),
    onBackground = Color.White
)

@Composable
fun RoomieTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (useDarkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colors,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}
