package com.orbit.launcher.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.orbit.launcher.core.domain.LauncherApp
import com.orbit.launcher.core.domain.LauncherSettings

@Composable
fun SettingsScreen(settings: LauncherSettings, apps: List<LauncherApp>, onChange: (LauncherSettings) -> Unit, onUnhide: (LauncherApp) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        TextButton(onClick = onBack) { Text("Back") }
        Text("Your orbit", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text("Changes apply immediately.", color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(28.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) { Text("App labels"); Text("Show names around the orbit", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
            Switch(settings.showLabels, { onChange(settings.copy(showLabels = it)) })
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) { Text("Reduce motion"); Text("Disable orbital fling animation", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
            Switch(settings.reduceMotion, { onChange(settings.copy(reduceMotion = it)) })
        }
        Spacer(Modifier.height(24.dp))
        Text("Icon size · ${settings.iconSize} dp")
        Slider(settings.iconSize.toFloat(), { onChange(settings.copy(iconSize = it.toInt())) }, valueRange = 32f..72f)
        val hidden = apps.filter { it.hidden }.sortedBy { it.label.lowercase() }
        Spacer(Modifier.height(16.dp))
        Text("Hidden apps", style = MaterialTheme.typography.titleMedium)
        if (hidden.isEmpty()) Text("No hidden apps", color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 12.dp))
        hidden.forEach { app ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(app.label, modifier = Modifier.weight(1f))
                TextButton(onClick = { onUnhide(app) }) { Text("Unhide") }
            }
        }
    }
}
