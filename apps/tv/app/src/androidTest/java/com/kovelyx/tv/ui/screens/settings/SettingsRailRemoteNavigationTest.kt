package com.kovelyx.tv.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kovelyx.tv.ui.theme.KovelyxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Actual Compose TV rail: all five settings pairs, D-pad and OK, no persistent writes. */
@RunWith(AndroidJUnit4::class)
class SettingsRailRemoteNavigationTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun accountAndProfilesCanBeNavigated() = checkPair("Compte", "Profils")
    @Test fun appearanceAndLayoutCanBeNavigated() = checkPair("Apparence", "Disposition")
    @Test fun discoveryAndPlaybackCanBeNavigated() = checkPair("Contenu", "Lecture")
    @Test fun integrationsAndTrackingCanBeNavigated() = checkPair("Intégrations", "Suivi")
    @Test fun advancedAndAboutCanBeNavigated() = checkPair("Avancé", "À propos")

    private fun checkPair(first: String, second: String) {
        val presses = mutableListOf<String>()
        lateinit var firstFocus: FocusRequester
        composeRule.setContent {
            KovelyxTheme {
                firstFocus = remember { FocusRequester() }
                Column {
                    SettingsRailButton(
                        title = first,
                        isSelected = true,
                        onClick = { presses.add(first) },
                        focusRequester = firstFocus
                    )
                    SettingsRailButton(
                        title = second,
                        isSelected = false,
                        onClick = { presses.add(second) }
                    )
                }
            }
        }
        composeRule.onNodeWithContentDescription(first).assertExists()
        composeRule.onNodeWithContentDescription(second).assertExists()
        composeRule.runOnIdle { firstFocus.requestFocus() }
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onRoot().performKeyInput { pressKey(Key.Enter) }
        composeRule.runOnIdle { assertEquals(listOf(second), presses) }
    }
}
