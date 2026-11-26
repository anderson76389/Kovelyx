package com.kovelyx.tv.ui.screens.detail

import android.view.KeyEvent
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kovelyx.tv.R
import com.kovelyx.tv.domain.model.EpisodeRating
import com.kovelyx.tv.domain.model.Video
import com.kovelyx.tv.ui.theme.KovelyxTheme
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WholeSeriesRatingsTvTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun visibleWholeSeriesButtonRespondsToRemoteAndShowsWatchedState() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val mark = context.getString(R.string.hero_mark_all_watched_short)
        val done = context.getString(R.string.hero_all_watched_short)
        var watched by mutableStateOf(false)
        var clicks = 0
        compose.setContent {
            KovelyxTheme { WholeSeriesWatchedButton(watched, false, { clicks++; watched = !watched }) }
        }
        compose.onNodeWithText(mark).assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithText(mark).assertIsFocused()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.onNodeWithText(done).assertIsDisplayed().assertIsFocused()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.onNodeWithText(mark).assertIsDisplayed()
        compose.runOnIdle { assertEquals(2, clicks) }
    }

    @Test
    fun partialRatingsAreVisibleDuringLoadAndSourceIsAccurate() {
        val episodes = (1..2).map {
            Video("tt1234567:1:$it", "Episode $it", "2020-01-01", null,
                season = 1, episode = it, overview = null)
        }
        compose.setContent {
            KovelyxTheme {
                Column {
                    EpisodeRatingsSection(episodes, mapOf(
                        (1 to 1) to EpisodeRating(8.7, "IMDb"),
                        (1 to 2) to EpisodeRating(7.2, "TMDB")
                    ), isLoading = true, error = null)
                }
            }
        }
        compose.onNodeWithText("IMDb " + String.format(Locale.getDefault(), "%.1f", 8.7)).assertIsDisplayed()
        compose.onNodeWithText("TMDB " + String.format(Locale.getDefault(), "%.1f", 7.2)).assertIsDisplayed()
    }
}
