package com.kovelyx.tv.ui.screens.settings

import com.kovelyx.tv.ui.theme.KovelyxTheme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.kovelyx.tv.R
import com.kovelyx.tv.data.local.PlayerSettings
import com.kovelyx.tv.ui.components.P2pConsentDialog

internal object PlaybackSettingsTestTags {
    fun section(section: PlaybackSection): String = "playback_section_${section.name.lowercase()}"
    fun pair(firstSection: PlaybackSection): String = "playback_pair_${firstSection.name.lowercase()}"
}

@Composable
internal fun PlaybackSettingsSections(
    playerSettings: PlayerSettings,
    p2p: P2pSettingsUi,
    transparentLetterbox: Boolean,
    onUpdate: PlaybackSettingsUpdate,
    onOpenDialog: (PlaybackDialog) -> Unit,
    onMemorySettingChanged: () -> Unit,
    onClearTorrentCache: () -> Unit,
    initialFocusRequester: FocusRequester? = null
) {
    val sections = visiblePlaybackSections(playerSettings)
    val pairs = sections.chunked(2)
    var expandedSections by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val listState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = KovelyxTheme.spacing.xs, bottom = KovelyxTheme.spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(KovelyxTheme.spacing.md)
        ) {
            items(items = pairs, key = { it.first().name }) { pair ->
                val first = pair.first()
                val headingRes = first.pairHeadingRes
                    ?: R.string.settings_tv_playback_pair_network.takeIf { first == PlaybackSection.P2P }
                // A real shared card, not a label above two unrelated sections.
                SettingsGroupCard(
                    title = headingRes?.let { stringResource(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(PlaybackSettingsTestTags.pair(first))
                ) {
                    pair.forEachIndexed { index, section ->
                        if (index > 0) SettingsPairDivider()
                        val expanded = section.name in expandedSections
                        SettingsCollapsibleSection(
                            title = stringResource(section.title),
                            description = stringResource(section.description),
                            icon = section.icon,
                            expanded = expanded,
                            onToggle = {
                                expandedSections = if (expanded) {
                                    expandedSections - section.name
                                } else {
                                    expandedSections + section.name
                                }
                            },
                            focusRequester = if (section == sections.first()) initialFocusRequester else null,
                            modifier = Modifier.testTag(PlaybackSettingsTestTags.section(section))
                        ) {
                            PlaybackSectionContent(
                                section = section,
                                playerSettings = playerSettings,
                                p2p = p2p,
                                transparentLetterbox = transparentLetterbox,
                                onUpdate = onUpdate,
                                onOpenDialog = onOpenDialog,
                                onMemorySettingChanged = onMemorySettingChanged,
                                onClearTorrentCache = onClearTorrentCache
                            )
                        }
                    }
                }
            }
        }
        SettingsVerticalScrollIndicators(state = listState)
    }
}

@Composable
private fun PlaybackSectionContent(
    section: PlaybackSection,
    playerSettings: PlayerSettings,
    p2p: P2pSettingsUi,
    transparentLetterbox: Boolean,
    onUpdate: PlaybackSettingsUpdate,
    onOpenDialog: (PlaybackDialog) -> Unit,
    onMemorySettingChanged: () -> Unit,
    onClearTorrentCache: () -> Unit
) {
    when (section) {
        PlaybackSection.PLAYER -> PlaybackPlayerSection(playerSettings, onUpdate, onOpenDialog)
        PlaybackSection.STREAM_SELECTION -> PlaybackStreamSelectionSection(playerSettings, onUpdate, onOpenDialog)
        PlaybackSection.UP_NEXT -> PlaybackUpNextSection(playerSettings, onUpdate, onOpenDialog)
        PlaybackSection.SKIP_SEGMENTS -> PlaybackSkipSegmentsSection(playerSettings, onUpdate)
        PlaybackSection.PLAYER_INTERFACE -> PlaybackPlayerInterfaceSection(playerSettings, onUpdate)
        PlaybackSection.AUDIO -> PlaybackAudioSection(playerSettings, onUpdate, onOpenDialog)
        PlaybackSection.SUBTITLES -> PlaybackSubtitlesSection(playerSettings, onUpdate, onOpenDialog)
        PlaybackSection.VIDEO -> PlaybackVideoSection(playerSettings, transparentLetterbox, onUpdate, onOpenDialog)
        PlaybackSection.BUFFER_NETWORK -> PlaybackBufferNetworkSection(playerSettings, onUpdate, onMemorySettingChanged)
        PlaybackSection.P2P -> PlaybackP2pSection(p2p, onUpdate, onOpenDialog, onClearTorrentCache)
    }
}

@Composable
internal fun PlaybackSettingsDialogs(
    dialog: PlaybackDialog?,
    settings: PlayerSettings,
    p2p: P2pSettingsUi,
    installedAddonNames: List<String>,
    enabledPluginNames: List<String>,
    onUpdate: PlaybackSettingsUpdate,
    onDismiss: () -> Unit
) {
    when (dialog) {
        null -> Unit
        PlaybackDialog.PLAYER_PREFERENCE -> PlayerPreferenceDialog(
            currentPreference = settings.playerPreference,
            onPreferenceSelected = { preference ->
                onUpdate { setPlayerPreference(preference) }
                onDismiss()
            },
            onDismiss = onDismiss
        )
        PlaybackDialog.INTERNAL_ENGINE -> InternalPlayerEngineDialog(
            currentEngine = settings.internalPlayerEngine,
            onEngineSelected = { engine ->
                onUpdate { setInternalPlayerEngine(engine) }
                onDismiss()
            },
            onDismiss = onDismiss
        )
        PlaybackDialog.P2P_CONSENT -> P2pConsentDialog(
            onEnableP2p = {
                onUpdate { setP2pEnabled(true) }
                onDismiss()
            },
            onDismiss = onDismiss
        )
        else -> {
            AutoPlaySettingsDialogs(dialog, settings, installedAddonNames, enabledPluginNames, onUpdate, onDismiss)
            AudioSettingsDialogs(dialog, settings, onUpdate, onDismiss)
            SubtitleSettingsDialogs(dialog, settings, onUpdate, onDismiss)
            VideoSettingsDialogs(dialog, settings, onUpdate, onDismiss)
            P2pSettingsDialogs(dialog, p2p, onUpdate, onDismiss)
        }
    }
}
