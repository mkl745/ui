package com.orbit.launcher.core.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
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
import androidx.datastore.preferences.core.stringPreferencesKey
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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
private val WEB_SEARCH = booleanPreferencesKey("web_search")
private val LOCK_HIDDEN = booleanPreferencesKey("lock_hidden")
private val THEME_MODE = stringPreferencesKey("theme_mode")
private val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
private val PALETTE = stringPreferencesKey("palette")
private val FONT = stringPreferencesKey("font")
private val CORNER_RADIUS = intPreferencesKey("corner_radius")
private val ICON_SHAPE = stringPreferencesKey("icon_shape")
private val THEMED_ICONS = booleanPreferencesKey("themed_icons")
private val SWIPE_UP = stringPreferencesKey("swipe_up")
private val SWIPE_DOWN = stringPreferencesKey("swipe_down")
private val SWIPE_LEFT = stringPreferencesKey("swipe_left")
private val SWIPE_RIGHT = stringPreferencesKey("swipe_right")
private val DOUBLE_TAP = stringPreferencesKey("double_tap")
private val EMPTY_LONG_PRESS = stringPreferencesKey("empty_long_press")
private val TWO_FINGER = stringPreferencesKey("two_finger")
private val gestureActions = setOf("none", "search", "settings", "widgets", "groups", "notifications", "quick_settings", "lock")

@Singleton
class AndroidLauncherRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: AppPreferenceDao,
    private val diskCache: IconDiskCache,
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
            override fun onPackageAdded(packageName: String, user: UserHandle) { iconCache.evictAll(); refresh() }
            override fun onPackageRemoved(packageName: String, user: UserHandle) { iconCache.evictAll(); refresh() }
            override fun onPackageChanged(packageName: String, user: UserHandle) { iconCache.evictAll(); refresh() }
            override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) { iconCache.evictAll(); refresh() }
            override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) { iconCache.evictAll(); refresh() }
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
                searchOnly = preference?.searchOnly ?: false,
                groupName = preference?.groupName,
            )
        }
    }

    override val settings = context.launcherSettings.data.map {
        LauncherSettings(
            showLabels = it[SHOW_LABELS] ?: true, reduceMotion = it[REDUCE_MOTION] ?: false,
            iconSize = (it[ICON_SIZE] ?: 48).coerceIn(32, 72), webSearch = it[WEB_SEARCH] ?: false,
            lockHidden = it[LOCK_HIDDEN] ?: false, themeMode = it[THEME_MODE] ?: "dark",
            dynamicColor = it[DYNAMIC_COLOR] ?: false, palette = it[PALETTE] ?: "violet",
            font = it[FONT] ?: "sans", cornerRadius = (it[CORNER_RADIUS] ?: 20).coerceIn(0, 32),
            iconShape = it[ICON_SHAPE] ?: "circle", themedIcons = it[THEMED_ICONS] ?: false,
            swipeUp = it[SWIPE_UP] ?: "search", swipeDown = it[SWIPE_DOWN] ?: "notifications",
            swipeLeft = it[SWIPE_LEFT] ?: "none", swipeRight = it[SWIPE_RIGHT] ?: "none",
            doubleTap = it[DOUBLE_TAP] ?: "search", emptyLongPress = it[EMPTY_LONG_PRESS] ?: "settings", twoFinger = it[TWO_FINGER] ?: "none",
        )
    }

    override suspend fun updateSettings(settings: LauncherSettings) {
        context.launcherSettings.edit {
            it[SHOW_LABELS] = settings.showLabels
            it[REDUCE_MOTION] = settings.reduceMotion
            it[ICON_SIZE] = settings.iconSize.coerceIn(32, 72)
            it[WEB_SEARCH] = settings.webSearch
            it[LOCK_HIDDEN] = settings.lockHidden
            it[THEME_MODE] = settings.themeMode.takeIf { value -> value in setOf("system", "dark", "light", "amoled") } ?: "dark"
            it[DYNAMIC_COLOR] = settings.dynamicColor
            it[PALETTE] = settings.palette.takeIf { value -> value in setOf("violet", "cyan", "amber") } ?: "violet"
            it[FONT] = settings.font.takeIf { value -> value in setOf("sans", "serif", "mono") } ?: "sans"
            it[CORNER_RADIUS] = settings.cornerRadius.coerceIn(0, 32)
            it[ICON_SHAPE] = settings.iconShape.takeIf { value -> value in setOf("circle", "squircle", "teardrop", "square") } ?: "circle"
            it[THEMED_ICONS] = settings.themedIcons
            it[SWIPE_UP] = settings.swipeUp.takeIf { v -> v in gestureActions } ?: "none"
            it[SWIPE_DOWN] = settings.swipeDown.takeIf { v -> v in gestureActions } ?: "none"
            it[SWIPE_LEFT] = settings.swipeLeft.takeIf { v -> v in gestureActions } ?: "none"
            it[SWIPE_RIGHT] = settings.swipeRight.takeIf { v -> v in gestureActions } ?: "none"
            it[DOUBLE_TAP] = settings.doubleTap.takeIf { v -> v in gestureActions } ?: "none"
            it[EMPTY_LONG_PRESS] = settings.emptyLongPress.takeIf { v -> v in gestureActions } ?: "none"
            it[TWO_FINGER] = settings.twoFinger.takeIf { v -> v in gestureActions } ?: "none"
        }
    }

    private suspend fun update(app: LauncherApp, change: (AppPreference) -> AppPreference) {
        updateLock.withLock { dao.put(change(dao.find(app.key) ?: AppPreference(app.key))) }
    }

    override suspend fun setPinned(app: LauncherApp, pinned: Boolean) = update(app) { it.copy(pinned = pinned) }
    override suspend fun setHidden(app: LauncherApp, hidden: Boolean) = update(app) { it.copy(hidden = hidden) }
    override suspend fun setLabel(app: LauncherApp, label: String?) = update(app) { it.copy(customLabel = label?.trim()?.takeIf(String::isNotEmpty)?.take(100)) }
    override suspend fun setSearchOnly(app: LauncherApp, searchOnly: Boolean) = update(app) { it.copy(searchOnly = searchOnly) }
    override suspend fun setGroup(app: LauncherApp, group: String?) = update(app) { it.copy(groupName = group?.trim()?.takeIf(String::isNotEmpty)?.take(60)) }

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

    suspend fun icon(app: LauncherApp, pixels: Int, settings: LauncherSettings): Bitmap? = withContext(Dispatchers.IO) {
        @Suppress("DEPRECATION")
        val updatedAt = try { context.packageManager.getPackageInfo(app.packageName, 0).lastUpdateTime } catch (_: android.content.pm.PackageManager.NameNotFoundException) { 0L }
        val night = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val dynamicAccent = if (settings.dynamicColor && Build.VERSION.SDK_INT >= 31) {
            context.getColor(if (settings.themeMode == "light" || (settings.themeMode == "system" && night != android.content.res.Configuration.UI_MODE_NIGHT_YES)) android.R.color.system_accent1_700 else android.R.color.system_accent1_200)
        } else 0
        val cacheKey = "${app.key}:$pixels:${settings.themedIcons}:${settings.palette}:${settings.dynamicColor}:${settings.themeMode}:$night:$dynamicAccent:$updatedAt"
        iconCache.get(cacheKey) ?: (if (updatedAt != 0L) diskCache.read(cacheKey, pixels) else null)?.also { iconCache.put(cacheKey, it) } ?: try {
            val profile = user(app.userSerial) ?: return@withContext null
            val info = launcher.getActivityList(app.packageName, profile).firstOrNull {
                it.componentName.flattenToString() == app.componentName
            } ?: return@withContext null
            val adaptive = if (Build.VERSION.SDK_INT >= 33 && settings.themedIcons && !app.isWorkProfile) info.getIcon(0) as? AdaptiveIconDrawable else null
            val monochrome = if (Build.VERSION.SDK_INT >= 33) adaptive?.monochrome else null
            val bitmap = if (monochrome != null) {
                val dark = settings.themeMode != "light" && (settings.themeMode != "system" || night == android.content.res.Configuration.UI_MODE_NIGHT_YES)
                val color = if (settings.dynamicColor && Build.VERSION.SDK_INT >= 31) {
                    context.getColor(if (dark) android.R.color.system_accent1_200 else android.R.color.system_accent1_700)
                } else when (settings.palette) {
                    "cyan" -> if (dark) Color.rgb(155, 232, 236) else Color.rgb(0, 105, 109)
                    "amber" -> if (dark) Color.rgb(255, 214, 138) else Color.rgb(121, 89, 0)
                    else -> if (dark) Color.rgb(187, 199, 255) else Color.rgb(68, 88, 165)
                }
                Bitmap.createBitmap(pixels, pixels, Bitmap.Config.ARGB_8888).also { result ->
                    val canvas = Canvas(result)
                    canvas.drawColor(if (dark) Color.rgb(35, 41, 57) else Color.rgb(232, 235, 248))
                    val icon = (monochrome.constantState?.newDrawable() ?: monochrome).mutate()
                    icon.setTint(color)
                    val inset = pixels / 8
                    icon.setBounds(inset, inset, pixels - inset, pixels - inset)
                    icon.draw(canvas)
                }
            } else info.getBadgedIcon(0).toBitmap(pixels, pixels, Bitmap.Config.ARGB_8888)
            bitmap.also { iconCache.put(cacheKey, it); if (updatedAt != 0L) diskCache.write(cacheKey, it) }
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
        Room.databaseBuilder(context, LauncherDatabase::class.java, "orbit.db")
            .addMigrations(object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS `widgets` (`widgetId` INTEGER NOT NULL, `heightDp` INTEGER NOT NULL, PRIMARY KEY(`widgetId`))")
                }
            }, object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `app_preferences` ADD COLUMN `searchOnly` INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE `app_preferences` ADD COLUMN `groupName` TEXT")
                }
            }).build()
    @Provides fun preferences(database: LauncherDatabase): AppPreferenceDao = database.preferences()
    @Provides fun widgets(database: LauncherDatabase): WidgetDao = database.widgets()
}
