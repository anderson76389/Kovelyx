@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.kovelyx.tv.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kovelyx.tv.R
import com.kovelyx.tv.ui.theme.KovelyxTheme

internal val SettingsRowContentInset = 18.dp

internal enum class SettingsNoteTone { Info, Warning, Danger }

@Composable
internal fun SettingsCollapsibleSection(
    title: String,
    description: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(KovelyxTheme.spacing.sm)
    ) {
        SettingsActionRow(
            title = title,
            subtitle = description,
            value = stringResource(if (expanded) R.string.layout_open else R.string.layout_closed),
            onClick = onToggle,
            leadingIcon = icon,
            trailingIcon = if (expanded) Icons.Default.ExpandMore else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            modifier = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier,
            onFocused = onFocused
        )

        if (expanded) {
            SettingsGroupCard(modifier = Modifier.padding(start = KovelyxTheme.spacing.lg)) {
                content()
            }
        }
    }
}

@Composable
internal fun SettingsSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    description: String? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = SettingsRowContentInset,
                end = SettingsRowContentInset,
                top = KovelyxTheme.spacing.md,
                bottom = KovelyxTheme.spacing.xxs
            ),
        verticalArrangement = Arrangement.spacedBy(KovelyxTheme.spacing.xxs)
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 1.2.sp,
            color = KovelyxTheme.colors.TextTertiary
        )
        if (!description.isNullOrBlank()) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = KovelyxTheme.colors.TextTertiary
            )
        }
    }
}

@Composable
internal fun SettingsNote(
    text: String,
    modifier: Modifier = Modifier,
    tone: SettingsNoteTone = SettingsNoteTone.Info
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = when (tone) {
            SettingsNoteTone.Info -> KovelyxTheme.colors.TextSecondary
            SettingsNoteTone.Warning -> KovelyxTheme.colors.Warning
            SettingsNoteTone.Danger -> KovelyxTheme.colors.Error
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = SettingsRowContentInset,
                end = SettingsRowContentInset,
                top = KovelyxTheme.spacing.xxs,
                bottom = KovelyxTheme.spacing.xs
            )
    )
}

@Composable
internal fun SettingsRailDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = SettingsRowContentInset, end = SettingsRowContentInset, bottom = 10.dp)
            .height(KovelyxTheme.spacing.hairline)
            .background(KovelyxTheme.colors.Border)
    )
}

@Composable
internal fun SettingsTopBarDivider() {
    Box(
        modifier = Modifier
            .padding(end = KovelyxTheme.spacing.sm)
            .width(KovelyxTheme.spacing.hairline)
            .height(20.dp)
            .background(KovelyxTheme.colors.Border)
    )
}

@Composable
internal fun SettingsResetButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {}
) {
    val flat = isFlatSettingsStyle()
    val shape = if (flat) settingsRowShape() else RoundedCornerShape(SettingsPillRadius)
    Button(
        onClick = onClick,
        modifier = modifier.onFocusChanged { if (it.isFocused) onFocused() },
        shape = ButtonDefaults.shape(shape = shape),
        colors = ButtonDefaults.colors(
            containerColor = KovelyxTheme.colors.Background,
            focusedContainerColor = if (flat) settingsFocusFillColor() else KovelyxTheme.colors.Background
        ),
        border = if (flat) {
            ButtonDefaults.border(border = Border.None, focusedBorder = Border.None)
        } else {
            ButtonDefaults.border(
                focusedBorder = Border(
                    border = KovelyxTheme.focusRing.border(KovelyxTheme.spacing.xxs),
                    shape = shape
                )
            )
        },
        scale = ButtonDefaults.scale(focusedScale = 1f, pressedScale = 1f)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.RestartAlt,
                contentDescription = null,
                tint = KovelyxTheme.colors.TextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(KovelyxTheme.spacing.sm))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = KovelyxTheme.colors.TextPrimary
            )
        }
    }
}
