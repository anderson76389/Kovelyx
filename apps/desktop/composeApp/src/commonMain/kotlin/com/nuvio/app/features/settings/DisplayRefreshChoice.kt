package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kovelyx.display.DisplayRefreshSettings
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.display_refresh_title
import nuvio.composeapp.generated.resources.display_refresh_description
import nuvio.composeapp.generated.resources.display_refresh_unavailable
import nuvio.composeapp.generated.resources.display_refresh_minimum
import nuvio.composeapp.generated.resources.display_refresh_maximum
import nuvio.composeapp.generated.resources.display_refresh_actual
import org.jetbrains.compose.resources.stringResource

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun DisplayRefreshChoice(isTablet: Boolean) {
    val state by DisplayRefreshSettings.state.collectAsStateWithLifecycle()
    if (!state.enabled) return
    val choices = (state.available + state.minimum + state.maximum).sorted()
    SettingsSection(title = stringResource(Res.string.display_refresh_title), isTablet = isTablet) {
        Text(stringResource(Res.string.display_refresh_description), modifier = Modifier.padding(horizontal = 16.dp))
        Text(stringResource(Res.string.display_refresh_minimum), modifier = Modifier.padding(horizontal = 16.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
            choices.forEach { rate ->
                FilterChip(selected = state.minimum == rate,
                    enabled = rate in state.available && rate <= state.maximum,
                    onClick = { DisplayRefreshSettings.selectMinimum(rate) }, label = { Text("$rate Hz") })
            }
        }
        Text(stringResource(Res.string.display_refresh_maximum), modifier = Modifier.padding(horizontal = 16.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
            choices.forEach { rate ->
                FilterChip(selected = state.maximum == rate,
                    enabled = rate in state.available && rate >= state.minimum,
                    onClick = { DisplayRefreshSettings.selectMaximum(rate) }, label = { Text("$rate Hz") })
            }
        }
        state.actual?.let { Text(stringResource(Res.string.display_refresh_actual, it), modifier = Modifier.padding(horizontal = 16.dp)) }
        if (!state.appliedInRange) {
            Text(stringResource(Res.string.display_refresh_unavailable), modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}
