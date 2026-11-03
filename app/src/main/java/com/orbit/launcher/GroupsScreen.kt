package com.orbit.launcher

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.orbit.launcher.core.domain.LauncherApp
import com.orbit.launcher.core.domain.LauncherSettings

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GroupsScreen(apps: List<LauncherApp>, settings: LauncherSettings, loadIcon: suspend (LauncherApp, Int) -> Bitmap?, onLaunch: (LauncherApp) -> Unit, onMenu: (LauncherApp) -> Unit, onBack: () -> Unit) {
    val groups = remember(apps) { apps.filter { !it.hidden && !it.groupName.isNullOrBlank() }.groupBy { it.groupName!! }.toSortedMap() }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp)) {
        TextButton(onClick = onBack) { Text("Back") }
        Text("Groups", style = MaterialTheme.typography.headlineLarge)
        Text("Long-press an app in your orbit to add it to a group.", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
        if (groups.isEmpty()) Text("No groups yet", modifier = Modifier.padding(vertical = 24.dp))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 16.dp)) {
            groups.forEach { (name, members) ->
                item(key = "group:$name") { Text(name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 12.dp)) }
                items(members.sortedBy { it.label.lowercase() }, key = { "app:${it.key}" }) { app ->
                    val size = with(LocalDensity.current) { 40.dp.roundToPx() }
                    val icon by produceState<Bitmap?>(null, app.key, size, settings.themedIcons, settings.palette, settings.themeMode, settings.dynamicColor, settings.iconPack, app.customIconUri) { value = loadIcon(app, size) }
                    Row(Modifier.fillMaxWidth().combinedClickable(onClick = { onLaunch(app) }, onLongClick = { onMenu(app) }).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (icon != null) Image(icon!!.asImageBitmap(), contentDescription = null, modifier = Modifier.size(40.dp)) else Spacer(Modifier.size(40.dp))
                        Spacer(Modifier.width(16.dp))
                        Column { Text(app.label); if (app.isWorkProfile) Text("Work profile", style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
        }
    }
}
