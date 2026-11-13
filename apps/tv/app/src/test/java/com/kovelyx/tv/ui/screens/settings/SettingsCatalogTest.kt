package com.kovelyx.tv.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsCatalogTest {

    @Test
    fun `primary profile in advanced debug build sees every category in group order`() {
        val categories = visibleSettingsCategories(
            isPrimaryProfile = true,
            isEssentialMode = false,
            isDebugBuild = true
        )

        assertEquals(
            listOf(
                SettingsCategory.ACCOUNT,
                SettingsCategory.PROFILES,
                SettingsCategory.APPEARANCE,
                SettingsCategory.LAYOUT,
                SettingsCategory.PLAYBACK,
                SettingsCategory.CONTENT_DISCOVERY,
                SettingsCategory.INTEGRATION,
                SettingsCategory.TRACKING,
                SettingsCategory.ADVANCED,
                SettingsCategory.ABOUT,
                SettingsCategory.DEBUG
            ),
            categories
        )
    }

    @Test
    fun `secondary profile does not see account or profiles`() {
        val categories = visibleSettingsCategories(
            isPrimaryProfile = false,
            isEssentialMode = false,
            isDebugBuild = false
        )

        assertFalse(SettingsCategory.ACCOUNT in categories)
        assertFalse(SettingsCategory.PROFILES in categories)
        assertEquals(SettingsCategory.APPEARANCE, categories.first())
    }

    @Test
    fun `debug is hidden in release builds and in essential mode`() {
        assertFalse(SettingsCategory.DEBUG in visibleSettingsCategories(true, isEssentialMode = false, isDebugBuild = false))
        assertFalse(SettingsCategory.DEBUG in visibleSettingsCategories(true, isEssentialMode = true, isDebugBuild = true))
    }

    @Test
    fun `experience never appears in the rail`() {
        allCombinations().forEach { categories ->
            assertFalse(SettingsCategory.EXPERIENCE in categories)
        }
    }

    @Test
    fun `about is the last regular category`() {
        allCombinations().forEach { categories ->
            val withoutDebug = categories - SettingsCategory.DEBUG
            assertEquals(SettingsCategory.ABOUT, withoutDebug.last())
        }
    }

    @Test
    fun `each group is contiguous in every configuration`() {
        allCombinations().forEach { categories ->
            val groupRuns = categories.map { it.group }.fold(mutableListOf<SettingsRailGroup>()) { runs, group ->
                if (runs.lastOrNull() != group) runs.add(group)
                runs
            }
            assertEquals(groupRuns.distinct(), groupRuns)
        }
    }

    @Test
    fun `dividers are placed only where the group changes`() {
        val categories = visibleSettingsCategories(
            isPrimaryProfile = true,
            isEssentialMode = false,
            isDebugBuild = true
        )
        val dividerBefore = categories.indices.filter { categories.startsNewGroup(it) }.map { categories[it] }

        assertEquals(
            listOf(
                SettingsCategory.APPEARANCE,
                SettingsCategory.PLAYBACK,
                SettingsCategory.INTEGRATION,
                SettingsCategory.ADVANCED,
                SettingsCategory.DEBUG
            ),
            dividerBefore
        )
        assertFalse(categories.startsNewGroup(0))
    }

    @Test
    fun `secondary profile rail starts without a divider`() {
        val categories = visibleSettingsCategories(
            isPrimaryProfile = false,
            isEssentialMode = true,
            isDebugBuild = false
        )
        assertFalse(categories.startsNewGroup(0))
        assertTrue(categories.startsNewGroup(categories.indexOf(SettingsCategory.CONTENT_DISCOVERY)))
    }

    @Test
    fun `every ordinary TV rail group has exactly two coherent destinations`() {
        val groups = visibleSettingsCategories(
            isPrimaryProfile = true,
            isEssentialMode = false,
            isDebugBuild = false
        ).groupBy { it.group }

        assertEquals(
            listOf(SettingsCategory.ACCOUNT, SettingsCategory.PROFILES),
            groups[SettingsRailGroup.YOU]
        )
        assertEquals(
            listOf(SettingsCategory.APPEARANCE, SettingsCategory.LAYOUT),
            groups[SettingsRailGroup.LOOK]
        )
        assertEquals(
            listOf(SettingsCategory.PLAYBACK, SettingsCategory.CONTENT_DISCOVERY),
            groups[SettingsRailGroup.WATCH]
        )
        assertEquals(
            listOf(SettingsCategory.INTEGRATION, SettingsCategory.TRACKING),
            groups[SettingsRailGroup.SERVICES]
        )
        assertEquals(
            listOf(SettingsCategory.ADVANCED, SettingsCategory.ABOUT),
            groups[SettingsRailGroup.SYSTEM]
        )
        assertFalse(groups.containsKey(SettingsRailGroup.DIAGNOSTICS))
    }

    @Test
    fun `group labels do not introduce additional focusable categories`() {
        allCombinations().forEach { categories ->
            val headingAt = categories.indices.filter(categories::startsLabeledGroup)
            val groups = categories.map { it.group }.distinct()
            assertEquals(groups.size, headingAt.size)
            assertEquals(categories.indices.filter(categories::startsNewGroup).size + 1, headingAt.size)
            assertTrue(categories.startsLabeledGroup(0))
            if (SettingsCategory.DEBUG in categories) {
                assertEquals(SettingsRailGroup.DIAGNOSTICS, SettingsCategory.DEBUG.group)
                assertTrue(categories.startsNewGroup(categories.indexOf(SettingsCategory.DEBUG)))
            }
        }
    }

    private fun allCombinations(): List<List<SettingsCategory>> =
        listOf(true, false).flatMap { primary ->
            listOf(true, false).flatMap { essential ->
                listOf(true, false).map { debug ->
                    visibleSettingsCategories(primary, essential, debug)
                }
            }
        }
}
