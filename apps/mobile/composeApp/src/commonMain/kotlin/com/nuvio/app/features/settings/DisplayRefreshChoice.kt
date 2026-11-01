package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun DisplayRefreshChoice(isTablet: Boolean) {
    val state by DisplayRefreshSettings.state.collectAsStateWithLifecycle()
    if (!state.enabled) return
    SettingsSection(title = stringResource(Res.string.display_refresh_title), isTablet = isTablet) {
        Text(stringResource(Res.string.display_refresh_description), modifier = Modifier.padding(horizontal = 16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
            listOf(60, 120).forEach { rate ->
                FilterChip(selected = state.requested == rate,
                    enabled = rate in state.available,
                    onClick = { DisplayRefreshSettings.select(rate) }, label = { Text("$rate Hz") })
            }
        }
        if (state.actual != state.requested) {
            Text(stringResource(Res.string.display_refresh_unavailable), modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}
