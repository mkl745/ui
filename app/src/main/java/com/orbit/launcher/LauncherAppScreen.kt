package com.orbit.launcher

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.nativeKeyEvent
import androidx.compose.ui.platform.LocalContext
import com.orbit.launcher.core.domain.LauncherApp
import com.orbit.launcher.feature.home.OrbitHome
import com.orbit.launcher.feature.search.SearchScreen
import com.orbit.launcher.feature.settings.SettingsScreen

@Composable
fun LauncherAppScreen(viewModel: LauncherViewModel, homeRequests: Int) {
    val apps by viewModel.apps.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val badgeCounts by NotificationBadges.badgeCounts.collectAsState()
    var screen by rememberSaveable { mutableStateOf("home") }
    var initialQuery by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<LauncherApp?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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
    LaunchedEffect(homeRequests) { screen = "home"; selected = null; renaming = false }
    BackHandler(enabled = screen != "home" || selected != null) {
        if (selected != null) { selected = null; renaming = false } else screen = "home"
    }
    Surface(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).onPreviewKeyEvent { event ->
        val codepoint = event.nativeKeyEvent.unicodeChar
        if (screen == "home" && selected == null && event.type == KeyEventType.KeyDown && codepoint > 0 && !Character.isISOControl(codepoint)) {
            initialQuery = String(Character.toChars(codepoint))
            screen = "search"
            true
        } else false
    }) {
        when (screen) {
            "search" -> SearchScreen(apps, initialQuery, viewModel::icon, { viewModel.launch(it); screen = "home" }, { screen = "home" })
            "settings" -> SettingsScreen(settings, apps, viewModel::settings, viewModel::icon, viewModel::hide, { export.launch("orbit-backup.json") }, { restore.launch(arrayOf("application/json", "text/json")) }, {
                try { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                catch (_: ActivityNotFoundException) { Toast.makeText(context, "Notification settings unavailable", Toast.LENGTH_SHORT).show() }
            }, { screen = "home" })
            else -> OrbitHome(apps, settings, badgeCounts, viewModel::icon, viewModel::launch, { selected = it }, { initialQuery = ""; screen = "search" }, { screen = "settings" })
        }
    }
    selected?.let { app ->
        if (renaming) {
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
                    androidx.compose.foundation.layout.Column {
                        shortcuts.take(5).forEach { shortcut ->
                            TextButton(onClick = {
                                if (!viewModel.launchShortcut(app, shortcut.id)) Toast.makeText(context, "Shortcut unavailable", Toast.LENGTH_SHORT).show()
                                selected = null
                            }) { Text(shortcut.label) }
                        }
                        TextButton(onClick = { viewModel.pin(app); selected = null }) { Text(if (app.pinned) "Unpin from inner orbit" else "Pin to inner orbit") }
                        TextButton(onClick = { newName = app.label; renaming = true }) { Text("Rename") }
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
