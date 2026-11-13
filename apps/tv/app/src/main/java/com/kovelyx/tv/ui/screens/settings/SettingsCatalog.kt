package com.kovelyx.tv.ui.screens.settings

import androidx.annotation.StringRes
import com.kovelyx.tv.R

internal enum class SettingsRailGroup {
    YOU,
    LOOK,
    WATCH,
    SERVICES,
    SYSTEM
}

/** Visual-only headings: the categories, saved selections and focus order are unchanged. */
@get:StringRes
internal val SettingsRailGroup.titleRes: Int
    get() = when (this) {
        SettingsRailGroup.YOU -> R.string.settings_tv_group_you
        SettingsRailGroup.LOOK -> R.string.settings_tv_group_look
        SettingsRailGroup.WATCH -> R.string.settings_tv_group_watch
        SettingsRailGroup.SERVICES -> R.string.settings_tv_group_services
        SettingsRailGroup.SYSTEM -> R.string.settings_tv_group_system
    }

internal enum class SettingsCategory(val group: SettingsRailGroup) {
    ACCOUNT(SettingsRailGroup.YOU),
    PROFILES(SettingsRailGroup.YOU),
    APPEARANCE(SettingsRailGroup.LOOK),
    LAYOUT(SettingsRailGroup.LOOK),
    CONTENT_DISCOVERY(SettingsRailGroup.WATCH),
    PLAYBACK(SettingsRailGroup.WATCH),
    INTEGRATION(SettingsRailGroup.SERVICES),
    TRACKING(SettingsRailGroup.SERVICES),
    ADVANCED(SettingsRailGroup.SYSTEM),
    ABOUT(SettingsRailGroup.SYSTEM),
    DEBUG(SettingsRailGroup.SYSTEM),
    EXPERIENCE(SettingsRailGroup.SYSTEM)
}

internal fun visibleSettingsCategories(
    isPrimaryProfile: Boolean,
    isEssentialMode: Boolean,
    isDebugBuild: Boolean
): List<SettingsCategory> = SettingsCategory.entries.filter { category ->
    when (category) {
        SettingsCategory.ACCOUNT,
        SettingsCategory.PROFILES -> isPrimaryProfile
        SettingsCategory.DEBUG -> isDebugBuild && !isEssentialMode
        SettingsCategory.EXPERIENCE -> false
        else -> true
    }
}

internal fun List<SettingsCategory>.startsNewGroup(index: Int): Boolean =
    index > 0 && this[index - 1].group != this[index].group
