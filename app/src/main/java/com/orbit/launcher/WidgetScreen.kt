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
    onResize: (WidgetPlacement, Int, Int) -> Unit,
    onRemove: (WidgetPlacement) -> Unit,
    onRebind: (WidgetPlacement, AppWidgetProviderInfo) -> Unit,
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
                val info = if (widget.widgetId >= 0) manager.getAppWidgetInfo(widget.widgetId) else null
                val missingProvider = if (widget.widgetId < 0) manager.installedProviders.firstOrNull { it.provider.flattenToString() == widget.providerName } else null
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(info?.loadLabel(context.packageManager)?.toString() ?: missingProvider?.loadLabel(context.packageManager)?.toString() ?: widget.providerName ?: "Unavailable widget", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        TextButton(onClick = { onRemove(widget) }) { Text("Remove") }
                    }
                    if (info == null) {
                        Text(if (missingProvider != null) "Restore this widget to grant binding permission and configure it again." else "The provider is unavailable. Remove this widget or install/enable its app.", style = MaterialTheme.typography.bodySmall)
                        if (missingProvider != null) TextButton(onClick = { onRebind(widget, missingProvider) }) { Text("Restore widget") }
                    } else {
                        val density = LocalDensity.current.density
                        var height by remember(widget.widgetId, widget.heightDp) { mutableFloatStateOf(widget.heightDp.toFloat()) }
                        var width by remember(widget.widgetId, widget.widthDp) { mutableFloatStateOf(widget.widthDp.toFloat()) }
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val availableWidth = maxWidth.value.coerceAtMost(1600f)
                            val displayedWidth = if (width <= 0f) availableWidth else width.coerceAtMost(availableWidth)
                            Column(Modifier.fillMaxWidth()) {
                                AndroidView(
                                    factory = { viewContext: Context -> host.createView(viewContext, widget.widgetId, info).apply { setAppWidget(widget.widgetId, info) } },
                                    modifier = Modifier.width(displayedWidth.dp).height(height.dp),
                                    update = { view: AppWidgetHostView ->
                                        view.post {
                                            val measuredWidth = (view.width / density).toInt().coerceAtLeast(1)
                                            @Suppress("DEPRECATION")
                                            view.updateAppWidgetSize(null, measuredWidth, height.toInt(), measuredWidth, height.toInt())
                                        }
                                    },
                                )
                                if (info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0) {
                                    Text("Height: ${height.toInt()} dp", style = MaterialTheme.typography.labelSmall)
                                    Slider(height, { height = it }, onValueChangeFinished = { onResize(widget, height.toInt(), width.toInt()) }, valueRange = 100f..600f)
                                }
                                if (info.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0 && availableWidth > 100f) {
                                    Text("Width: ${displayedWidth.toInt()} dp", style = MaterialTheme.typography.labelSmall)
                                    Slider(displayedWidth, { width = it }, onValueChangeFinished = { onResize(widget, height.toInt(), width.toInt()) }, valueRange = 100f..availableWidth)
                                }
                            }
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
