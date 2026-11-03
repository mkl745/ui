package com.orbit.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.orbit.launcher.core.domain.LauncherApp
import com.orbit.launcher.core.domain.LauncherSettings
import com.orbit.launcher.core.ui.OrbitTheme
import com.orbit.launcher.feature.home.LauncherFoundationScreen
import com.orbit.launcher.feature.home.OrbitHome
import com.orbit.launcher.feature.search.SearchScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LauncherUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun setupCanBeSkipped() {
        var skipped by mutableStateOf(false)
        compose.setContent {
            OrbitTheme {
                if (skipped) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Text("Launcher ready")
                }
                else LauncherFoundationScreen(false, 0, onChooseDefault = {}, onContinue = { skipped = true })
            }
        }
        compose.onNodeWithText("Continue without changing").performClick()
        compose.runOnIdle { assertTrue(skipped) }
        compose.onNodeWithText("Launcher ready").assertExists()
    }

    @Test fun hubOpensSearchAndFindsApp() {
        val apps = listOf(LauncherApp("0:camera", "camera", "camera/.Main", 0, "Camera", false))
        var search by mutableStateOf(false)
        val settings = LauncherSettings(reduceMotion = true)
        compose.setContent {
            OrbitTheme(settings) {
                if (!search) OrbitHome(apps, emptyList(), settings, emptyMap(), { _, _ -> null }, {}, {}, { search = true }, {}, {}, {}, {})
                else SearchScreen(apps, settings, "", false, {}, { emptyList() }, {}, { emptyList() }, {}, { emptyList() }, {}, { _, _ -> null }, {}, { search = false })
            }
        }
        compose.onNodeWithContentDescription("Search apps").performClick()
        compose.onNodeWithText("Search apps").performTextInput("cam")
        compose.onNodeWithText("Camera").assertIsDisplayed()
    }

    @Test fun calculatorResultAppearsInSearch() {
        val settings = LauncherSettings(reduceMotion = true)
        compose.setContent {
            OrbitTheme(settings) {
                SearchScreen(emptyList(), settings, "", false, {}, { emptyList() }, {}, { emptyList() }, {}, { emptyList() }, {}, { _, _ -> null }, {}, {})
            }
        }
        compose.onNodeWithText("Search apps").performTextInput("2+3")
        compose.onNodeWithText("5  ·  Copy result").assertIsDisplayed()
    }
}
