package com.kovelyx.app.features.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kovelyx.app.core.build.AppFeaturePolicy
import com.kovelyx.app.features.search.SearchHistoryRepository
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

internal fun LazyListScope.contentDiscoveryContent(
    isTablet: Boolean,
    showPluginsEntry: Boolean,
    onAddonsClick: () -> Unit,
    onPluginsClick: () -> Unit,
    onCollectionsClick: () -> Unit,
    onMediaServersClick: () -> Unit,
    onPlaybackTopicClick: (SettingsPage) -> Unit,
) {
    item {
        SettingsSection(
            title = stringResource(Res.string.kovelyx_media_options_section),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsNavigationRow(
                    title = stringResource(Res.string.kovelyx_media_group_player_title),
                    description = stringResource(Res.string.kovelyx_media_group_player_description),
                    isTablet = isTablet,
                    onClick = { onPlaybackTopicClick(SettingsPage.MediaPlayer) },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.kovelyx_media_group_streams_title),
                    description = stringResource(Res.string.kovelyx_media_group_streams_description),
                    isTablet = isTablet,
                    onClick = { onPlaybackTopicClick(SettingsPage.MediaStreamSelection) },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.kovelyx_media_group_episodes_title),
                    description = stringResource(Res.string.kovelyx_media_group_episodes_description),
                    isTablet = isTablet,
                    onClick = { onPlaybackTopicClick(SettingsPage.MediaUpNext) },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.kovelyx_media_group_audio_video_title),
                    description = stringResource(Res.string.kovelyx_media_group_audio_video_description),
                    isTablet = isTablet,
                    onClick = { onPlaybackTopicClick(SettingsPage.MediaAudio) },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.kovelyx_media_subtitles_title),
                    description = stringResource(Res.string.kovelyx_media_subtitles_description),
                    isTablet = isTablet,
                    onClick = { onPlaybackTopicClick(SettingsPage.MediaSubtitles) },
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
            title = stringResource(Res.string.kovelyx_media_group_catalogue),
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
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_media_servers),
                    description = stringResource(Res.string.settings_content_discovery_media_servers_description),
                    isTablet = isTablet,
                    onClick = onMediaServersClick,
                )
                SettingsGroupDivider(isTablet = isTablet)
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
