package com.orbit.launcher.core.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.util.Xml
import com.orbit.launcher.core.domain.IconPack
import dagger.hilt.android.qualifiers.ApplicationContext
import org.xmlpull.v1.XmlPullParser
import javax.inject.Inject
import javax.inject.Singleton

/** Reads the common ADW/Nova appfilter format. Pack resources are never executed. */
@Singleton
class IconPackManager @Inject constructor(@ApplicationContext private val context: Context) {
    private val pm = context.packageManager
    private var cachedKey = ""
    private var cachedIcons: Map<String, String> = emptyMap()

    @Suppress("DEPRECATION")
    fun installed(): List<IconPack> {
        val actions = listOf("org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.gau.go.launcherex.theme")
        return actions.flatMap { action -> pm.queryIntentActivities(Intent(action), 0) }
            .distinctBy { it.activityInfo.packageName }
            .map { IconPack(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .sortedBy { it.label.lowercase() }
    }

    @Synchronized
    @Suppress("DEPRECATION")
    fun icon(pack: String, component: String): Drawable? {
        return try {
            val version = pm.getPackageInfo(pack, 0).lastUpdateTime
            val key = "$pack:$version"
            val resources = pm.getResourcesForApplication(pack)
            if (cachedKey != key) {
                val xmlId = resources.getIdentifier("appfilter", "xml", pack)
                val parser = if (xmlId != 0) resources.getXml(xmlId) else Xml.newPullParser().apply { setInput(resources.assets.open("appfilter.xml"), "UTF-8") }
                val icons = mutableMapOf<String, String>()
                try {
                    var event = parser.eventType
                    while (event != XmlPullParser.END_DOCUMENT) {
                        if (event == XmlPullParser.START_TAG && parser.name == "item") {
                            val raw = parser.getAttributeValue(null, "component")?.removePrefix("ComponentInfo{")?.removeSuffix("}")
                            val name = parser.getAttributeValue(null, "drawable")
                            val normalized = raw?.let { ComponentName.unflattenFromString(it)?.flattenToString() }
                            if (normalized != null && !name.isNullOrBlank()) icons[normalized] = name
                        }
                        event = parser.next()
                    }
                } finally { (parser as? AutoCloseable)?.close() }
                cachedKey = key
                cachedIcons = icons
            }
            val name = cachedIcons[component] ?: return null
            val drawableId = resources.getIdentifier(name, "drawable", pack)
            if (drawableId == 0) null else resources.getDrawable(drawableId, null)
        } catch (_: Exception) { null }
    }
}
