package com.kovelyx.app.features.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kovelyx.app.core.build.AppFeaturePolicy
import com.kovelyx.app.features.search.SearchHistoryRepository
import com.kovelyx.app.features.p2p.P2pSettingsRepository
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.settings_media_section_player
import nuvio.composeapp.generated.resources.settings_media_desc_player
import nuvio.composeapp.generated.resources.settings_media_desc_streams
import nuvio.composeapp.generated.resources.settings_media_desc_up_next
import nuvio.composeapp.generated.resources.settings_media_desc_skip
import nuvio.composeapp.generated.resources.settings_media_desc_interface
import nuvio.composeapp.generated.resources.settings_media_desc_audio
import nuvio.composeapp.generated.resources.settings_media_desc_subtitles
import nuvio.composeapp.generated.resources.settings_media_desc_video
import nuvio.composeapp.generated.resources.settings_media_desc_p2p
import nuvio.composeapp.generated.resources.collections_header
import nuvio.composeapp.generated.resources.compose_settings_page_addons
import nuvio.composeapp.generated.resources.compose_settings_page_media_servers
import nuvio.composeapp.generated.resources.compose_settings_page_plugins
import nuvio.composeapp.generated.resources.settings_content_discovery_addons_description
import nuvio.composeapp.generated.resources.settings_content_discovery_addons_description_appstore
import nuvio.composeapp.generated.resources.settings_content_discovery_media_servers_description
import nuvio.composeapp.generated.resources.settings_content_discovery_plugins_description
import nuvio.composeapp.generated.resources.settings_content_discovery_section_catalogues
import nuvio.composeapp.generated.resources.settings_content_discovery_section_personal_servers
import nuvio.composeapp.generated.resources.settings_content_discovery_collections_description
import nuvio.composeapp.generated.resources.settings_content_discovery_section_search
import nuvio.composeapp.generated.resources.settings_content_discovery_recent_searches
import nuvio.composeapp.generated.resources.settings_content_discovery_recent_searches_description
import org.jetbrains.compose.resources.stringResource

internal fun LazyListScope.contentDiscoveryContent(
    isTablet: Boolean,
    showPluginsEntry: Boolean,
    onAddonsClick: () -> Unit,
    onPluginsClick: () -> Unit,
    onCollectionsClick: () -> Unit,
    onMediaServersClick: () -> Unit,
    onMediaPageClick: (SettingsPage) -> Unit,
) {
    // Every playback control now lives on an actual child page of Content Discovery.
    // Only the selected page is composed; the previous giant player panel is not
    // assembled when the user simply opens Media > Content Discovery.
    item {
        SettingsSection(
            title = stringResource(Res.string.settings_media_section_player),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                val pages = listOf(
                    SettingsPage.ContentPlayer to Res.string.settings_media_desc_player,
                    SettingsPage.ContentStreamSelection to Res.string.settings_media_desc_streams,
                    SettingsPage.ContentUpNext to Res.string.settings_media_desc_up_next,
                    SettingsPage.ContentSkipSegments to Res.string.settings_media_desc_skip,
                    SettingsPage.ContentPlayerInterface to Res.string.settings_media_desc_interface,
                    SettingsPage.ContentAudio to Res.string.settings_media_desc_audio,
                    SettingsPage.ContentSubtitles to Res.string.settings_media_desc_subtitles,
                    SettingsPage.ContentVideo to Res.string.settings_media_desc_video,
                    SettingsPage.ContentP2P to Res.string.settings_media_desc_p2p,
                ).filter { (page, _) ->
                    page != SettingsPage.ContentP2P || P2pSettingsRepository.isVisible
                }
                pages.forEachIndexed { index, (page, description) ->
                    if (index > 0) SettingsGroupDivider(isTablet = isTablet)
                    SettingsNavigationRow(
                        title = stringResource(page.titleRes),
                        description = stringResource(description),
                        isTablet = isTablet,
                        onClick = { onMediaPageClick(page) },
                    )
                }
            }
        }
    }

    item {
        SettingsSection(
            title = stringResource(Res.string.settings_content_discovery_section_catalogues),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_addons),
                    description = stringResource(
                        if (AppFeaturePolicy.personalMediaAddonCopyEnabled) {
                            Res.string.settings_content_discovery_addons_description_appstore
                        } else {
                            Res.string.settings_content_discovery_addons_description
                        },
                    ),
                    isTablet = isTablet,
                    onClick = onAddonsClick,
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.collections_header),
                    description = stringResource(Res.string.settings_content_discovery_collections_description),
                    isTablet = isTablet,
                    onClick = onCollectionsClick,
                )
                if (showPluginsEntry) {
                    SettingsGroupDivider(isTablet = isTablet)
                    SettingsNavigationRow(
                        title = stringResource(Res.string.compose_settings_page_plugins),
                        description = stringResource(Res.string.settings_content_discovery_plugins_description),
                        isTablet = isTablet,
                        onClick = onPluginsClick,
                    )
                }
            }
        }
    }

    item {
        SettingsSection(
            title = stringResource(Res.string.settings_content_discovery_section_personal_servers),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_media_servers),
                    description = stringResource(Res.string.settings_content_discovery_media_servers_description),
                    isTablet = isTablet,
                    onClick = onMediaServersClick,
                )
            }
        }
    }

    item {
        val recentSearchesEnabled by remember {
            SearchHistoryRepository.ensureLoaded()
            SearchHistoryRepository.enabled
        }.collectAsStateWithLifecycle()

        SettingsSection(
            title = stringResource(Res.string.settings_content_discovery_section_search),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_content_discovery_recent_searches),
                    description = stringResource(Res.string.settings_content_discovery_recent_searches_description),
                    checked = recentSearchesEnabled,
                    isTablet = isTablet,
                    onCheckedChange = SearchHistoryRepository::setEnabled,
                )
            }
        }
    }
}
