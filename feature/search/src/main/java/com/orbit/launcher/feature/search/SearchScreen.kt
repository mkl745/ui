package com.orbit.launcher.feature.search

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.orbit.launcher.core.domain.LauncherApp
import com.orbit.launcher.core.domain.searchApps

@Composable
fun SearchScreen(apps: List<LauncherApp>, initialQuery: String, loadIcon: suspend (LauncherApp, Int) -> Bitmap?, onLaunch: (LauncherApp) -> Unit, onBack: () -> Unit) {
    var query by remember { mutableStateOf(initialQuery) }
    val requester = remember { FocusRequester() }
    val results = remember(apps, query) { searchApps(apps.filterNot { it.hidden }, query) }
    LaunchedEffect(Unit) { requester.requestFocus() }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back") }
            Text("SEARCH YOUR SPACE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth().focusRequester(requester), label = { Text("Search apps") }, singleLine = true)
        Spacer(Modifier.height(12.dp))
        Text("${results.size} apps", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
        if (results.isEmpty()) Text("No matching apps", modifier = Modifier.padding(top = 24.dp), color = MaterialTheme.colorScheme.secondary)
        LazyColumn(Modifier.fillMaxSize()) {
            items(results, key = { it.key }) { app ->
                val pixels = with(LocalDensity.current) { 40.dp.roundToPx() }
                val icon by produceState<Bitmap?>(null, app.key, pixels) { value = loadIcon(app, pixels) }
                Row(Modifier.fillMaxWidth().clickable { onLaunch(app) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) Image(icon!!.asImageBitmap(), contentDescription = null, modifier = Modifier.size(40.dp)) else Spacer(Modifier.size(40.dp))
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(app.label, style = MaterialTheme.typography.bodyLarge)
                        if (app.isWorkProfile) Text("Work profile", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
    }
}
