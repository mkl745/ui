package com.orbit.launcher.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val OrbitColors = darkColorScheme(
    primary = Color(0xFFBBC7FF),
    onPrimary = Color(0xFF182553),
    background = Color(0xFF090C15),
    onBackground = Color(0xFFE9ECF8),
    surface = Color(0xFF141A2B),
    onSurface = Color(0xFFE9ECF8),
    secondary = Color(0xFFB8C2DE),
)

@Composable
fun OrbitTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = OrbitColors, content = content)
}
