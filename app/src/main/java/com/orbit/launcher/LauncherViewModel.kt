package com.orbit.launcher

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.provider.DocumentsContract
import com.orbit.launcher.core.domain.DocumentResult
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import com.orbit.launcher.core.domain.ContactResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.Bitmap
import android.net.Uri
import com.orbit.launcher.core.data.BackupManager
import com.orbit.launcher.core.data.WidgetDao
import com.orbit.launcher.core.data.WidgetPlacement
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
    private val widgetDao: WidgetDao,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val widgets = widgetDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun putWidget(id: Int, height: Int, provider: String? = null) { viewModelScope.launch { widgetDao.put(WidgetPlacement(id, height.coerceIn(100, 600), provider)) } }
    fun replaceWidget(placeholderId: Int, id: Int, height: Int, provider: String?) { viewModelScope.launch { widgetDao.replacePlaceholder(placeholderId, WidgetPlacement(id, height.coerceIn(100, 600), provider)) } }
    fun removeWidget(id: Int) { viewModelScope.launch { widgetDao.delete(id) } }
    val suggestions = repository.suggestedApps.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun refreshSuggestions() = repository.refreshSuggestions()
    val apps = repository.apps.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings = repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LauncherSettings())
    fun launch(app: LauncherApp) { viewModelScope.launch { repository.launch(app) } }
    fun pin(app: LauncherApp) { viewModelScope.launch { repository.setPinned(app, !app.pinned) } }
    fun hide(app: LauncherApp) { viewModelScope.launch { repository.setHidden(app, !app.hidden) } }
    fun customIcon(app: LauncherApp, uri: String?) { viewModelScope.launch { repository.setCustomIcon(app, uri) } }
    fun searchOnly(app: LauncherApp) { viewModelScope.launch { repository.setSearchOnly(app, !app.searchOnly) } }
    fun group(app: LauncherApp, name: String?) { viewModelScope.launch { repository.setGroup(app, name) } }
    fun rename(app: LauncherApp, label: String?) { viewModelScope.launch { repository.setLabel(app, label) } }
    fun settings(value: LauncherSettings) { viewModelScope.launch { repository.updateSettings(value) } }
    suspend fun contacts(query: String): List<ContactResult> = withContext(Dispatchers.IO) {
        val text = query.trim().take(80)
        if (text.length < 2 || ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return@withContext emptyList()
        val escaped = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        try {
            context.contentResolver.query(ContactsContract.Contacts.CONTENT_URI, arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ? ESCAPE '\\'", arrayOf("%$escaped%"), "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} COLLATE NOCASE ASC")?.use { cursor ->
                val results = mutableListOf<ContactResult>()
                while (cursor.moveToNext() && results.size < 20) {
                    val key = cursor.getString(1) ?: continue
                    val name = cursor.getString(2) ?: continue
                    results.add(ContactResult(cursor.getLong(0), key, name))
                }
                results
            } ?: emptyList()
        } catch (_: SecurityException) { emptyList() } catch (_: IllegalArgumentException) { emptyList() }
    }
    suspend fun installedIconPacks() = androidRepository.installedIconPacks()
    suspend fun searchShortcuts(query: String) = androidRepository.searchShortcuts(query, apps.value)
    suspend fun documents(query: String): List<DocumentResult> = withContext(Dispatchers.IO) {
        val tree = settings.value.documentTree.takeIf { it.isNotBlank() } ?: return@withContext emptyList()
        try {
            val uri = Uri.parse(tree)
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
            val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            context.contentResolver.query(children, columns, null, null, null)?.use { cursor ->
                val results = mutableListOf<DocumentResult>()
                var scanned = 0
                while (cursor.moveToNext() && scanned++ < 1000) {
                    val id = cursor.getString(0) ?: continue
                    val name = cursor.getString(1) ?: continue
                    val type = cursor.getString(2) ?: "application/octet-stream"
                    if (type != DocumentsContract.Document.MIME_TYPE_DIR && name.contains(query.trim(), ignoreCase = true)) {
                        results.add(DocumentResult(DocumentsContract.buildDocumentUriUsingTree(uri, id).toString(), name, type, cursor.getLong(3)))
                    }
                }
                results.sortedByDescending { it.lastModified }.take(20)
            } ?: emptyList()
        } catch (_: SecurityException) { emptyList() } catch (_: IllegalArgumentException) { emptyList() }
    }
    suspend fun shortcuts(app: LauncherApp) = androidRepository.shortcuts(app)
    fun launchShortcut(app: LauncherApp, id: String) = androidRepository.launchShortcut(app, id)
    suspend fun exportBackup(uri: Uri) = backupManager.export(uri)
    suspend fun restoreBackup(uri: Uri) = backupManager.restore(uri)
    suspend fun icon(app: LauncherApp, pixels: Int): Bitmap? = androidRepository.icon(app, pixels, settings.value)
}
