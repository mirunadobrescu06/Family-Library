package com.example.library.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF6D4C2F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF3DFC6),
    onPrimaryContainer = Color(0xFF2B1700),
    secondary = Color(0xFF6A5D4F),
    secondaryContainer = Color(0xFFF1E1CF),
    onSecondaryContainer = Color(0xFF241A0F),
    tertiary = Color(0xFF2F6B6B),
    tertiaryContainer = Color(0xFFBDEBEA),
    onTertiaryContainer = Color(0xFF002020),
    background = Color(0xFFFBF7F2),
    surface = Color(0xFFFBF7F2),
    surfaceVariant = Color(0xFFEFE4D8),
    onSurfaceVariant = Color(0xFF50453A),
    surfaceContainerLow = Color(0xFFF6EFE7),
    surfaceContainer = Color(0xFFF1E9E0),
    surfaceContainerHigh = Color(0xFFEBE3DA),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFE7BF94),
    onPrimary = Color(0xFF452A0B),
    primaryContainer = Color(0xFF5E3F20),
    onPrimaryContainer = Color(0xFFFFDDB9),
    secondary = Color(0xFFD7C3AE),
    secondaryContainer = Color(0xFF52463A),
    onSecondaryContainer = Color(0xFFF4DFC9),
    tertiary = Color(0xFF9FD0CF),
    tertiaryContainer = Color(0xFF124F4F),
    onTertiaryContainer = Color(0xFFBDEBEA),
    background = Color(0xFF19140F),
    surface = Color(0xFF19140F),
    surfaceVariant = Color(0xFF4F453B),
    onSurfaceVariant = Color(0xFFD4C4B5),
    surfaceContainerLow = Color(0xFF211B16),
    surfaceContainer = Color(0xFF261F1A),
    surfaceContainerHigh = Color(0xFF302924),
)

@Composable
fun LibraryTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
