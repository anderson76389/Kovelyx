package com.kovelyx.app.features.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_settings_category_about
import nuvio.composeapp.generated.resources.compose_settings_category_general
import nuvio.composeapp.generated.resources.compose_settings_page_account
import nuvio.composeapp.generated.resources.compose_settings_page_addons
import nuvio.composeapp.generated.resources.compose_settings_page_advanced
import nuvio.composeapp.generated.resources.compose_settings_page_appearance
import nuvio.composeapp.generated.resources.settings_org_sources_title
import nuvio.composeapp.generated.resources.settings_org_debrid_title
import nuvio.composeapp.generated.resources.compose_settings_page_continue_watching
import nuvio.composeapp.generated.resources.compose_settings_page_homescreen
import nuvio.composeapp.generated.resources.settings_org_integrations_title
import nuvio.composeapp.generated.resources.compose_settings_page_licenses_attributions
import nuvio.composeapp.generated.resources.compose_settings_page_mdblist_ratings
import nuvio.composeapp.generated.resources.compose_settings_page_media_server
import nuvio.composeapp.generated.resources.compose_settings_page_media_servers
import nuvio.composeapp.generated.resources.compose_settings_page_meta_screen
import nuvio.composeapp.generated.resources.compose_settings_page_notifications
import nuvio.composeapp.generated.resources.compose_settings_page_playback
import nuvio.composeapp.generated.resources.compose_settings_page_plugins
import nuvio.composeapp.generated.resources.compose_settings_page_poster_customization
import nuvio.composeapp.generated.resources.compose_settings_page_root
import nuvio.composeapp.generated.resources.settings_org_stream_presentation
import nuvio.composeapp.generated.resources.compose_settings_page_supporters_contributors
import nuvio.composeapp.generated.resources.compose_settings_page_tmdb_enrichment
import nuvio.composeapp.generated.resources.compose_settings_page_trakt
import nuvio.composeapp.generated.resources.compose_settings_page_tracking
import nuvio.composeapp.generated.resources.settings_account
import nuvio.composeapp.generated.resources.settings_nav_group_you
import nuvio.composeapp.generated.resources.settings_nav_group_look
import nuvio.composeapp.generated.resources.settings_nav_group_watch
import nuvio.composeapp.generated.resources.settings_nav_group_services
import nuvio.composeapp.generated.resources.settings_nav_group_system
import nuvio.composeapp.generated.resources.settings_nav_layout_title
import org.jetbrains.compose.resources.StringResource

internal enum class SettingsCategory(
    val labelRes: StringResource,
    val icon: ImageVector,
) {
    You(Res.string.settings_nav_group_you, Icons.Rounded.AccountCircle),
    Look(Res.string.settings_nav_group_look, Icons.Rounded.Settings),
    Watch(Res.string.settings_nav_group_watch, Icons.Rounded.Tune),
    Services(Res.string.settings_nav_group_services, Icons.Rounded.Settings),
    System(Res.string.settings_nav_group_system, Icons.Rounded.Info),
}

internal enum class SettingsPage(
    val titleRes: StringResource,
    val category: SettingsCategory,
    val parentPage: SettingsPage?,
) {
    Root(
        titleRes = Res.string.compose_settings_page_root,
        category = SettingsCategory.Look,
        parentPage = null,
    ),
    Layout(
        titleRes = Res.string.settings_nav_layout_title,
        category = SettingsCategory.Look,
        parentPage = Root,
    ),
    Account(
        titleRes = Res.string.compose_settings_page_account,
        category = SettingsCategory.You,
        parentPage = Root,
    ),
    SupportersContributors(
        titleRes = Res.string.compose_settings_page_supporters_contributors,
        category = SettingsCategory.System,
        parentPage = Root,
    ),
    LicensesAttributions(
        titleRes = Res.string.compose_settings_page_licenses_attributions,
        category = SettingsCategory.System,
        parentPage = Root,
    ),
    Playback(
        titleRes = Res.string.compose_settings_page_playback,
        category = SettingsCategory.Watch,
        parentPage = Root,
    ),
    Appearance(
        titleRes = Res.string.compose_settings_page_appearance,
        category = SettingsCategory.Look,
        parentPage = Root,
    ),
    Streams(
        titleRes = Res.string.settings_org_stream_presentation,
        category = SettingsCategory.Look,
        parentPage = Layout,
    ),
    Advanced(
        titleRes = Res.string.compose_settings_page_advanced,
        category = SettingsCategory.System,
        parentPage = Root,
    ),
    Notifications(
        titleRes = Res.string.compose_settings_page_notifications,
        category = SettingsCategory.System,
        parentPage = Root,
    ),
    ContinueWatching(
        titleRes = Res.string.compose_settings_page_continue_watching,
        category = SettingsCategory.Look,
        parentPage = Layout,
    ),
    PosterCustomization(
        titleRes = Res.string.compose_settings_page_poster_customization,
        category = SettingsCategory.Look,
        parentPage = Layout,
    ),
    ContentDiscovery(
        titleRes = Res.string.settings_org_sources_title,
        category = SettingsCategory.Watch,
        parentPage = Root,
    ),
    Addons(
        titleRes = Res.string.compose_settings_page_addons,
        category = SettingsCategory.Watch,
        parentPage = ContentDiscovery,
    ),
    Plugins(
        titleRes = Res.string.compose_settings_page_plugins,
        category = SettingsCategory.Watch,
        parentPage = ContentDiscovery,
    ),
    MediaServers(
        titleRes = Res.string.compose_settings_page_media_servers,
        category = SettingsCategory.Watch,
        parentPage = ContentDiscovery,
    ),
    MediaServer(
        titleRes = Res.string.compose_settings_page_media_server,
        category = SettingsCategory.Watch,
        parentPage = MediaServers,
    ),
    Homescreen(
        titleRes = Res.string.compose_settings_page_homescreen,
        category = SettingsCategory.Look,
        parentPage = Layout,
    ),
    MetaScreen(
        titleRes = Res.string.compose_settings_page_meta_screen,
        category = SettingsCategory.Look,
        parentPage = Layout,
    ),
    Integrations(
        titleRes = Res.string.settings_org_integrations_title,
        category = SettingsCategory.Services,
        parentPage = Root,
    ),
    TmdbEnrichment(
        titleRes = Res.string.compose_settings_page_tmdb_enrichment,
        category = SettingsCategory.Services,
        parentPage = Integrations,
    ),
    MdbListRatings(
        titleRes = Res.string.compose_settings_page_mdblist_ratings,
        category = SettingsCategory.Services,
        parentPage = Integrations,
    ),
    Debrid(
        titleRes = Res.string.settings_org_debrid_title,
        category = SettingsCategory.Services,
        parentPage = Integrations,
    ),
    TraktAuthentication(
        // Keep the enum name for saved navigation-state compatibility.
        titleRes = Res.string.compose_settings_page_tracking,
        category = SettingsCategory.Services,
        parentPage = Root,
    ),
}

internal val SettingsPage.opensInlineOnTablet: Boolean
    get() = parentPage != null

internal fun SettingsPage.previousPage(): SettingsPage? = parentPage

// Previously persisted tablet sidebar selections must still open safely after regrouping.
internal fun settingsCategoryForName(name: String): SettingsCategory = when (name) {
    "Account" -> SettingsCategory.You
    "General" -> SettingsCategory.Look
    "Advanced", "About" -> SettingsCategory.System
    else -> SettingsCategory.entries.firstOrNull { it.name == name } ?: SettingsCategory.Look
}
