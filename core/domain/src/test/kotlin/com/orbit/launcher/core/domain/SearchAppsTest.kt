package com.orbit.launcher.core.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchAppsTest {
    private fun app(label: String, launches: Int = 0) = LauncherApp(label, "pkg", "pkg/.Main", 0, label, false, launches = launches)
    @Test fun exactAndPrefixRankBeforeFuzzy() {
        val result = searchApps(listOf(app("Camera"), app("Calendar"), app("Cal")), "cal")
        assertEquals(listOf("Cal", "Calendar"), result.map { it.label })
    }
    @Test fun subsequenceMatchesAndFrequencyBreaksTies() {
        val result = searchApps(listOf(app("Calculator"), app("Calendar", 3)), "cl")
        assertEquals(listOf("Calendar", "Calculator"), result.map { it.label })
    }
}
