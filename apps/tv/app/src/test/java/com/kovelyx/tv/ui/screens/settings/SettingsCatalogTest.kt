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
                SettingsCategory.CONTENT_DISCOVERY,
                SettingsCategory.PLAYBACK,
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
                SettingsCategory.CONTENT_DISCOVERY,
                SettingsCategory.INTEGRATION,
                SettingsCategory.ADVANCED
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
    fun `television categories remain grouped as five coherent pairs`() {
        val expectedPairs = listOf(
            listOf(SettingsCategory.ACCOUNT, SettingsCategory.PROFILES),
            listOf(SettingsCategory.APPEARANCE, SettingsCategory.LAYOUT),
            listOf(SettingsCategory.CONTENT_DISCOVERY, SettingsCategory.PLAYBACK),
            listOf(SettingsCategory.INTEGRATION, SettingsCategory.TRACKING),
            listOf(SettingsCategory.ADVANCED, SettingsCategory.ABOUT)
        )
        val visible = visibleSettingsCategories(
            isPrimaryProfile = true, isEssentialMode = false, isDebugBuild = false
        )
        assertEquals(expectedPairs.flatten(), visible)
        assertEquals(expectedPairs.map { it[0] }, visible.indices
            .filter { i -> i == 0 || visible.startsNewGroup(i) }
            .map { visible[it] })
        assertEquals(
            listOf(
                com.kovelyx.tv.R.string.settings_tv_group_you,
                com.kovelyx.tv.R.string.settings_tv_group_look,
                com.kovelyx.tv.R.string.settings_tv_group_watch,
                com.kovelyx.tv.R.string.settings_tv_group_services,
                com.kovelyx.tv.R.string.settings_tv_group_system
            ),
            expectedPairs.map { it[0].group.titleRes }
        )
    }

    @Test
    fun `all remote modes preserve navigation order without empty visual pairs`() {
        allCombinations().forEach { visible ->
            val regular = visible - SettingsCategory.DEBUG
            assertEquals(regular.distinct(), regular)
            val groups = regular.map { it.group }.distinct()
            assertEquals(groups, regular.map { it.group }.fold(mutableListOf<SettingsRailGroup>()) { runs, group ->
                if (runs.lastOrNull() != group) runs.add(group)
                runs
            })
            // Secondary profiles cannot see account actions; this is intentional.
            if (SettingsCategory.ACCOUNT !in regular) {
                assertFalse(SettingsCategory.PROFILES in regular)
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
