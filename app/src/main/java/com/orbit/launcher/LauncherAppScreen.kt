package com.orbit.launcher

import android.Manifest
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
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
import androidx.activity.compose.PredictiveBackHandler
import kotlinx.coroutines.CancellationException
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import com.orbit.launcher.core.domain.LauncherApp
import com.orbit.launcher.feature.home.OrbitHome
import com.orbit.launcher.feature.search.SearchScreen
import com.orbit.launcher.feature.settings.SettingsScreen

@Composable
fun LauncherAppScreen(viewModel: LauncherViewModel, homeRequests: Int, widgetHost: AppWidgetHost, authenticate: (() -> Unit) -> Unit, onBlur: (Int) -> Unit) {
    val apps by viewModel.apps.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val badgeCounts by NotificationBadges.badgeCounts.collectAsState()
    var screen by rememberSaveable { mutableStateOf("home") }
    var packs by remember { mutableStateOf(emptyList<com.orbit.launcher.core.domain.IconPack>()) }
    LaunchedEffect(screen) { if (screen == "settings") packs = viewModel.installedIconPacks() }
    var initialQuery by rememberSaveable { mutableStateOf("") }
    var backProgress by remember { mutableFloatStateOf(0f) }
    var selected by remember { mutableStateOf<LauncherApp?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var grouping by remember { mutableStateOf(false) }
    var hiddenUnlocked by remember { mutableStateOf(false) }
    var groupName by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    var contactsRationale by remember { mutableStateOf(false) }
    var locationRationale by remember { mutableStateOf(false) }
    var torchRationale by remember { mutableStateOf(false) }
    val torchStates = remember { mutableStateMapOf<String, Boolean>() }
    var pendingIconKey by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val homeFocus = remember { FocusRequester() }
    val cameraManager = remember { context.getSystemService(CameraManager::class.java) }
    val torchConfigured = listOf(settings.swipeUp, settings.swipeDown, settings.swipeLeft, settings.swipeRight, settings.doubleTap, settings.emptyLongPress, settings.twoFinger).contains("torch")
    DisposableEffect(cameraManager, torchConfigured) {
        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) { torchStates[cameraId] = enabled }
            override fun onTorchModeUnavailable(cameraId: String) { torchStates.remove(cameraId) }
        }
        if (torchConfigured) try { cameraManager.registerTorchCallback(callback, Handler(Looper.getMainLooper())) } catch (_: SecurityException) { }
        onDispose { if (torchConfigured) cameraManager.unregisterTorchCallback(callback) }
    }
    fun toggleTorch() {
        try {
            val id = cameraManager.cameraIdList.firstOrNull { cameraId ->
                val info = cameraManager.getCameraCharacteristics(cameraId)
                info.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true && info.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
            if (id == null) { Toast.makeText(context, "No torch available", Toast.LENGTH_SHORT).show(); return }
            cameraManager.setTorchMode(id, torchStates[id] != true)
        } catch (_: CameraAccessException) { Toast.makeText(context, "Torch unavailable", Toast.LENGTH_SHORT).show() }
        catch (_: SecurityException) { Toast.makeText(context, "Camera permission required", Toast.LENGTH_SHORT).show() }
    }
    val torchPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) toggleTorch() else Toast.makeText(context, "Camera permission not granted", Toast.LENGTH_SHORT).show()
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) hiddenUnlocked = false }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val widgetManager = remember { AppWidgetManager.getInstance(context) }
    var pendingWidgetId by rememberSaveable { mutableIntStateOf(AppWidgetManager.INVALID_APPWIDGET_ID) }
    var pendingPlaceholder by rememberSaveable { mutableIntStateOf(AppWidgetManager.INVALID_APPWIDGET_ID) }
    var pendingWidgetHeight by rememberSaveable { mutableIntStateOf(180) }
    var pendingWidgetWidth by rememberSaveable { mutableIntStateOf(0) }
    fun cancelPendingWidget() {
        if (pendingWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) widgetHost.deleteAppWidgetId(pendingWidgetId)
        pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        pendingPlaceholder = AppWidgetManager.INVALID_APPWIDGET_ID
    }
    fun savePendingWidget() {
        if (pendingWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            val provider = widgetManager.getAppWidgetInfo(pendingWidgetId)?.provider?.flattenToString()
            if (pendingPlaceholder < AppWidgetManager.INVALID_APPWIDGET_ID) viewModel.replaceWidget(pendingPlaceholder, pendingWidgetId, pendingWidgetHeight, provider, pendingWidgetWidth)
            else viewModel.putWidget(pendingWidgetId, pendingWidgetHeight, provider, pendingWidgetWidth)
            pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
            pendingPlaceholder = AppWidgetManager.INVALID_APPWIDGET_ID
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
    fun addWidget(provider: AppWidgetProviderInfo, placeholder: com.orbit.launcher.core.data.WidgetPlacement? = null) {
        if (pendingWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) return
        pendingPlaceholder = placeholder?.widgetId ?: AppWidgetManager.INVALID_APPWIDGET_ID
        pendingWidgetHeight = placeholder?.heightDp ?: 180
        pendingWidgetWidth = placeholder?.widthDp ?: 0
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
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.settings(viewModel.settings.value.copy(locationCategories = granted))
    }
    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.settings(viewModel.settings.value.copy(contactSearch = granted))
    }
    val iconPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val app = viewModel.apps.value.firstOrNull { it.key == pendingIconKey }
        pendingIconKey = ""
        if (uri != null && app != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                viewModel.customIcon(app, uri.toString())
            } catch (_: SecurityException) { Toast.makeText(context, "Could not keep icon access", Toast.LENGTH_SHORT).show() }
        }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val old = viewModel.settings.value.documentTree
                if (old.isNotBlank() && old != uri.toString()) {
                    try { context.contentResolver.releasePersistableUriPermission(Uri.parse(old), Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: SecurityException) { }
                }
                viewModel.settings(viewModel.settings.value.copy(documentTree = uri.toString()))
            } catch (_: SecurityException) { Toast.makeText(context, "Could not keep folder access", Toast.LENGTH_SHORT).show() }
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
    fun performGesture(action: String) {
        when {
            action.startsWith("app:") -> {
                val app = apps.firstOrNull { it.key == action.removePrefix("app:") && !it.hidden }
                if (app != null) viewModel.launch(app) else Toast.makeText(context, "Gesture app unavailable", Toast.LENGTH_SHORT).show()
            }
            action == "torch" -> {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) toggleTorch() else torchRationale = true
            }
            action == "search" -> { initialQuery = ""; screen = "search" }
            action == "settings" -> screen = "settings"
            action == "widgets" -> screen = "widgets"
            action == "groups" -> screen = "groups"
            action in setOf("notifications", "quick_settings", "lock") -> {
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
    LaunchedEffect(screen, settings.wallpaperEnabled, settings.blurStrength) {
        onBlur(if (settings.wallpaperEnabled && screen != "home") settings.blurStrength else 0)
    }
    PredictiveBackHandler(enabled = screen != "home" || selected != null) { progress ->
        try {
            progress.collect { event -> backProgress = event.progress }
            if (selected != null) { selected = null; renaming = false; grouping = false } else screen = "home"
        } catch (_: CancellationException) {
            // The user cancelled the back gesture; keep the current screen.
        } finally {
            backProgress = 0f
        }
    }
    Surface(Modifier.fillMaxSize().onPreviewKeyEvent { event ->
        val codepoint = event.nativeKeyEvent.unicodeChar
        if (screen == "home" && selected == null && event.type == KeyEventType.KeyDown && codepoint > 0 && !Character.isISOControl(codepoint)) {
            initialQuery = String(Character.toChars(codepoint))
            screen = "search"
            true
        } else false
    }.focusRequester(homeFocus).focusable().graphicsLayer {
        val progress = if (settings.reduceMotion) 0f else backProgress
        scaleX = 1f - progress * .04f
        scaleY = 1f - progress * .04f
        alpha = 1f - progress * .15f
    }, color = if (settings.wallpaperEnabled) { if (screen == "home") Color.Transparent else MaterialTheme.colorScheme.background.copy(alpha = .85f) } else MaterialTheme.colorScheme.background) {
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
            }, viewModel::documents, { document ->
                try { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(document.uri), document.mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                catch (_: ActivityNotFoundException) { Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show() }
                catch (_: SecurityException) { Toast.makeText(context, "File access was revoked", Toast.LENGTH_SHORT).show() }
            }, viewModel::searchShortcuts, { shortcut ->
                if (!viewModel.launchShortcut(shortcut.app, shortcut.id)) Toast.makeText(context, "Shortcut unavailable", Toast.LENGTH_SHORT).show()
            }, viewModel::icon, { viewModel.launch(it); screen = "home" }, { screen = "home" })
            "groups" -> GroupsScreen(apps, settings, viewModel::icon, viewModel::launch, { selected = it }, { screen = "home" })
            "widgets" -> {
                val widgets by viewModel.widgets.collectAsState()
                WidgetScreen(widgetHost, widgets, { addWidget(it) },
                    { widget, height, width -> viewModel.putWidget(widget.widgetId, height, widget.providerName, width) },
                    { widget -> viewModel.removeWidget(widget.widgetId); if (widget.widgetId >= 0) widgetHost.deleteAppWidgetId(widget.widgetId) },
                    { widget, provider -> addWidget(provider, widget) },
                    { screen = "home" })
            }
            "settings" -> SettingsScreen(settings, apps, packs, viewModel::settings, viewModel::icon, viewModel::hide, viewModel::searchOnly,
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
            }, { folderPicker.launch(null) }, {
                val old = settings.documentTree
                if (old.isNotBlank()) try { context.contentResolver.releasePersistableUriPermission(Uri.parse(old), Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: SecurityException) { }
                viewModel.settings(settings.copy(documentTree = ""))
            }, { enabled ->
                if (!enabled) viewModel.settings(settings.copy(locationCategories = false))
                else if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) viewModel.settings(settings.copy(locationCategories = true))
                else locationRationale = true
            }, { place ->
                scope.launch {
                    val saved = viewModel.setLocationAnchor(place)
                    Toast.makeText(context, if (saved) "Place saved on this device" else "No recent location; enable location and try again", Toast.LENGTH_LONG).show()
                }
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
    if (locationRationale) AlertDialog(
        onDismissRequest = { locationRationale = false },
        title = { Text("Location categories") },
        text = { Text("Orbit can use the device's recent approximate location to distinguish home, work and other places. Rounded places and category history stay on this device. Turn this off to delete them.") },
        confirmButton = { TextButton(onClick = { locationRationale = false; locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }) { Text("Continue") } },
        dismissButton = { TextButton(onClick = { locationRationale = false }) { Text("Not now") } },
    )
    if (torchRationale) AlertDialog(
        onDismissRequest = { torchRationale = false },
        title = { Text("Toggle the torch") },
        text = { Text("Orbit needs camera access to switch the torch. It does not take photos or record video.") },
        confirmButton = { TextButton(onClick = { torchRationale = false; torchPermission.launch(Manifest.permission.CAMERA) }) { Text("Continue") } },
        dismissButton = { TextButton(onClick = { torchRationale = false }) { Text("Not now") } },
    )
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
            var shortcuts by remember(app.key) { mutableStateOf(emptyList<com.orbit.launcher.core.data.AndroidLauncherRepository.AppShortcut>()) }
            LaunchedEffect(app.key) { shortcuts = viewModel.shortcuts(app) }
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
                        TextButton(onClick = { pendingIconKey = app.key; selected = null; iconPicker.launch(arrayOf("image/*")) }) { Text("Choose custom icon") }
                        if (app.customIconUri != null) TextButton(onClick = { viewModel.customIcon(app, null); selected = null }) { Text("Reset icon") }
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
