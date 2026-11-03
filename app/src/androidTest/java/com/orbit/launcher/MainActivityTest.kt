package com.orbit.launcher

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test

/** Exercises the actual activity, including the opt-out transition into the launcher. */
class MainActivityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun continueWithoutDefaultOpensHome() {
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("Continue without changing").fetchSemanticsNodes().isNotEmpty() ||
                compose.onNodeWithContentDescription("Search apps").isDisplayed()
        }
        if (!compose.onNodeWithContentDescription("Search apps").isDisplayed()) {
            compose.onNodeWithText("Continue without changing").performScrollTo().performClick()
        }
        compose.waitUntil(15_000) { compose.onNodeWithContentDescription("Search apps").isDisplayed() }
        compose.onNodeWithContentDescription("Search apps").assertIsDisplayed()
    }
}
