package com.orbit.launcher.core.data

import android.content.Context
import android.net.Uri
import com.orbit.launcher.core.domain.LauncherRepository
import com.orbit.launcher.core.domain.LauncherSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Exports only Orbit-owned data, never icons or notification contents. */
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: AppPreferenceDao,
    private val repository: LauncherRepository,
) {
    suspend fun export(uri: Uri) = withContext(Dispatchers.IO) {
        val settings = repository.settings.first()
        val entries = JSONArray()
        dao.all().forEach { item ->
            entries.put(JSONObject().put("key", item.key).put("pinned", item.pinned).put("hidden", item.hidden)
                .put("label", item.customLabel ?: JSONObject.NULL).put("launches", item.launches).put("lastLaunched", item.lastLaunched)
                .put("searchOnly", item.searchOnly).put("group", item.groupName ?: JSONObject.NULL))
        }
        val json = JSONObject().put("version", 1).put("apps", entries).put("settings", JSONObject()
            .put("showLabels", settings.showLabels).put("reduceMotion", settings.reduceMotion).put("iconSize", settings.iconSize).put("webSearch", settings.webSearch)
            .put("themeMode", settings.themeMode).put("dynamicColor", settings.dynamicColor).put("palette", settings.palette)
            .put("font", settings.font).put("cornerRadius", settings.cornerRadius).put("iconShape", settings.iconShape).put("themedIcons", settings.themedIcons))
        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: error("Cannot open backup")
        stream.bufferedWriter(Charsets.UTF_8).use { it.write(json.toString()) }
    }

    suspend fun restore(uri: Uri) = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(uri) ?: error("Cannot open backup")
        val bytes = stream.use { input -> input.readBytesLimited(1_048_576) }
        val json = JSONObject(bytes.toString(Charsets.UTF_8))
        require(json.getInt("version") == 1) { "Unsupported backup version" }
        val settingsJson = json.getJSONObject("settings")
        val iconSize = settingsJson.getInt("iconSize")
        require(iconSize in 32..72)
        val themeMode = settingsJson.optString("themeMode", "dark")
        val palette = settingsJson.optString("palette", "violet")
        val font = settingsJson.optString("font", "sans")
        val radius = settingsJson.optInt("cornerRadius", 20)
        val iconShape = settingsJson.optString("iconShape", "circle")
        require(themeMode in setOf("system", "dark", "light", "amoled") && palette in setOf("violet", "cyan", "amber") && font in setOf("sans", "serif", "mono") && radius in 0..32 && iconShape in setOf("circle", "squircle", "teardrop", "square"))
        val settings = LauncherSettings(settingsJson.getBoolean("showLabels"), settingsJson.getBoolean("reduceMotion"), iconSize, settingsJson.optBoolean("webSearch", false), repository.settings.first().lockHidden, themeMode, settingsJson.optBoolean("dynamicColor", false), palette, font, radius, iconShape, settingsJson.optBoolean("themedIcons", false))
        val array = json.getJSONArray("apps")
        require(array.length() <= 10_000)
        val entries = (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val key = item.getString("key")
            require(key.length in 1..1024)
            val label = if (item.isNull("label")) null else item.getString("label")
            require(label == null || label.length <= 1024)
            val launches = item.getInt("launches")
            val last = item.getLong("lastLaunched")
            require(launches >= 0 && last >= 0)
            val group = if (!item.has("group") || item.isNull("group")) null else item.getString("group")
            require(group == null || group.length <= 60)
            AppPreference(key, item.getBoolean("pinned"), item.getBoolean("hidden"), label, launches, last, item.optBoolean("searchOnly", false), group)
        }
        require(entries.map { it.key }.distinct().size == entries.size)
        dao.replaceAll(entries)
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
