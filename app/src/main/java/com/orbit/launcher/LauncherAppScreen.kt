package com.orbit.launcher

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import com.orbit.launcher.core.domain.LauncherApp
import com.orbit.launcher.feature.home.OrbitHome
import com.orbit.launcher.feature.search.SearchScreen
import com.orbit.launcher.feature.settings.SettingsScreen

@Composable
fun LauncherAppScreen(viewModel: LauncherViewModel, homeRequests: Int, widgetHost: AppWidgetHost) {
    val apps by viewModel.apps.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val widgets by viewModel.widgets.collectAsState()
    val badgeCounts by NotificationBadges.badgeCounts.collectAsState()
    var screen by rememberSaveable { mutableStateOf("home") }
    var initialQuery by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<LauncherApp?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var grouping by remember { mutableStateOf(false) }
    var groupName by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val homeFocus = remember { FocusRequester() }
    val widgetManager = remember { AppWidgetManager.getInstance(context) }
    var pendingWidgetId by rememberSaveable { mutableIntStateOf(AppWidgetManager.INVALID_APPWIDGET_ID) }
    fun cancelPendingWidget() {
        if (pendingWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) widgetHost.deleteAppWidgetId(pendingWidgetId)
        pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    }
    fun savePendingWidget() {
        if (pendingWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            viewModel.putWidget(pendingWidgetId, 180)
            pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        }
    }
    val configureWidget = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) savePendingWidget() else cancelPendingWidget()
    }
    fun configureOrSaveWidget() {
        val info = widgetManager.getAppWidgetInfo(pendingWidgetId)
        if (info == null) { cancelPendingWidget(); Toast.makeText(context, "Widget unavailable", Toast.LENGTH_SHORT).show(); return }
        if (info.configure == null) { savePendingWidget(); return }
        try {
            configureWidget.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).setComponent(info.configure)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId))
        } catch (_: ActivityNotFoundException) {
            cancelPendingWidget(); Toast.makeText(context, "Widget configuration unavailable", Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            cancelPendingWidget(); Toast.makeText(context, "Widget configuration denied", Toast.LENGTH_SHORT).show()
        }
    }
    val bindWidget = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) configureOrSaveWidget() else cancelPendingWidget()
    }
    fun addWidget(provider: AppWidgetProviderInfo) {
        if (pendingWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) return
        pendingWidgetId = widgetHost.allocateAppWidgetId()
        try {
            if (widgetManager.bindAppWidgetIdIfAllowed(pendingWidgetId, provider.provider)) configureOrSaveWidget()
            else bindWidget.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider))
        } catch (_: ActivityNotFoundException) {
            cancelPendingWidget(); Toast.makeText(context, "Widget binding unavailable", Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            cancelPendingWidget(); Toast.makeText(context, "Widget binding denied", Toast.LENGTH_SHORT).show()
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val success = runCatching { viewModel.exportBackup(uri) }.isSuccess
            Toast.makeText(context, if (success) "Backup saved" else "Could not save backup", Toast.LENGTH_LONG).show()
        }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val success = runCatching { viewModel.restoreBackup(uri) }.isSuccess
            Toast.makeText(context, if (success) "Backup restored" else "Invalid or unreadable backup", Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(homeRequests) { screen = "home"; selected = null; renaming = false; grouping = false }
    LaunchedEffect(screen) { if (screen == "home") homeFocus.requestFocus() }
    BackHandler(enabled = screen != "home" || selected != null) {
        if (selected != null) { selected = null; renaming = false; grouping = false } else screen = "home"
    }
    Surface(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).onPreviewKeyEvent { event ->
        val codepoint = event.nativeKeyEvent.unicodeChar
        if (screen == "home" && selected == null && event.type == KeyEventType.KeyDown && codepoint > 0 && !Character.isISOControl(codepoint)) {
            initialQuery = String(Character.toChars(codepoint))
            screen = "search"
            true
        } else false
    }.focusRequester(homeFocus).focusable()) {
        when (screen) {
            "search" -> SearchScreen(apps, initialQuery, viewModel::icon, { viewModel.launch(it); screen = "home" }, { screen = "home" })
            "groups" -> GroupsScreen(apps, viewModel::icon, viewModel::launch, { selected = it }, { screen = "home" })
            "widgets" -> WidgetScreen(widgetHost, widgets, ::addWidget,
                { widget, height -> viewModel.putWidget(widget.widgetId, height) },
                { widget -> viewModel.removeWidget(widget.widgetId); widgetHost.deleteAppWidgetId(widget.widgetId) },
                { screen = "home" })
            "settings" -> SettingsScreen(settings, apps, viewModel::settings, viewModel::icon, viewModel::hide, viewModel::searchOnly, { export.launch("orbit-backup.json") }, { restore.launch(arrayOf("application/json", "text/json")) }, {
                try { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                catch (_: ActivityNotFoundException) { Toast.makeText(context, "Notification settings unavailable", Toast.LENGTH_SHORT).show() }
            }, { screen = "home" })
            else -> OrbitHome(apps, settings, badgeCounts, viewModel::icon, viewModel::launch, { selected = it }, { initialQuery = ""; screen = "search" }, { screen = "settings" }, { screen = "widgets" }, { screen = "groups" })
        }
    }
    selected?.let { app ->
        if (grouping) {
            AlertDialog(
                onDismissRequest = { grouping = false; selected = null },
                title = { Text("Group ${app.label}") },
                text = { OutlinedTextField(groupName, { groupName = it.take(60) }, label = { Text("Group name; blank removes") }, singleLine = true) },
                confirmButton = { TextButton(onClick = { viewModel.group(app, groupName); grouping = false; selected = null }) { Text("Save") } },
                dismissButton = { TextButton(onClick = { grouping = false; selected = null }) { Text("Cancel") } },
            )
        } else if (renaming) {
            AlertDialog(
                onDismissRequest = { renaming = false; selected = null },
                title = { Text("Rename ${app.label}") },
                text = { OutlinedTextField(newName, { newName = it }, label = { Text("Custom label") }, singleLine = true) },
                confirmButton = { TextButton(onClick = { viewModel.rename(app, newName); renaming = false; selected = null }) { Text("Save") } },
                dismissButton = { TextButton(onClick = { renaming = false; selected = null }) { Text("Cancel") } },
            )
        } else {
            val shortcuts by produceState(emptyList<com.orbit.launcher.core.data.AndroidLauncherRepository.AppShortcut>(), app.key) {
                value = viewModel.shortcuts(app)
            }
            AlertDialog(
                onDismissRequest = { selected = null },
                title = { Text(app.label) },
                text = {
                    androidx.compose.foundation.layout.Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                        shortcuts.take(5).forEach { shortcut ->
                            TextButton(onClick = {
                                if (!viewModel.launchShortcut(app, shortcut.id)) Toast.makeText(context, "Shortcut unavailable", Toast.LENGTH_SHORT).show()
                                selected = null
                            }) { Text(shortcut.label) }
                        }
                        TextButton(onClick = { viewModel.pin(app); selected = null }) { Text(if (app.pinned) "Unpin from inner orbit" else "Pin to inner orbit") }
                        TextButton(onClick = { newName = app.label; renaming = true }) { Text("Rename") }
                        TextButton(onClick = { groupName = app.groupName.orEmpty(); grouping = true }) { Text("Move to group") }
                        TextButton(onClick = { viewModel.searchOnly(app); selected = null }) { Text(if (app.searchOnly) "Show in orbit" else "Search only") }
                        TextButton(onClick = { viewModel.hide(app); selected = null }) { Text("Hide app") }
                        if (!app.isWorkProfile) {
                            TextButton(onClick = {
                                try { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))) }
                                catch (_: ActivityNotFoundException) { Toast.makeText(context, "App info unavailable", Toast.LENGTH_SHORT).show() }
                                selected = null
                            }) { Text("App info") }
                            TextButton(onClick = {
                                try { context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))) }
                                catch (_: ActivityNotFoundException) { Toast.makeText(context, "Uninstall unavailable", Toast.LENGTH_SHORT).show() }
                                selected = null
                            }) { Text("Uninstall") }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { selected = null }) { Text("Close") } },
            )
        }
    }
}
