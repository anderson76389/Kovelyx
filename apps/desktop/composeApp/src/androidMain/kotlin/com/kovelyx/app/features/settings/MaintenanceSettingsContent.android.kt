package com.kovelyx.app.features.settings

import androidx.compose.runtime.Composable
import com.kovelyx.app.core.auth.AuthRepository
import com.kovelyx.app.core.auth.AuthState
import com.kovelyx.app.core.cloud.CloudflareDataSync
import com.kovelyx.maintenance.android.AndroidMaintenancePanel

@Composable
internal actual fun MaintenanceSettingsContent() {
    AndroidMaintenancePanel(account = {
        when (val auth = AuthRepository.state.value) {
            is AuthState.Authenticated -> if (auth.isAnonymous) "guest" else com.kovelyx.app.core.cloud.CloudflareConfig.URL + "|" + auth.userId
            AuthState.Unauthenticated -> "guest"
            else -> error("Compte en cours de chargement")
        }
    }, readSync = { CloudflareDataSync.diagnosticText() })
}
