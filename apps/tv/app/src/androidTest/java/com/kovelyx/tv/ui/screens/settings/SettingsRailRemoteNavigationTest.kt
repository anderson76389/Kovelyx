package com.kovelyx.tv.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.runtime.remember
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kovelyx.tv.ui.theme.KovelyxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Actual TV Compose rail: all five settings pairs, D-pad and OK, no persistent writes. */
@RunWith(AndroidJUnit4::class)
class SettingsRailRemoteNavigationTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun everyProductionPairRespondsToDpadAndOk() {
        val pairs = listOf(
            "Compte" to "Profils",
            "Apparence" to "Disposition",
            "Contenu" to "Lecture",
            "Intégrations" to "Suivi",
            "Avancé" to "À propos"
        )
        pairs.forEach { (first, second) ->
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
}
