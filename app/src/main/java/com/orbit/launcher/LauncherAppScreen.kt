package com.orbit.launcher

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.orbit.launcher.core.domain.LauncherApp
import com.orbit.launcher.feature.home.OrbitHome
import com.orbit.launcher.feature.search.SearchScreen
import com.orbit.launcher.feature.settings.SettingsScreen

@Composable
fun LauncherAppScreen(viewModel: LauncherViewModel, homeRequests: Int) {
    val apps by viewModel.apps.collectAsState()
    val settings by viewModel.settings.collectAsState()
    var screen by rememberSaveable { mutableStateOf("home") }
    var selected by remember { mutableStateOf<LauncherApp?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    val context = LocalContext.current
    LaunchedEffect(homeRequests) { screen = "home"; selected = null; renaming = false }
    BackHandler(enabled = screen != "home" || selected != null) {
        if (selected != null) { selected = null; renaming = false } else screen = "home"
    }
    Surface(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (screen) {
            "search" -> SearchScreen(apps, viewModel::icon, { viewModel.launch(it); screen = "home" }, { screen = "home" })
            "settings" -> SettingsScreen(settings, apps, viewModel::settings, viewModel::hide, { screen = "home" })
            else -> OrbitHome(apps, settings, viewModel::icon, viewModel::launch, { selected = it }, { screen = "search" }, { screen = "settings" })
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
            AlertDialog(
                onDismissRequest = { selected = null },
                title = { Text(app.label) },
                text = {
                    androidx.compose.foundation.layout.Column {
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
