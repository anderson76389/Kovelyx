package com.kovelyx.app.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kovelyx.display.DisplayRefreshSettings
import com.kovelyx.app.core.ui.DialogSurface
import com.kovelyx.app.core.ui.DialogButton
import com.kovelyx.app.core.ui.DialogButtons
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun DisplayRefreshChoice(isTablet: Boolean) {
    val state by DisplayRefreshSettings.state.collectAsStateWithLifecycle()
    if (!state.enabled) return
    var editingMinimum by rememberSaveable { mutableStateOf<Boolean?>(null) }
    SettingsSection(title = stringResource(Res.string.display_refresh_title), isTablet = isTablet) {
        SettingsGroup(isTablet = isTablet) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.display_refresh_range, state.minimum, state.maximum), style = MaterialTheme.typography.titleMedium)
                Text(state.actual?.let { stringResource(Res.string.display_refresh_actual, it) }
                    ?: stringResource(Res.string.display_refresh_pending), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary)
                Text(stringResource(Res.string.display_refresh_description), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.requiresSystemModeConsent) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        stringResource(Res.string.display_refresh_windows_permission),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Switch(
                        checked = state.systemModeConsentGranted,
                        onCheckedChange = DisplayRefreshSettings::setSystemModeConsent,
                    )
                }
                Text(
                    stringResource(Res.string.display_refresh_windows_warning),
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SettingsGroupDivider(isTablet)
            SettingsNavigationRow(
                title = stringResource(Res.string.display_refresh_minimum), description = null, isTablet = isTablet,
                enabled = state.available.isNotEmpty() && state.systemModeConsentGranted,
                trailingContent = { Text("${state.minimum} Hz") },
                onClick = { if (state.available.isNotEmpty()) editingMinimum = true },
            )
            SettingsGroupDivider(isTablet)
            SettingsNavigationRow(
                title = stringResource(Res.string.display_refresh_maximum), description = null, isTablet = isTablet,
                enabled = state.available.isNotEmpty() && state.systemModeConsentGranted,
                trailingContent = { Text("${state.maximum} Hz") },
                onClick = { if (state.available.isNotEmpty()) editingMinimum = false },
            )
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (state.available.isEmpty()) stringResource(Res.string.display_refresh_pending) else
                    stringResource(Res.string.display_refresh_supported, state.available.sorted().joinToString(" / ") { "$it Hz" }),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(Res.string.display_refresh_adjust_bounds), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.actual != null && state.systemModeConsentGranted && !state.appliedInRange) Text(stringResource(Res.string.display_refresh_unavailable),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
    editingMinimum?.let { minimum ->
        DialogSurface(onDismissRequest = { editingMinimum = null },
            title = stringResource(if (minimum) Res.string.display_refresh_minimum else Res.string.display_refresh_maximum)) {
            Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                state.available.sorted().forEach { rate ->
                    val selected = rate == if (minimum) state.minimum else state.maximum
                    val choose = {
                        if (minimum) DisplayRefreshSettings.chooseMinimum(rate) else DisplayRefreshSettings.chooseMaximum(rate)
                        editingMinimum = null
                    }
                    Row(Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = choose).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RadioButton(selected, onClick = null)
                        Text("$rate Hz", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            DialogButtons { DialogButton(text = stringResource(Res.string.action_cancel), onClick = { editingMinimum = null }) }
        }
    }
}
