package com.orbit.launcher.core.data

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Process
import android.os.UserManager
import com.orbit.launcher.core.domain.LauncherRepository
import com.orbit.launcher.core.domain.LauncherSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Explicit user-initiated export; never includes notifications or precise location. */
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: AppPreferenceDao,
    private val repository: LauncherRepository,
    private val widgetDao: WidgetDao,
    private val portableIcons: PortableIconStore,
) {
    suspend fun export(uri: Uri) = withContext(Dispatchers.IO) {
        val settings = repository.settings.first()
        val entries = JSONArray()
        var encodedIconBytes = 0L
        dao.all().forEach { item ->
            val entry = JSONObject().put("key", item.key).put("pinned", item.pinned).put("hidden", item.hidden)
                .put("label", item.customLabel ?: JSONObject.NULL).put("launches", item.launches).put("lastLaunched", item.lastLaunched)
                .put("searchOnly", item.searchOnly).put("group", item.groupName ?: JSONObject.NULL)
            item.customIconUri?.let {
                val encoded = portableIcons.snapshot(it)
                encodedIconBytes += encoded.length
                require(encodedIconBytes <= 24L * 1024 * 1024) { "Icon backups are too large" }
                entry.put("customIconPng", encoded)
            }
            entries.put(entry)
        }
        val manager = AppWidgetManager.getInstance(context)
        val widgetEntries = JSONArray()
        widgetDao.all().forEach { widget ->
            val provider = widget.providerName ?: manager.getAppWidgetInfo(widget.widgetId)?.provider?.flattenToString()
            if (provider != null) widgetEntries.put(JSONObject().put("provider", provider).put("heightDp", widget.heightDp).put("widthDp", widget.widthDp))
        }
        val personalSerial = context.getSystemService(UserManager::class.java).getSerialNumberForUser(Process.myUserHandle())
        val json = JSONObject().put("widgets", widgetEntries).put("version", 2).put("personalSerial", personalSerial).put("apps", entries).put("settings", JSONObject()
            .put("showLabels", settings.showLabels).put("reduceMotion", settings.reduceMotion).put("iconSize", settings.iconSize).put("webSearch", settings.webSearch)
            .put("themeMode", settings.themeMode).put("dynamicColor", settings.dynamicColor).put("palette", settings.palette)
            .put("font", settings.font).put("cornerRadius", settings.cornerRadius).put("iconShape", settings.iconShape).put("themedIcons", settings.themedIcons)
            .put("swipeUp", settings.swipeUp).put("swipeDown", settings.swipeDown).put("swipeLeft", settings.swipeLeft).put("swipeRight", settings.swipeRight)
            .put("doubleTap", settings.doubleTap).put("emptyLongPress", settings.emptyLongPress).put("twoFinger", settings.twoFinger).put("iconPack", settings.iconPack)
            .put("wallpaperEnabled", settings.wallpaperEnabled).put("wallpaperParallax", settings.wallpaperParallax).put("blurStrength", settings.blurStrength))
        val payload = json.toString().toByteArray(Charsets.UTF_8)
        require(payload.size <= 32 * 1024 * 1024) { "Backup is too large" }
        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: error("Cannot open backup")
        stream.use { it.write(payload) }
    }

    suspend fun restore(uri: Uri) = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(uri) ?: error("Cannot open backup")
        val bytes = stream.use { input -> input.readBytesLimited(32 * 1024 * 1024) }
        val json = JSONObject(bytes.toString(Charsets.UTF_8))
        require(json.getInt("version") in 1..2) { "Unsupported backup version" }
        val oldPersonalSerial = json.optLong("personalSerial", -1)
        val currentSerial = context.getSystemService(UserManager::class.java).getSerialNumberForUser(Process.myUserHandle())
        fun remapKey(key: String): String {
            val prefix = "$oldPersonalSerial:"
            return if (oldPersonalSerial >= 0 && currentSerial >= 0 && key.startsWith(prefix)) "$currentSerial:${key.removePrefix(prefix)}" else key
        }
        val settingsJson = json.getJSONObject("settings")
        val iconSize = settingsJson.getInt("iconSize")
        require(iconSize in 32..72)
        val themeMode = settingsJson.optString("themeMode", "dark")
        val palette = settingsJson.optString("palette", "violet")
        val font = settingsJson.optString("font", "sans")
        val radius = settingsJson.optInt("cornerRadius", 20)
        val iconShape = settingsJson.optString("iconShape", "circle")
        require(themeMode in setOf("system", "dark", "light", "amoled") && palette in setOf("violet", "cyan", "amber") && font in setOf("sans", "serif", "mono") && radius in 0..32 && iconShape in setOf("circle", "squircle", "teardrop", "square"))
        val allowedActions = setOf("none", "search", "settings", "widgets", "groups", "notifications", "quick_settings", "lock", "torch")
        fun action(name: String, default: String): String {
            val value = settingsJson.optString(name, default)
            require(value in allowedActions || (value.startsWith("app:") && value.length in 5..1028))
            return if (value.startsWith("app:")) "app:${remapKey(value.removePrefix("app:"))}" else value
        }
        val settings = LauncherSettings(settingsJson.getBoolean("showLabels"), settingsJson.getBoolean("reduceMotion"), iconSize, settingsJson.optBoolean("webSearch", false), repository.settings.first().lockHidden, themeMode, settingsJson.optBoolean("dynamicColor", false), palette, font, radius, iconShape, settingsJson.optBoolean("themedIcons", false), action("swipeUp", "search"), action("swipeDown", "notifications"), action("swipeLeft", "none"), action("swipeRight", "none"), action("doubleTap", "search"), action("emptyLongPress", "settings"), action("twoFinger", "none"), repository.settings.first().contactSearch, repository.settings.first().contextSuggestions, repository.settings.first().documentTree, settingsJson.optString("iconPack", "").also { require(it.length <= 255) }, settingsJson.optBoolean("wallpaperEnabled", false), settingsJson.optBoolean("wallpaperParallax", false), settingsJson.optInt("blurStrength", 20).also { require(it in 0..40) }, repository.settings.first().locationCategories, repository.settings.first().homeAnchor, repository.settings.first().workAnchor)
        val array = json.getJSONArray("apps")
        require(array.length() <= 10_000)
        val iconSnapshots = mutableMapOf<String, ByteArray>()
        val entries = (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val key = remapKey(item.getString("key"))
            require(key.length in 1..1024)
            val label = if (item.isNull("label")) null else item.getString("label")
            require(label == null || label.length <= 1024)
            val launches = item.getInt("launches")
            val last = item.getLong("lastLaunched")
            require(launches >= 0 && last >= 0)
            val group = if (!item.has("group") || item.isNull("group")) null else item.getString("group")
            require(group == null || group.length <= 60)
            val iconUri = if (!item.has("customIconUri") || item.isNull("customIconUri")) null else item.getString("customIconUri")
            require(iconUri == null || (iconUri.length <= 4096 && Uri.parse(iconUri).scheme == "content"))
            if (item.has("customIconPng") && !item.isNull("customIconPng")) iconSnapshots[key] = portableIcons.validate(item.getString("customIconPng"))
            AppPreference(key, item.getBoolean("pinned"), item.getBoolean("hidden"), label, launches, last, item.optBoolean("searchOnly", false), group, iconUri)
        }
        require(entries.map { it.key }.distinct().size == entries.size)
        val widgetSpecs = if (json.has("widgets")) {
            val list = json.getJSONArray("widgets")
            require(list.length() <= 100)
            (0 until list.length()).map { index ->
                val item = list.getJSONObject(index)
                val provider = item.getString("provider")
                val height = item.getInt("heightDp")
                val width = item.optInt("widthDp", 0)
                require(provider.length <= 1024 && ComponentName.unflattenFromString(provider) != null && height in 100..600 && (width == 0 || width in 100..1600))
                Triple(provider, height, width)
            }
        } else null
        val oldWidgets = if (widgetSpecs != null) widgetDao.all() else emptyList()
        val manager = AppWidgetManager.getInstance(context)
        val remaining = oldWidgets.toMutableList()
        val restoredWidgets = widgetSpecs?.mapIndexed { index, (provider, height, width) ->
            val reusable = remaining.firstOrNull { item ->
                item.widgetId >= 0 && manager.getAppWidgetInfo(item.widgetId)?.provider?.flattenToString() == provider
            }
            if (reusable != null) { remaining.remove(reusable); WidgetPlacement(reusable.widgetId, height, provider, width) }
            else WidgetPlacement(-2 - index, height, provider, width)
        }
        val restoredEntries = entries.map { entry ->
            val icon = iconSnapshots[entry.key]
            if (icon != null) entry.copy(customIconUri = portableIcons.save(icon)) else entry
        }
        dao.replaceAll(restoredEntries)
        if (restoredWidgets != null) {
            widgetDao.replaceAll(restoredWidgets)
            val host = AppWidgetHost(context, 1024)
            remaining.filter { it.widgetId >= 0 }.forEach { host.deleteAppWidgetId(it.widgetId) }
        }
        repository.updateSettings(settings)
    }
}

private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        require(output.size() + count <= limit) { "Backup is too large" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
