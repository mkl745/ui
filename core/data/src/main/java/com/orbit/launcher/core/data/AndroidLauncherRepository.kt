package com.orbit.launcher.core.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.orbit.launcher.core.domain.LauncherApp
import com.orbit.launcher.core.domain.LauncherRepository
import com.orbit.launcher.core.domain.LauncherSettings
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.launcherSettings by preferencesDataStore(name = "launcher_settings")
private val SHOW_LABELS = booleanPreferencesKey("show_labels")
private val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
private val ICON_SIZE = intPreferencesKey("icon_size")

@Singleton
class AndroidLauncherRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: AppPreferenceDao,
) : LauncherRepository {
    private val launcher = context.getSystemService(LauncherApps::class.java)
    private val users = context.getSystemService(UserManager::class.java)
    private val updateLock = Mutex()
    private val iconCache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private fun user(serial: Long): UserHandle? = users.getUserForSerialNumber(serial)

    private fun discover(): List<LauncherApp> = users.userProfiles.flatMap { profile ->
        val serial = users.getSerialNumberForUser(profile)
        try {
            launcher.getActivityList(null, profile).map { activity ->
                val component = activity.componentName
                LauncherApp(
                    key = "$serial:${component.flattenToString()}",
                    packageName = component.packageName,
                    componentName = component.flattenToString(),
                    userSerial = serial,
                    label = activity.label.toString(),
                    isWorkProfile = profile != Process.myUserHandle(),
                )
            }
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    private val discovered = callbackFlow {
        fun refresh() { trySend(Unit) }
        val callback = object : LauncherApps.Callback() {
            override fun onPackageAdded(packageName: String, user: UserHandle) = refresh()
            override fun onPackageRemoved(packageName: String, user: UserHandle) = refresh()
            override fun onPackageChanged(packageName: String, user: UserHandle) { iconCache.evictAll(); refresh() }
            override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh()
            override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh()
        }
        launcher.registerCallback(callback, Handler(Looper.getMainLooper()))
        refresh()
        awaitClose { launcher.unregisterCallback(callback) }
    }.conflate().map { discover() }.flowOn(Dispatchers.IO)

    override val apps: Flow<List<LauncherApp>> = combine(discovered, dao.observeAll()) { apps, preferences ->
        val byKey = preferences.associateBy { it.key }
        apps.map { app ->
            val preference = byKey[app.key]
            app.copy(
                label = preference?.customLabel?.takeIf { it.isNotBlank() } ?: app.label,
                pinned = preference?.pinned ?: false,
                hidden = preference?.hidden ?: false,
                launches = preference?.launches ?: 0,
                lastLaunched = preference?.lastLaunched ?: 0,
            )
        }
    }

    override val settings = context.launcherSettings.data.map {
        LauncherSettings(it[SHOW_LABELS] ?: true, it[REDUCE_MOTION] ?: false, (it[ICON_SIZE] ?: 48).coerceIn(32, 72))
    }

    override suspend fun updateSettings(settings: LauncherSettings) {
        context.launcherSettings.edit {
            it[SHOW_LABELS] = settings.showLabels
            it[REDUCE_MOTION] = settings.reduceMotion
            it[ICON_SIZE] = settings.iconSize.coerceIn(32, 72)
        }
    }

    private suspend fun update(app: LauncherApp, change: (AppPreference) -> AppPreference) {
        updateLock.withLock { dao.put(change(dao.find(app.key) ?: AppPreference(app.key))) }
    }

    override suspend fun setPinned(app: LauncherApp, pinned: Boolean) = update(app) { it.copy(pinned = pinned) }
    override suspend fun setHidden(app: LauncherApp, hidden: Boolean) = update(app) { it.copy(hidden = hidden) }
    override suspend fun setLabel(app: LauncherApp, label: String?) = update(app) { it.copy(customLabel = label?.trim()?.takeIf(String::isNotEmpty)) }

    override suspend fun launch(app: LauncherApp): Boolean {
        val component = ComponentName.unflattenFromString(app.componentName) ?: return false
        val profile = user(app.userSerial) ?: return false
        return try {
            launcher.startMainActivity(component, profile, null, null)
            update(app) { it.copy(launches = it.launches + 1, lastLaunched = System.currentTimeMillis()) }
            true
        } catch (_: SecurityException) { false } catch (_: android.content.ActivityNotFoundException) { false }
    }

    data class AppShortcut(val id: String, val label: String)

    suspend fun shortcuts(app: LauncherApp): List<AppShortcut> = withContext(Dispatchers.IO) {
        try {
            val profile = user(app.userSerial) ?: return@withContext emptyList()
            if (!launcher.hasShortcutHostPermission()) return@withContext emptyList()
            val query = LauncherApps.ShortcutQuery().setPackage(app.packageName).setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
            )
            launcher.getShortcuts(query, profile).orEmpty().map { AppShortcut(it.id, (it.shortLabel ?: it.id).toString()) }
        } catch (_: SecurityException) { emptyList() } catch (_: IllegalStateException) { emptyList() }
    }

    fun launchShortcut(app: LauncherApp, id: String): Boolean {
        val profile = user(app.userSerial) ?: return false
        return try {
            launcher.startShortcut(app.packageName, id, null, null, profile)
            true
        } catch (_: SecurityException) { false } catch (_: android.content.ActivityNotFoundException) { false }
    }

    suspend fun icon(app: LauncherApp, pixels: Int): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "${app.key}:$pixels"
        iconCache.get(cacheKey) ?: try {
            val profile = user(app.userSerial) ?: return@withContext null
            val info = launcher.getActivityList(app.packageName, profile).firstOrNull {
                it.componentName.flattenToString() == app.componentName
            } ?: return@withContext null
            info.getBadgedIcon(0).toBitmap(pixels, pixels, Bitmap.Config.ARGB_8888).also { iconCache.put(cacheKey, it) }
        } catch (_: SecurityException) { null }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun bindRepository(implementation: AndroidLauncherRepository): LauncherRepository
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton fun database(@ApplicationContext context: Context): LauncherDatabase =
        Room.databaseBuilder(context, LauncherDatabase::class.java, "orbit.db").build()
    @Provides fun preferences(database: LauncherDatabase): AppPreferenceDao = database.preferences()
}
