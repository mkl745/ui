package com.orbit.launcher.core.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.orbit.launcher.core.domain.LauncherSettings

@Composable
fun OrbitTheme(settings: LauncherSettings = LauncherSettings(), content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) { "light" -> false; "system" -> isSystemInDarkTheme(); else -> true }
    val context = LocalContext.current
    val accent = when (settings.palette) { "cyan" -> Color(0xFF9BE8EC); "amber" -> Color(0xFFFFD68A); else -> Color(0xFFBBC7FF) }
    val lightAccent = when (settings.palette) { "cyan" -> Color(0xFF00696D); "amber" -> Color(0xFF795900); else -> Color(0xFF4458A5) }
    val scheme = if (settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) {
        darkColorScheme(primary = accent, onPrimary = Color(0xFF18202D), background = Color(0xFF090C15), onBackground = Color(0xFFE9ECF8), surface = Color(0xFF141A2B), onSurface = Color(0xFFE9ECF8), secondary = Color(0xFFB8C2DE))
    } else {
        lightColorScheme(primary = lightAccent, background = Color(0xFFF9F9FF), onBackground = Color(0xFF191B24), surface = Color(0xFFF9F9FF), onSurface = Color(0xFF191B24))
    }
    val colors = if (settings.themeMode == "amoled") scheme.copy(background = Color.Black, surface = Color.Black) else scheme
    val fontFamily = when (settings.font) { "serif" -> FontFamily.Serif; "mono" -> FontFamily.Monospace; else -> FontFamily.SansSerif }
    val base = MaterialTheme.typography
    val typography = base.copy(
        headlineLarge = base.headlineLarge.copy(fontFamily = fontFamily),
        titleMedium = base.titleMedium.copy(fontFamily = fontFamily),
        bodyLarge = base.bodyLarge.copy(fontFamily = fontFamily),
        bodyMedium = base.bodyMedium.copy(fontFamily = fontFamily),
        labelMedium = base.labelMedium.copy(fontFamily = fontFamily),
    )
    val radius = settings.cornerRadius.coerceIn(0, 32).dp
    val shapes = Shapes(small = RoundedCornerShape(radius / 2), medium = RoundedCornerShape(radius), large = RoundedCornerShape(radius), extraLarge = RoundedCornerShape(radius))
    MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes, content = content)
}
