package com.orbit.launcher.core.data

import android.content.ComponentName
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import java.util.Locale
import android.content.Context
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.BatteryManager
import java.util.Calendar
import android.content.pm.LauncherApps
import android.content.pm.ApplicationInfo
import android.graphics.BitmapFactory
import android.net.Uri
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
import com.orbit.launcher.core.domain.SearchShortcut
import com.orbit.launcher.core.domain.CurrentContext
import com.orbit.launcher.core.domain.UsageContext
import com.orbit.launcher.core.domain.rankContextApps
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
private val CONTACT_SEARCH = booleanPreferencesKey("contact_search")
private val CONTEXT_SUGGESTIONS = booleanPreferencesKey("context_suggestions")
private val DOCUMENT_TREE = stringPreferencesKey("document_tree")
private val ICON_PACK = stringPreferencesKey("icon_pack")
private val WALLPAPER_ENABLED = booleanPreferencesKey("wallpaper_enabled")
private val WALLPAPER_PARALLAX = booleanPreferencesKey("wallpaper_parallax")
private val BLUR_STRENGTH = intPreferencesKey("blur_strength")
private val LOCATION_CATEGORIES = booleanPreferencesKey("location_categories")
private val HOME_ANCHOR = stringPreferencesKey("home_anchor")
private val WORK_ANCHOR = stringPreferencesKey("work_anchor")
private val gestureActions = setOf("none", "search", "settings", "widgets", "groups", "notifications", "quick_settings", "lock", "torch")
private fun validGesture(value: String) = value in gestureActions || (value.startsWith("app:") && value.length in 5..1028)

@Singleton
class AndroidLauncherRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: AppPreferenceDao,
    private val diskCache: IconDiskCache,
    private val usageDao: UsageContextDao,
    private val iconPacks: IconPackManager,
) : LauncherRepository {
    private val launcher = context.getSystemService(LauncherApps::class.java)
    private val users = context.getSystemService(UserManager::class.java)
    private val updateLock = Mutex()
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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
                    category = when (activity.applicationInfo.category) {
                        ApplicationInfo.CATEGORY_GAME -> "Games"
                        ApplicationInfo.CATEGORY_AUDIO -> "Audio"
                        ApplicationInfo.CATEGORY_VIDEO -> "Video"
                        ApplicationInfo.CATEGORY_IMAGE -> "Images"
                        ApplicationInfo.CATEGORY_SOCIAL -> "Social"
                        ApplicationInfo.CATEGORY_NEWS -> "News"
                        ApplicationInfo.CATEGORY_MAPS -> "Maps"
                        ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
                        ApplicationInfo.CATEGORY_ACCESSIBILITY -> "Accessibility"
                        else -> null
                    },
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
                customIconUri = preference?.customIconUri,
            )
        }
    }.stateIn(repositoryScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
            contactSearch = it[CONTACT_SEARCH] ?: false,
            contextSuggestions = it[CONTEXT_SUGGESTIONS] ?: false,
            documentTree = it[DOCUMENT_TREE] ?: "",
            iconPack = it[ICON_PACK] ?: "",
            wallpaperEnabled = it[WALLPAPER_ENABLED] ?: false, wallpaperParallax = it[WALLPAPER_PARALLAX] ?: false,
            blurStrength = (it[BLUR_STRENGTH] ?: 20).coerceIn(0, 40),
            locationCategories = it[LOCATION_CATEGORIES] ?: false,
            homeAnchor = it[HOME_ANCHOR] ?: "", workAnchor = it[WORK_ANCHOR] ?: "",
        )
    }

    override suspend fun updateSettings(settings: LauncherSettings) {
        var clearHistory = false
        var clearLocations = false
        val locationEnabled = settings.contextSuggestions && settings.locationCategories
        context.launcherSettings.edit {
            clearHistory = it[CONTEXT_SUGGESTIONS] == true && !settings.contextSuggestions
            clearLocations = it[LOCATION_CATEGORIES] == true && !locationEnabled
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
            it[SWIPE_UP] = settings.swipeUp.takeIf { v -> validGesture(v) } ?: "none"
            it[SWIPE_DOWN] = settings.swipeDown.takeIf { v -> validGesture(v) } ?: "none"
            it[SWIPE_LEFT] = settings.swipeLeft.takeIf { v -> validGesture(v) } ?: "none"
            it[SWIPE_RIGHT] = settings.swipeRight.takeIf { v -> validGesture(v) } ?: "none"
            it[DOUBLE_TAP] = settings.doubleTap.takeIf { v -> validGesture(v) } ?: "none"
            it[EMPTY_LONG_PRESS] = settings.emptyLongPress.takeIf { v -> validGesture(v) } ?: "none"
            it[TWO_FINGER] = settings.twoFinger.takeIf { v -> validGesture(v) } ?: "none"
            it[CONTACT_SEARCH] = settings.contactSearch
            it[CONTEXT_SUGGESTIONS] = settings.contextSuggestions
            it[DOCUMENT_TREE] = settings.documentTree
            it[ICON_PACK] = settings.iconPack.take(255)
            it[WALLPAPER_ENABLED] = settings.wallpaperEnabled
            it[WALLPAPER_PARALLAX] = settings.wallpaperParallax
            it[BLUR_STRENGTH] = settings.blurStrength.coerceIn(0, 40)
            it[LOCATION_CATEGORIES] = locationEnabled
            if (!locationEnabled) { it.remove(HOME_ANCHOR); it.remove(WORK_ANCHOR) }
        }
        if (clearHistory) usageDao.clear() else if (clearLocations) usageDao.clearLocations()
    }

    private val suggestionRefresh = MutableStateFlow(0L)
    override fun refreshSuggestions() { suggestionRefresh.value = System.currentTimeMillis() }

    private fun lastCoarseLocation(): Location? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
        val manager = context.getSystemService(LocationManager::class.java)
        return try {
            val now = android.os.SystemClock.elapsedRealtimeNanos()
            manager.getProviders(true).mapNotNull { provider ->
                try { manager.getLastKnownLocation(provider) } catch (_: SecurityException) { null } catch (_: IllegalArgumentException) { null }
            }.filter { (now - it.elapsedRealtimeNanos) in 0..21_600_000_000_000L }
                .maxByOrNull { it.elapsedRealtimeNanos }
        } catch (_: SecurityException) { null }
    }

    /** Stores only rounded coordinates when the user explicitly marks a place. */
    suspend fun setLocationAnchor(place: String): Boolean = withContext(Dispatchers.IO) {
        if (place !in setOf("home", "work") || !settings.first().locationCategories) return@withContext false
        val location = lastCoarseLocation() ?: return@withContext false
        val rounded = String.format(Locale.ROOT, "%.2f,%.2f", location.latitude, location.longitude)
        context.launcherSettings.edit { it[if (place == "home") HOME_ANCHOR else WORK_ANCHOR] = rounded }
        refreshSuggestions()
        true
    }

    private fun locationCategory(prefs: LauncherSettings): String? {
        if (!prefs.locationCategories) return null
        val location = lastCoarseLocation() ?: return null
        fun distance(anchor: String): Float? {
            val parts = anchor.split(',')
            if (parts.size != 2) return null
            val lat = parts[0].toDoubleOrNull() ?: return null
            val lon = parts[1].toDoubleOrNull() ?: return null
            val result = FloatArray(1)
            Location.distanceBetween(location.latitude, location.longitude, lat, lon, result)
            return result[0]
        }
        val home = distance(prefs.homeAnchor)
        val work = distance(prefs.workAnchor)
        return when {
            home == null && work == null -> null
            home != null && home < 2000 && (work == null || home <= work) -> "home"
            work != null && work < 2000 -> "work"
            else -> "other"
        }
    }

    private fun currentContext(prefs: LauncherSettings): CurrentContext {
        val now = Calendar.getInstance()
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val audio = context.getSystemService(AudioManager::class.java)
        val headphones = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type in setOf(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_USB_HEADSET)
        }
        return CurrentContext(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.DAY_OF_WEEK), charging, headphones, locationCategory(prefs))
    }

    private fun systemUsage(apps: List<LauncherApp>): List<UsageContext> {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        @Suppress("DEPRECATION")
        val allowed = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
        if (!allowed) return emptyList()
        return try {
            val now = System.currentTimeMillis()
            val events = context.getSystemService(UsageStatsManager::class.java).queryEvents(now - 7 * 86_400_000L, now)
            val event = UsageEvents.Event()
            val byPackage = apps.filterNot { it.isWorkProfile }.groupBy { it.packageName }
            val result = mutableListOf<UsageContext>()
            val calendar = Calendar.getInstance()
            var scanned = 0
            @Suppress("DEPRECATION")
            while (events.hasNextEvent() && scanned++ < 30_000) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    val app = byPackage[event.packageName]?.firstOrNull() ?: continue
                    calendar.timeInMillis = event.timeStamp
                    result.add(UsageContext(app.key, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.DAY_OF_WEEK), null, null, event.timeStamp))
                }
            }
            result
        } catch (_: SecurityException) { emptyList() }
    }

    override val suggestedApps: Flow<List<LauncherApp>> = combine(apps, usageDao.observeRecent(), settings.map { Triple(it.contextSuggestions, it.locationCategories, it.homeAnchor to it.workAnchor) }.distinctUntilChanged(), suggestionRefresh) { appList, entries, selected, _ ->
        Triple(appList, entries, selected)
    }.map { (appList, entries, selected) ->
        if (!selected.first) emptyList() else {
            val local = entries.map { UsageContext(it.appKey, it.hour, it.weekday, it.charging, it.headphones, it.timestamp, it.locationCategory) }
            rankContextApps(appList, local + systemUsage(appList), currentContext(settings.first()), System.currentTimeMillis()).take(6)
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun update(app: LauncherApp, change: (AppPreference) -> AppPreference) {
        updateLock.withLock { dao.put(change(dao.find(app.key) ?: AppPreference(app.key))) }
    }

    override suspend fun setPinned(app: LauncherApp, pinned: Boolean) = update(app) { it.copy(pinned = pinned) }
    override suspend fun setHidden(app: LauncherApp, hidden: Boolean) = update(app) { it.copy(hidden = hidden) }
    override suspend fun setLabel(app: LauncherApp, label: String?) = update(app) { it.copy(customLabel = label?.trim()?.takeIf(String::isNotEmpty)?.take(100)) }
    override suspend fun setSearchOnly(app: LauncherApp, searchOnly: Boolean) = update(app) { it.copy(searchOnly = searchOnly) }
    override suspend fun setGroup(app: LauncherApp, group: String?) = update(app) { it.copy(groupName = group?.trim()?.takeIf(String::isNotEmpty)?.take(60)) }
    override suspend fun setCustomIcon(app: LauncherApp, uri: String?) = update(app) { it.copy(customIconUri = uri?.take(4096)) }

    override suspend fun launch(app: LauncherApp): Boolean {
        val component = ComponentName.unflattenFromString(app.componentName) ?: return false
        val profile = user(app.userSerial) ?: return false
        return try {
            launcher.startMainActivity(component, profile, null, null)
            val now = System.currentTimeMillis()
            update(app) { it.copy(launches = it.launches + 1, lastLaunched = now) }
            val prefs = settings.first()
            if (prefs.contextSuggestions) {
                val signal = withContext(Dispatchers.IO) { currentContext(prefs) }
                usageDao.insert(UsageContextEntity(appKey = app.key, hour = signal.hour, weekday = signal.weekday, charging = signal.charging, headphones = signal.headphones, timestamp = now, locationCategory = signal.locationCategory))
                usageDao.prune()
            }
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

    suspend fun searchShortcuts(query: String, apps: List<LauncherApp>): List<SearchShortcut> = withContext(Dispatchers.IO) {
        val text = query.trim()
        if (text.length < 2 || !launcher.hasShortcutHostPermission()) return@withContext emptyList()
        val byProfilePackage = apps.filterNot { it.hidden }.groupBy { it.userSerial to it.packageName }
        val matches = mutableListOf<SearchShortcut>()
        users.userProfiles.forEach { profile ->
            try {
                val serial = users.getSerialNumberForUser(profile)
                val queryFlags = LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                val request = LauncherApps.ShortcutQuery().setQueryFlags(queryFlags)
                launcher.getShortcuts(request, profile).orEmpty().forEach { shortcut ->
                    val label = shortcut.shortLabel?.toString() ?: return@forEach
                    val app = byProfilePackage[serial to shortcut.`package`]?.firstOrNull() ?: return@forEach
                    if (label.contains(text, ignoreCase = true) && matches.size < 20) matches.add(SearchShortcut(app, shortcut.id, label))
                }
            } catch (_: SecurityException) { } catch (_: IllegalStateException) { }
        }
        matches.sortedBy { it.label.lowercase() }
    }

    fun launchShortcut(app: LauncherApp, id: String): Boolean {
        val profile = user(app.userSerial) ?: return false
        return try {
            launcher.startShortcut(app.packageName, id, null, null, profile)
            true
        } catch (_: SecurityException) { false } catch (_: android.content.ActivityNotFoundException) { false }
    }

    suspend fun installedIconPacks() = withContext(Dispatchers.IO) { iconPacks.installed() }

    suspend fun icon(app: LauncherApp, pixels: Int, settings: LauncherSettings): Bitmap? = withContext(Dispatchers.IO) {
        @Suppress("DEPRECATION")
        val updatedAt = try { context.packageManager.getPackageInfo(app.packageName, 0).lastUpdateTime } catch (_: android.content.pm.PackageManager.NameNotFoundException) { 0L }
        val night = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val dynamicAccent = if (settings.dynamicColor && Build.VERSION.SDK_INT >= 31) {
            context.getColor(if (settings.themeMode == "light" || (settings.themeMode == "system" && night != android.content.res.Configuration.UI_MODE_NIGHT_YES)) android.R.color.system_accent1_700 else android.R.color.system_accent1_200)
        } else 0
        @Suppress("DEPRECATION")
        val packUpdatedAt = if (settings.iconPack.isBlank()) 0L else try { context.packageManager.getPackageInfo(settings.iconPack, 0).lastUpdateTime } catch (_: android.content.pm.PackageManager.NameNotFoundException) { 0L }
        val cacheKey = "${app.customIconUri}:${settings.iconPack}:$packUpdatedAt:${app.key}:$pixels:${settings.themedIcons}:${settings.palette}:${settings.dynamicColor}:${settings.themeMode}:$night:$dynamicAccent:$updatedAt"
        iconCache.get(cacheKey) ?: (if (updatedAt != 0L && app.customIconUri == null) diskCache.read(cacheKey, pixels) else null)?.also { iconCache.put(cacheKey, it) } ?: try {
            val profile = user(app.userSerial) ?: return@withContext null
            val info = launcher.getActivityList(app.packageName, profile).firstOrNull {
                it.componentName.flattenToString() == app.componentName
            } ?: return@withContext null
            val custom = app.customIconUri?.let { loadCustomIcon(it, pixels) }
            if (custom != null) {
                val result = if (app.isWorkProfile) {
                    val drawable = android.graphics.drawable.BitmapDrawable(context.resources, custom)
                    context.packageManager.getUserBadgedIcon(drawable, profile).toBitmap(pixels, pixels, Bitmap.Config.ARGB_8888)
                } else custom
                iconCache.put(cacheKey, result)
                return@withContext result
            }
            val packed = if (settings.iconPack.isNotBlank()) iconPacks.icon(settings.iconPack, app.componentName) else null
            val badgedPacked = if (packed != null && app.isWorkProfile) context.packageManager.getUserBadgedIcon(packed, profile) else packed
            val adaptive = if (badgedPacked == null && Build.VERSION.SDK_INT >= 33 && settings.themedIcons && !app.isWorkProfile) info.getIcon(0) as? AdaptiveIconDrawable else null
            val monochrome = if (Build.VERSION.SDK_INT >= 33) adaptive?.monochrome else null
            val bitmap = if (badgedPacked != null) badgedPacked.toBitmap(pixels, pixels, Bitmap.Config.ARGB_8888) else if (monochrome != null) {
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
            bitmap.also { iconCache.put(cacheKey, it); if (updatedAt != 0L && app.customIconUri == null) diskCache.write(cacheKey, it) }
        } catch (_: SecurityException) { null }
    }

    private fun loadCustomIcon(value: String, pixels: Int): Bitmap? {
      return try {
        val uri = Uri.parse(value)
        if (uri.scheme != "content") return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val input = context.contentResolver.openInputStream(uri) ?: return null
        input.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth !in 1..16384 || bounds.outHeight !in 1..16384) return null
        var sample = 1
        while (bounds.outWidth / sample > pixels * 2 || bounds.outHeight / sample > pixels * 2) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        Bitmap.createScaledBitmap(bitmap, pixels, pixels, true)
      } catch (_: Exception) { null }
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
            .addMigrations(*LauncherMigrations.all).build()
    @Provides fun preferences(database: LauncherDatabase): AppPreferenceDao = database.preferences()
    @Provides fun widgets(database: LauncherDatabase): WidgetDao = database.widgets()
    @Provides fun usageContext(database: LauncherDatabase): UsageContextDao = database.usageContext()
}
