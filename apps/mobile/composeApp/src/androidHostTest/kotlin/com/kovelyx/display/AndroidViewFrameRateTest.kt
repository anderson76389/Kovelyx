package com.kovelyx.display

import android.widget.FrameLayout
import android.view.View
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class AndroidViewFrameRateTest {
    @Test fun votesReachNestedRenderingViewsAndFollowBothSelections() {
        val context = RuntimeEnvironment.getApplication()
        val root = FrameLayout(context)
        val child = FrameLayout(context)
        val renderer = View(context)
        root.addView(child)
        child.addView(renderer)
        for (rate in listOf(120f, 60f, 0f)) {
            applyAndroidViewFrameRate(root, rate)
            assertEquals(rate, root.requestedFrameRate, 0f)
            assertEquals(rate, child.requestedFrameRate, 0f)
            assertEquals(rate, renderer.requestedFrameRate, 0f)
        }
    }
}
