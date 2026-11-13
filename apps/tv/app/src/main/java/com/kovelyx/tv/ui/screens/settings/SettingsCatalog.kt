package com.kovelyx.tv.ui.screens.settings

import androidx.annotation.StringRes
import com.kovelyx.tv.R

/**
 * Android TV garde des catégories propres à la télécommande (mise en page)
 * et n'affiche pas la rubrique Notifications du mobile. Les destinations
 * ordinaires sont regroupées par couples cohérents, comme sur Desktop.
 */
internal enum class SettingsRailGroup(@StringRes val titleRes: Int) {
    YOU(R.string.settings_tv_group_account),
    LOOK(R.string.settings_tv_group_appearance),
    WATCH(R.string.settings_tv_group_playback_content),
    SERVICES(R.string.settings_tv_group_integrations_tracking),
    SYSTEM(R.string.settings_tv_group_system_about),
    DIAGNOSTICS(R.string.settings_tv_group_diagnostics)
}

internal enum class SettingsCategory(val group: SettingsRailGroup) {
    ACCOUNT(SettingsRailGroup.YOU),
    PROFILES(SettingsRailGroup.YOU),
    APPEARANCE(SettingsRailGroup.LOOK),
    LAYOUT(SettingsRailGroup.LOOK),
    PLAYBACK(SettingsRailGroup.WATCH),
    CONTENT_DISCOVERY(SettingsRailGroup.WATCH),
    INTEGRATION(SettingsRailGroup.SERVICES),
    TRACKING(SettingsRailGroup.SERVICES),
    ADVANCED(SettingsRailGroup.SYSTEM),
    ABOUT(SettingsRailGroup.SYSTEM),
    DEBUG(SettingsRailGroup.DIAGNOSTICS),
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

internal fun List<SettingsCategory>.startsLabeledGroup(index: Int): Boolean =
    index == 0 || startsNewGroup(index)
