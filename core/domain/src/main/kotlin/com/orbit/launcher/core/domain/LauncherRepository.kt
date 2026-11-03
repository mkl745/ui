package com.orbit.launcher.core.domain

import kotlinx.coroutines.flow.Flow

data class LauncherApp(
    val key: String,
    val packageName: String,
    val componentName: String,
    val userSerial: Long,
    val label: String,
    val isWorkProfile: Boolean,
    val pinned: Boolean = false,
    val hidden: Boolean = false,
    val launches: Int = 0,
    val lastLaunched: Long = 0,
    val searchOnly: Boolean = false,
    val groupName: String? = null,
)

data class ContactResult(val id: Long, val lookupKey: String, val label: String)

data class LauncherSettings(
    val showLabels: Boolean = true,
    val reduceMotion: Boolean = false,
    val iconSize: Int = 48,
    val webSearch: Boolean = false,
    val lockHidden: Boolean = false,
    val themeMode: String = "dark",
    val dynamicColor: Boolean = false,
    val palette: String = "violet",
    val font: String = "sans",
    val cornerRadius: Int = 20,
    val iconShape: String = "circle",
    val themedIcons: Boolean = false,
    val swipeUp: String = "search",
    val swipeDown: String = "notifications",
    val swipeLeft: String = "none",
    val swipeRight: String = "none",
    val doubleTap: String = "search",
    val emptyLongPress: String = "settings",
    val twoFinger: String = "none",
    val contactSearch: Boolean = false,
    val contextSuggestions: Boolean = false,
)

interface LauncherRepository {
    val apps: Flow<List<LauncherApp>>
    val settings: Flow<LauncherSettings>
    val suggestedApps: Flow<List<LauncherApp>>
    fun refreshSuggestions()
    suspend fun launch(app: LauncherApp): Boolean
    suspend fun setPinned(app: LauncherApp, pinned: Boolean)
    suspend fun setHidden(app: LauncherApp, hidden: Boolean)
    suspend fun setLabel(app: LauncherApp, label: String?)
    suspend fun setSearchOnly(app: LauncherApp, searchOnly: Boolean)
    suspend fun setGroup(app: LauncherApp, group: String?)
    suspend fun updateSettings(settings: LauncherSettings)
}

/** Stable, locale-independent matching with a subsequence fallback. */
fun searchApps(apps: List<LauncherApp>, query: String): List<LauncherApp> {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return apps.sortedBy { it.label.lowercase() }
    fun score(label: String): Int {
        val text = label.lowercase()
        if (text == needle) return 0
        if (text.startsWith(needle)) return 1
        if (text.contains(needle)) return 2
        var i = 0
        for (c in text) if (i < needle.length && c == needle[i]) i++
        return if (i == needle.length) 3 else 4
    }
    return apps.asSequence().map { it to score(it.label) }.filter { it.second < 4 }
        .sortedWith(compareBy<Pair<LauncherApp, Int>> { it.second }.thenByDescending { it.first.launches }.thenBy { it.first.label.lowercase() })
        .map { it.first }.toList()
}
