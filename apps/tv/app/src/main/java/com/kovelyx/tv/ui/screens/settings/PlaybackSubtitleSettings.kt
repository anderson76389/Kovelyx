package com.kovelyx.tv.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kovelyx.tv.R
import com.kovelyx.tv.data.local.AVAILABLE_SUBTITLE_LANGUAGES
import com.kovelyx.tv.data.local.LibassRenderType
import com.kovelyx.tv.data.local.PlayerPreference
import com.kovelyx.tv.data.local.PlayerSettings
import com.kovelyx.tv.data.local.SubtitleFontStore
import com.kovelyx.tv.data.local.SubtitleLanguageOption
import com.kovelyx.tv.data.local.SubtitleStyleSettings
import com.kovelyx.tv.data.local.displayName
import kotlin.math.roundToInt

private val subtitleColors = listOf(
    Color.White,
    Color(0xFFD9D9D9),
    Color.Yellow,
    Color.Cyan,
    Color.Green,
    Color.Magenta,
    Color(0xFFFF6B6B),
    Color(0xFFFFA500),
    Color(0xFF90EE90)
)

private val subtitleBackgroundColors = listOf(
    Color.Transparent,
    Color.Black,
    Color(0x80000000),
    Color(0xFF1A1A1A),
    Color(0xFF2D2D2D)
)

private val subtitleOutlineColors = listOf(
    Color.Black,
    Color(0xFF1A1A1A),
    Color(0xFF333333),
    Color.White
)

@Composable
internal fun PlaybackSubtitlesSection(
    settings: PlayerSettings,
    onUpdate: PlaybackSettingsUpdate,
    onOpenDialog: (PlaybackDialog) -> Unit
) {
    val style = settings.subtitleStyle
    val enabled = !settings.playerPreference.usesActivityPlayer
    val languageSelectionEnabled = enabled || settings.externalPlayerForwardSubtitles

    SettingsSectionLabel(text = stringResource(R.string.sub_languages_label))
    SettingsActionRow(
        title = stringResource(R.string.sub_preferred_lang),
        subtitle = null,
        value = subtitleLanguageLabel(style),
        enabled = languageSelectionEnabled,
        onClick = { onOpenDialog(PlaybackDialog.SUBTITLE_LANGUAGE) }
    )
    SettingsActionRow(
        title = stringResource(R.string.sub_secondary_lang),
        subtitle = null,
        value = style.secondaryPreferredLanguage
            ?.let { code -> AVAILABLE_SUBTITLE_LANGUAGES.find { it.code == code }?.displayName }
            ?: stringResource(R.string.sub_not_set),
        enabled = languageSelectionEnabled,
        onClick = { onOpenDialog(PlaybackDialog.SECONDARY_SUBTITLE_LANGUAGE) }
    )
    autoSyncSettingsItems(enabled = enabled) // AutoSync hook

    SettingsToggleRow(
        title = stringResource(R.string.sub_use_forced_subtitles),
        subtitle = stringResource(R.string.sub_use_forced_subtitles_desc),
        checked = style.useForcedSubtitles,
        onToggle = { onUpdate { setUseForcedSubtitles(!style.useForcedSubtitles) } },
        enabled = enabled
    )
    SettingsToggleRow(
        title = stringResource(R.string.sub_show_only_preferred_languages),
        subtitle = stringResource(R.string.sub_show_only_preferred_languages_desc),
        checked = style.showOnlyPreferredLanguages,
        onToggle = { onUpdate { setSubtitleShowOnlyPreferredLanguages(!style.showOnlyPreferredLanguages) } },
        enabled = enabled
    )
    SettingsToggleRow(
        title = stringResource(R.string.sub_strip_sdh),
        subtitle = stringResource(R.string.sub_strip_sdh_desc),
        checked = style.stripSdh,
        onToggle = { onUpdate { setSubtitleStripSdh(!style.stripSdh) } },
        enabled = enabled
    )

    SettingsSectionLabel(text = stringResource(R.string.sub_style_label))
    if (enabled) SubtitleAppearancePreview(style)
    SettingsActionRow(
        title = "Police des sous-titres",
        subtitle = "Par défaut, sans empattement, serif, monospace ou .ttf/.otf local (10 Mo max.)",
        value = SubtitleFontStore.label(style.fontFamily),
        onClick = { onOpenDialog(PlaybackDialog.SUBTITLE_FONT) },
        enabled = enabled
    )
    SliderSettingsItem(
        title = stringResource(R.string.sub_size),
        value = style.size,
        valueText = "${style.size}%",
        minValue = 50,
        maxValue = 200,
        step = 10,
        onValueChange = { size -> onUpdate { setSubtitleSize(size) } },
        enabled = enabled
    )
    SliderSettingsItem(
        title = stringResource(R.string.sub_vertical_offset),
        value = style.verticalOffset,
        valueText = "${style.verticalOffset}%",
        minValue = -20,
        maxValue = 50,
        step = 1,
        onValueChange = { offset -> onUpdate { setSubtitleVerticalOffset(offset) } },
        enabled = enabled
    )
    SettingsToggleRow(
        title = stringResource(R.string.sub_bold),
        subtitle = stringResource(R.string.sub_bold_sub),
        checked = style.bold,
        onToggle = { onUpdate { setSubtitleBold(!style.bold) } },
        enabled = enabled
    )
    ColorSettingsItem(
        title = stringResource(R.string.sub_text_color),
        currentColor = Color(style.textColor),
        onClick = { onOpenDialog(PlaybackDialog.SUBTITLE_TEXT_COLOR) },
        enabled = enabled
    )
    SliderSettingsItem(
        title = stringResource(R.string.subtitle_style_text_opacity),
        value = (Color(style.textColor).alpha * 100f).roundToInt(),
        valueText = "${(Color(style.textColor).alpha * 100f).roundToInt()}%",
        minValue = 0,
        maxValue = 100,
        step = 10,
        onValueChange = { opacity ->
            val updatedColor = Color(style.textColor).copy(alpha = opacity / 100f).toArgb()
            onUpdate { setSubtitleTextColor(updatedColor) }
        },
        enabled = enabled
    )
    ColorSettingsItem(
        title = stringResource(R.string.sub_bg_color),
        currentColor = Color(style.backgroundColor),
        showTransparent = style.backgroundColor == Color.Transparent.toArgb(),
        onClick = { onOpenDialog(PlaybackDialog.SUBTITLE_BACKGROUND_COLOR) },
        enabled = enabled
    )
    SettingsToggleRow(
        title = stringResource(R.string.sub_outline),
        subtitle = stringResource(R.string.sub_outline_sub),
        checked = style.outlineEnabled,
        onToggle = { onUpdate { setSubtitleOutlineEnabled(!style.outlineEnabled) } },
        enabled = enabled
    )
    if (style.outlineEnabled) {
        ColorSettingsItem(
            title = stringResource(R.string.sub_outline_color),
            currentColor = Color(style.outlineColor),
            onClick = { onOpenDialog(PlaybackDialog.SUBTITLE_OUTLINE_COLOR) },
            enabled = enabled
        )
    }

    SettingsSectionLabel(text = stringResource(R.string.sub_advanced_section))
    SettingsToggleRow(
        title = stringResource(R.string.sub_libass),
        subtitle = stringResource(R.string.sub_libass_sub),
        checked = settings.useLibass,
        onToggle = { onUpdate { setUseLibass(!settings.useLibass) } },
        enabled = enabled
    )
    if (settings.useLibass) {
        SettingsActionRow(
            title = stringResource(R.string.sub_libass_mode),
            subtitle = null,
            value = libassRenderTypeOptions().firstOrNull { it.value == settings.libassRenderType }?.title,
            onClick = { onOpenDialog(PlaybackDialog.LIBASS_RENDER_TYPE) }
        )
    }
}

@Composable
private fun SubtitleAppearancePreview(style: SubtitleStyleSettings) {
    val context = LocalContext.current
    val fontFamily = remember(context, style.fontFamily, style.bold) {
        FontFamily(SubtitleFontStore.resolve(context, style.fontFamily, style.bold))
    }
    val textColor = Color(style.textColor)
    val textStyle = TextStyle(
        color = textColor,
        fontFamily = fontFamily,
        fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
        fontSize = (22 * style.size.coerceIn(50, 200) / 100f).sp,
        textAlign = TextAlign.Center
    )
    val outlineWidth = with(LocalDensity.current) { style.outlineWidth.coerceIn(1, 5).dp.toPx() }
    val outline = Color(style.outlineColor)
    val sample = stringResource(R.string.subtitle_style_preview_sample)
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = 128.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF222B3C), Color(0xFF5B6575))))
            .padding(start = 12.dp, top = 12.dp, end = 12.dp,
                bottom = (12 + (style.verticalOffset.coerceIn(-20, 50) + 20) * 0.6f).dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(Modifier.background(Color(style.backgroundColor)).padding(horizontal = 6.dp, vertical = 2.dp)) {
            if (style.outlineEnabled) {
                BasicText(
                    text = sample,
                    modifier = Modifier.clearAndSetSemantics { },
                    style = textStyle.copy(
                        color = outline.copy(alpha = outline.alpha * textColor.alpha),
                        drawStyle = Stroke(width = outlineWidth)
                    )
                )
            }
            BasicText(text = sample, style = textStyle)
        }
    }
}

@Composable
internal fun subtitleLanguageLabel(style: SubtitleStyleSettings): String = when {
    style.preferredLanguage == "none" -> stringResource(R.string.action_none)
    style.isPreferredLanguageSystemDefault -> stringResource(R.string.appearance_language_system)
    else -> AVAILABLE_SUBTITLE_LANGUAGES.find { it.code == style.preferredLanguage }?.displayName
        ?: stringResource(R.string.appearance_language_system)
}

@Composable
private fun libassRenderTypeOptions(): List<SettingsPickerOption<LibassRenderType>> = listOf(
    SettingsPickerOption(
        LibassRenderType.OVERLAY_OPEN_GL,
        stringResource(R.string.sub_mode_overlay_gl),
        stringResource(R.string.sub_mode_overlay_gl_sub)
    ),
    SettingsPickerOption(
        LibassRenderType.OVERLAY_CANVAS,
        stringResource(R.string.sub_mode_overlay_canvas),
        stringResource(R.string.sub_mode_overlay_canvas_sub)
    ),
    SettingsPickerOption(
        LibassRenderType.EFFECTS_OPEN_GL,
        stringResource(R.string.sub_mode_effects_gl),
        stringResource(R.string.sub_mode_effects_gl_sub)
    ),
    SettingsPickerOption(
        LibassRenderType.EFFECTS_CANVAS,
        stringResource(R.string.sub_mode_effects_canvas),
        stringResource(R.string.sub_mode_effects_canvas_sub)
    ),
    SettingsPickerOption(
        LibassRenderType.CUES,
        stringResource(R.string.sub_mode_standard),
        stringResource(R.string.sub_mode_standard_sub)
    )
)

@Composable
internal fun SubtitleSettingsDialogs(
    dialog: PlaybackDialog?,
    settings: PlayerSettings,
    onUpdate: PlaybackSettingsUpdate,
    onDismiss: () -> Unit
) {
    val style = settings.subtitleStyle
    when (dialog) {
        PlaybackDialog.SUBTITLE_FONT -> SubtitleFontDialog(
            selected = style.fontFamily,
            onSelected = { choice ->
                onUpdate { setSubtitleFontFamily(choice) }
                onDismiss()
            },
            onDismiss = onDismiss
        )
        PlaybackDialog.SUBTITLE_LANGUAGE -> LanguageSelectionDialog(
            title = stringResource(R.string.sub_preferred_lang),
            selectedLanguage = when {
                style.preferredLanguage == "none" -> null
                style.isPreferredLanguageSystemDefault -> SubtitleLanguageOption.DEVICE
                else -> style.preferredLanguage
            },
            showNoneOption = true,
            extraOptions = listOf(SubtitleLanguageOption.DEVICE to stringResource(R.string.appearance_language_system)),
            onLanguageSelected = { language ->
                onUpdate { setSubtitlePreferredLanguage(language ?: "none") }
                onDismiss()
            },
            onDismiss = onDismiss
        )
        PlaybackDialog.SECONDARY_SUBTITLE_LANGUAGE -> LanguageSelectionDialog(
            title = stringResource(R.string.sub_secondary_lang),
            selectedLanguage = style.secondaryPreferredLanguage,
            showNoneOption = true,
            onLanguageSelected = { language ->
                onUpdate { setSubtitleSecondaryLanguage(language) }
                onDismiss()
            },
            onDismiss = onDismiss
        )
        PlaybackDialog.SUBTITLE_TEXT_COLOR -> ColorSelectionDialog(
            title = stringResource(R.string.sub_text_color),
            colors = subtitleColors,
            selectedColor = Color(style.textColor),
            onColorSelected = { color ->
                onUpdate { setSubtitleTextColor(color.copy(alpha = Color(style.textColor).alpha).toArgb()) }
                onDismiss()
            },
            onDismiss = onDismiss
        )
        PlaybackDialog.SUBTITLE_BACKGROUND_COLOR -> ColorSelectionDialog(
            title = stringResource(R.string.sub_bg_color),
            colors = subtitleBackgroundColors,
            selectedColor = Color(style.backgroundColor),
            showTransparentOption = true,
            onColorSelected = { color ->
                onUpdate { setSubtitleBackgroundColor(color.toArgb()) }
                onDismiss()
            },
            onDismiss = onDismiss
        )
        PlaybackDialog.SUBTITLE_OUTLINE_COLOR -> ColorSelectionDialog(
            title = stringResource(R.string.sub_outline_color),
            colors = subtitleOutlineColors,
            selectedColor = Color(style.outlineColor),
            onColorSelected = { color ->
                onUpdate { setSubtitleOutlineColor(color.toArgb()) }
                onDismiss()
            },
            onDismiss = onDismiss
        )
        PlaybackDialog.LIBASS_RENDER_TYPE -> SettingsSingleChoiceDialog(
            title = stringResource(R.string.sub_libass_mode),
            options = libassRenderTypeOptions(),
            selectedValue = settings.libassRenderType,
            onOptionSelected = { renderType ->
                onUpdate { setLibassRenderType(renderType) }
                onDismiss()
            },
            onDismiss = onDismiss,
            width = 480.dp,
            maxHeight = 380.dp
        )
        else -> Unit
    }
}
