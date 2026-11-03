package com.orbit.launcher

import android.graphics.Bitmap
import android.net.Uri
import com.orbit.launcher.core.data.BackupManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbit.launcher.core.data.AndroidLauncherRepository
import com.orbit.launcher.core.domain.LauncherApp
import com.orbit.launcher.core.domain.LauncherRepository
import com.orbit.launcher.core.domain.LauncherSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class LauncherViewModel @Inject constructor(
    private val repository: LauncherRepository,
    private val androidRepository: AndroidLauncherRepository,
    private val backupManager: BackupManager,
) : ViewModel() {
    val apps = repository.apps.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings = repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LauncherSettings())
    fun launch(app: LauncherApp) { viewModelScope.launch { repository.launch(app) } }
    fun pin(app: LauncherApp) { viewModelScope.launch { repository.setPinned(app, !app.pinned) } }
    fun hide(app: LauncherApp) { viewModelScope.launch { repository.setHidden(app, !app.hidden) } }
    fun rename(app: LauncherApp, label: String?) { viewModelScope.launch { repository.setLabel(app, label) } }
    fun settings(value: LauncherSettings) { viewModelScope.launch { repository.updateSettings(value) } }
    suspend fun shortcuts(app: LauncherApp) = androidRepository.shortcuts(app)
    fun launchShortcut(app: LauncherApp, id: String) = androidRepository.launchShortcut(app, id)
    suspend fun exportBackup(uri: Uri) = backupManager.export(uri)
    suspend fun restoreBackup(uri: Uri) = backupManager.restore(uri)
    suspend fun icon(app: LauncherApp, pixels: Int): Bitmap? = androidRepository.icon(app, pixels)
}
