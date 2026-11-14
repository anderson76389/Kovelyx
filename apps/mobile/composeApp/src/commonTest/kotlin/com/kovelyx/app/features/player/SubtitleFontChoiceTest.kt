package com.kovelyx.app.features.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubtitleFontChoiceTest {
    @Test fun builtInSelectionsRemainStableAndLocal() {
        listOf(
            SubtitleFontChoice.DEFAULT,
            SubtitleFontChoice.SANS_SERIF,
            SubtitleFontChoice.SERIF,
            SubtitleFontChoice.MONOSPACE,
        ).forEach { assertTrue(SubtitleFontChoice.isValid(it)) }
        assertEquals("Par défaut", SubtitleFontChoice.label("default"))
    }

    @Test fun rejectsPathsUrlsAndMalformedCustomFontIds() {
        val valid = "custom:123e4567-e89b-12d3-a456-426614174000.ttf"
        assertTrue(SubtitleFontChoice.isValid(valid))
        assertEquals("123e4567-e89b-12d3-a456-426614174000.ttf", SubtitleFontChoice.filename(valid))
        listOf(
            "../font.ttf", "/tmp/a.otf", "https://example.com/f.ttf",
            "custom:../fonts.ttf", "custom:abc.ttf", "custom:123e4567-e89b-12d3-a456-426614174000.ttf/../",
            "unknown",
        ).forEach { assertFalse(SubtitleFontChoice.isValid(it)) }
    }
}
