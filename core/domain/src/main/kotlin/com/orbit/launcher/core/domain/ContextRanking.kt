package com.orbit.launcher.core.domain

import kotlin.math.abs

data class UsageContext(val appKey: String, val hour: Int, val weekday: Int, val charging: Boolean?, val headphones: Boolean?, val timestamp: Long)
data class CurrentContext(val hour: Int, val weekday: Int, val charging: Boolean, val headphones: Boolean)

/** Scores only on-device signals. A missing device-state signal contributes no weight. */
fun rankContextApps(apps: List<LauncherApp>, events: List<UsageContext>, context: CurrentContext, now: Long): List<LauncherApp> {
    val scores = mutableMapOf<String, Double>()
    events.forEach { event ->
        val delta = abs(event.hour - context.hour).coerceAtMost(24)
        val hourDistance = minOf(delta, 24 - delta)
        val ageDays = ((now - event.timestamp).coerceAtLeast(0L) / 86_400_000.0)
        var weight = 1.0
        if (hourDistance <= 2) weight += 2.0
        if (event.weekday == context.weekday) weight += 0.5
        if (event.charging != null && event.charging == context.charging) weight += 0.35
        if (event.headphones != null && event.headphones == context.headphones) weight += 0.35
        scores[event.appKey] = (scores[event.appKey] ?: 0.0) + weight / (1.0 + ageDays / 7.0)
    }
    return apps.filter { !it.hidden && !it.searchOnly && (scores[it.key] ?: 0.0) > 0.0 }
        .sortedWith(compareByDescending<LauncherApp> { scores[it.key] ?: 0.0 }.thenBy { it.label.lowercase() })
}
