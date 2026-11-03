package com.orbit.launcher

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.orbit.launcher.core.data.WidgetPlacement

/** Hosts widgets on a dedicated, scrollable home surface. */
@Composable
fun WidgetScreen(
    host: AppWidgetHost,
    widgets: List<WidgetPlacement>,
    onAdd: (AppWidgetProviderInfo) -> Unit,
    onResize: (WidgetPlacement, Int) -> Unit,
    onRemove: (WidgetPlacement) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val manager = remember { AppWidgetManager.getInstance(context) }
    var picking by remember { mutableStateOf(false) }
    val providers = remember(picking) {
        if (!picking) emptyList() else manager.installedProviders
            .filter { it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0 }
            .sortedBy { it.loadLabel(context.packageManager).toString().lowercase() }
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text("Back") }
            Text("WIDGET SPACE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            TextButton(onClick = { picking = true }) { Text("Add") }
        }
        if (widgets.isEmpty()) Text("Add a widget to make this space your own.", color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(16.dp))
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
            items(widgets, key = { it.widgetId }) { widget ->
                val info = manager.getAppWidgetInfo(widget.widgetId)
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(info?.loadLabel(context.packageManager)?.toString() ?: "Unavailable widget", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        TextButton(onClick = { onRemove(widget) }) { Text("Remove") }
                    }
                    if (info == null) {
                        Text("The provider is unavailable. Remove this widget or enable its app.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        val density = LocalDensity.current.density
                        AndroidView(
                            factory = { viewContext: Context -> host.createView(viewContext, widget.widgetId, info).apply { setAppWidget(widget.widgetId, info) } },
                            modifier = Modifier.fillMaxWidth().height(widget.heightDp.dp),
                            update = { view: AppWidgetHostView ->
                                // The available width is reported once Android has measured the host view.
                                view.post {
                                    val width = (view.width / density).toInt().coerceAtLeast(1)
                                    @Suppress("DEPRECATION")
                                    view.updateAppWidgetSize(null, width, widget.heightDp, width, widget.heightDp)
                                }
                            },
                        )
                        if (info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0) {
                            Text("Height: ${widget.heightDp} dp", style = MaterialTheme.typography.labelSmall)
                            Slider(widget.heightDp.toFloat(), { onResize(widget, it.toInt()) }, valueRange = 100f..600f)
                        }
                    }
                }
            }
        }
    }
    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("Choose a widget") },
            text = {
                if (providers.isEmpty()) Text("No widgets available")
                else LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(providers, key = { it.provider.flattenToString() }) { provider ->
                        TextButton(onClick = { picking = false; onAdd(provider) }, modifier = Modifier.fillMaxWidth()) {
                            Text(provider.loadLabel(context.packageManager).toString())
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        )
    }
}
