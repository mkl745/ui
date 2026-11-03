package com.orbit.launcher

import android.Manifest
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.spring
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
fun LauncherAppScreen(viewModel: LauncherViewModel, homeRequests: Int, widgetHost: AppWidgetHost, authenticate: (() -> Unit) -> Unit) {
    val apps by viewModel.apps.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val widgets by viewModel.widgets.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val badgeCounts by NotificationBadges.badgeCounts.collectAsState()
    var screen by rememberSaveable { mutableStateOf("home") }
    var initialQuery by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<LauncherApp?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var grouping by remember { mutableStateOf(false) }
    var hiddenUnlocked by remember { mutableStateOf(false) }
    var groupName by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    var contactsRationale by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val homeFocus = remember { FocusRequester() }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) hiddenUnlocked = false }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
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
    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.settings(viewModel.settings.value.copy(contactSearch = granted))
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
    fun performGesture(action: String) {
        when (action) {
            "search" -> { initialQuery = ""; screen = "search" }
            "settings" -> screen = "settings"
            "widgets" -> screen = "widgets"
            "groups" -> screen = "groups"
            "notifications", "quick_settings", "lock" -> {
                val systemAction = when (action) {
                    "notifications" -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
                    "quick_settings" -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
                    else -> if (Build.VERSION.SDK_INT >= 28) AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN else -1
                }
                if (systemAction == -1 || !GestureAccessibilityService.perform(systemAction))
                    Toast.makeText(context, "Enable Orbit gesture accessibility in settings", Toast.LENGTH_LONG).show()
            }
        }
    }
    LaunchedEffect(homeRequests) { screen = "home"; selected = null; renaming = false; grouping = false }
    LaunchedEffect(screen) { if (screen == "home") homeFocus.requestFocus(); if (screen != "settings") hiddenUnlocked = false }
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
        AnimatedContent(targetState = screen, label = "launcher screen", transitionSpec = {
            if (settings.reduceMotion) EnterTransition.None togetherWith ExitTransition.None
            else (fadeIn(spring()) + scaleIn(spring(), initialScale = .97f)) togetherWith (fadeOut(spring()) + scaleOut(spring(), targetScale = .97f))
        }) { destination ->
        when (destination) {
            "search" -> SearchScreen(apps, settings, initialQuery, settings.webSearch, { query ->
                try { context.startActivity(Intent(Intent.ACTION_WEB_SEARCH).putExtra(android.app.SearchManager.QUERY, query)) }
                catch (_: ActivityNotFoundException) { Toast.makeText(context, "No web search app available", Toast.LENGTH_SHORT).show() }
            }, viewModel::contacts, { contact ->
                val uri = ContactsContract.Contacts.getLookupUri(contact.id, contact.lookupKey)
                try { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                catch (_: ActivityNotFoundException) { Toast.makeText(context, "No contacts app available", Toast.LENGTH_SHORT).show() }
            }, viewModel::icon, { viewModel.launch(it); screen = "home" }, { screen = "home" })
            "groups" -> GroupsScreen(apps, settings, viewModel::icon, viewModel::launch, { selected = it }, { screen = "home" })
            "widgets" -> WidgetScreen(widgetHost, widgets, ::addWidget,
                { widget, height -> viewModel.putWidget(widget.widgetId, height) },
                { widget -> viewModel.removeWidget(widget.widgetId); widgetHost.deleteAppWidgetId(widget.widgetId) },
                { screen = "home" })
            "settings" -> SettingsScreen(settings, apps, viewModel::settings, viewModel::icon, viewModel::hide, viewModel::searchOnly,
                { if (settings.lockHidden && !hiddenUnlocked) authenticate { hiddenUnlocked = true; export.launch("orbit-backup.json") } else export.launch("orbit-backup.json") },
                { if (settings.lockHidden && !hiddenUnlocked) authenticate { hiddenUnlocked = true; restore.launch(arrayOf("application/json", "text/json")) } else restore.launch(arrayOf("application/json", "text/json")) }, {
                try { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                catch (_: ActivityNotFoundException) { Toast.makeText(context, "Notification settings unavailable", Toast.LENGTH_SHORT).show() }
            }, {
                try { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                catch (_: ActivityNotFoundException) { Toast.makeText(context, "Accessibility settings unavailable", Toast.LENGTH_SHORT).show() }
            }, {
                try { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                catch (_: ActivityNotFoundException) { Toast.makeText(context, "Usage settings unavailable", Toast.LENGTH_SHORT).show() }
            }, { enabled ->
                if (!enabled) viewModel.settings(settings.copy(contactSearch = false))
                else if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) viewModel.settings(settings.copy(contactSearch = true))
                else contactsRationale = true
            }, hiddenUnlocked, { authenticate { hiddenUnlocked = true } }, {
                authenticate { viewModel.settings(settings.copy(lockHidden = !settings.lockHidden)); hiddenUnlocked = !settings.lockHidden }
            }, { screen = "home" })
            else -> OrbitHome(apps, suggestions, settings, badgeCounts, viewModel::icon, viewModel::launch, { selected = it }, { initialQuery = ""; screen = "search" }, { screen = "settings" }, { screen = "widgets" }, { screen = "groups" }, ::performGesture)
        }
        }
    }
    if (contactsRationale) AlertDialog(
        onDismissRequest = { contactsRationale = false },
        title = { Text("Search contacts") },
        text = { Text("Orbit reads contact names on this device only when you search. Contacts are not stored or sent to a server. You can turn this off at any time.") },
        confirmButton = { TextButton(onClick = { contactsRationale = false; contactsPermission.launch(Manifest.permission.READ_CONTACTS) }) { Text("Continue") } },
        dismissButton = { TextButton(onClick = { contactsRationale = false }) { Text("Not now") } },
    )
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
