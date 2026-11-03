package com.orbit.launcher.feature.settings

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import com.orbit.launcher.feature.home.AppOrb
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
fun SettingsScreen(settings: LauncherSettings, apps: List<LauncherApp>, onChange: (LauncherSettings) -> Unit, loadIcon: suspend (LauncherApp, Int) -> Bitmap?, onUnhide: (LauncherApp) -> Unit, onShowInOrbit: (LauncherApp) -> Unit, onExport: () -> Unit, onRestore: () -> Unit, onNotificationAccess: () -> Unit, hiddenUnlocked: Boolean, onUnlockHidden: () -> Unit, onToggleHiddenLock: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        TextButton(onClick = onBack) { Text("Back") }
        Text("Your orbit", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text("Changes apply immediately.", color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(20.dp))
        apps.firstOrNull { !it.hidden }?.let { previewApp ->
            Text("LIVE PREVIEW", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                AppOrb(previewApp, settings, 0, loadIcon, {}, {})
            }
        }
        Spacer(Modifier.height(8.dp))
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
        Text("Appearance", style = MaterialTheme.typography.titleMedium)
        Text("Theme: ${settings.themeMode}", style = MaterialTheme.typography.bodyMedium)
        Row { listOf("system", "dark", "light", "amoled").forEach { mode ->
            TextButton(onClick = { onChange(settings.copy(themeMode = mode)) }, contentPadding = PaddingValues(horizontal = 6.dp)) { Text(mode) }
        } }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) { Text("Material You"); Text("Uses system colors on Android 12+", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
            Switch(settings.dynamicColor, { onChange(settings.copy(dynamicColor = it)) })
        }
        if (!settings.dynamicColor) Row { listOf("violet", "cyan", "amber").forEach { palette ->
            TextButton(onClick = { onChange(settings.copy(palette = palette)) }) { Text(palette) }
        } }
        Row { listOf("sans", "serif", "mono").forEach { font ->
            TextButton(onClick = { onChange(settings.copy(font = font)) }) { Text(font) }
        } }
        Text("Corner radius · ${settings.cornerRadius} dp")
        Slider(settings.cornerRadius.toFloat(), { onChange(settings.copy(cornerRadius = it.toInt())) }, valueRange = 0f..32f)
        Spacer(Modifier.height(16.dp))
        Text("Icon size · ${settings.iconSize} dp")
        Slider(settings.iconSize.toFloat(), { onChange(settings.copy(iconSize = it.toInt())) }, valueRange = 32f..72f)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) { Text("Web search"); Text("Adds an option to send your query to another app", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
            Switch(settings.webSearch, { onChange(settings.copy(webSearch = it)) })
        }
        Spacer(Modifier.height(20.dp))
        Text("Notification badges", style = MaterialTheme.typography.titleMedium)
        Text("Allow notification access to show counts. Orbit does not store notification contents.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        TextButton(onClick = onNotificationAccess) { Text("Manage notification access") }
        Spacer(Modifier.height(16.dp))
        Text("Backup", style = MaterialTheme.typography.titleMedium)
        Text("Save or restore app preferences and settings as JSON. Profile identifiers may differ on another device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        Row { TextButton(onClick = onExport) { Text("Export") }; TextButton(onClick = onRestore) { Text("Restore") } }
        val searchOnly = apps.filter { it.searchOnly && !it.hidden }.sortedBy { it.label.lowercase() }
        Spacer(Modifier.height(16.dp))
        Text("Search-only apps", style = MaterialTheme.typography.titleMedium)
        if (searchOnly.isEmpty()) Text("No search-only apps", color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 12.dp))
        searchOnly.forEach { app ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(app.label, modifier = Modifier.weight(1f))
                TextButton(onClick = { onShowInOrbit(app) }) { Text("Show in orbit") }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) { Text("Protect hidden apps"); Text("Require biometrics to view or restore hidden apps and manage backups", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
            Switch(settings.lockHidden, { onToggleHiddenLock() })
        }
        val hidden = if (!settings.lockHidden || hiddenUnlocked) apps.filter { it.hidden }.sortedBy { it.label.lowercase() } else emptyList()
        Spacer(Modifier.height(16.dp))
        Text("Hidden apps", style = MaterialTheme.typography.titleMedium)
        if (settings.lockHidden && !hiddenUnlocked) TextButton(onClick = onUnlockHidden) { Text("Unlock hidden apps") }
        else if (hidden.isEmpty()) Text("No hidden apps", color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 12.dp))
        hidden.forEach { app ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(app.label, modifier = Modifier.weight(1f))
                TextButton(onClick = { onUnhide(app) }) { Text("Unhide") }
            }
        }
    }
}
