package com.nuvio.tv.ui.screens.settings

import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kovelyx.display.DisplayRefreshSettings
import com.nuvio.tv.R

@Composable
internal fun DisplayRefreshRangeSection() {
    val state by DisplayRefreshSettings.state.collectAsStateWithLifecycle()
    if (!state.enabled) return
    var editingMinimum by remember { mutableStateOf<Boolean?>(null) }
    SettingsSectionLabel(text = stringResource(R.string.display_refresh_title))
    SettingsActionRow(
        title = stringResource(R.string.display_refresh_minimum),
        subtitle = stringResource(R.string.display_refresh_description),
        value = "${state.minimum} Hz",
        enabled = state.available.any { it <= state.maximum },
        onClick = { editingMinimum = true }
    )
    SettingsActionRow(
        title = stringResource(R.string.display_refresh_maximum),
        subtitle = state.actual?.let { stringResource(R.string.display_refresh_actual, it) },
        value = "${state.maximum} Hz",
        enabled = state.available.any { it >= state.minimum },
        onClick = { editingMinimum = false }
    )
    if (!state.appliedInRange) SettingsSectionLabel(text = stringResource(R.string.display_refresh_unavailable))
    editingMinimum?.let { minimum ->
        SettingsSingleChoiceDialog(
            title = stringResource(if (minimum) R.string.display_refresh_minimum else R.string.display_refresh_maximum),
            options = state.available.sorted().filter { if (minimum) it <= state.maximum else it >= state.minimum }.map {
                SettingsPickerOption(it, "$it Hz", stringResource(R.string.display_refresh_description))
            },
            selectedValue = if (minimum) state.minimum else state.maximum,
            onOptionSelected = {
                if (minimum) DisplayRefreshSettings.selectMinimum(it) else DisplayRefreshSettings.selectMaximum(it)
                editingMinimum = null
            },
            onDismiss = { editingMinimum = null }, width = 460.dp, maxHeight = 400.dp
        )
    }
}
