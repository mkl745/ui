package com.orbit.launcher.core.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ContextRankingTest {
    private fun app(key: String) = LauncherApp(key, key, "$key/.Main", 0, key, false)
    @Test fun matchingHourAndSignalsImproveRank() {
        val now = 1_000_000_000L
        val results = rankContextApps(listOf(app("a"), app("b")), listOf(
            UsageContext("a", 3, 2, false, false, now),
            UsageContext("b", 9, 4, true, true, now),
        ), CurrentContext(9, 4, true, true), now)
        assertEquals(listOf("b", "a"), results.map { it.key })
    }
    @Test fun optedInLocationCategoryImprovesRank() {
        val now = 1_000_000L
        val results = rankContextApps(listOf(app("a"), app("b")), listOf(
            UsageContext("a", 9, 1, null, null, now, "work"),
            UsageContext("b", 9, 1, null, null, now, "home"),
        ), CurrentContext(9, 1, false, false, "home"), now)
        assertEquals(listOf("b", "a"), results.map { it.key })
    }
    @Test fun hiddenAppsNeverSuggested() {
        val hidden = app("a").copy(hidden = true)
        assertEquals(emptyList<LauncherApp>(), rankContextApps(listOf(hidden), listOf(UsageContext("a", 9, 1, null, null, 0)), CurrentContext(9, 1, false, false), 0))
    }
}
