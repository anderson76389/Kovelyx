@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.core.trakt.KovelyxTvTraktCredentials
import com.nuvio.tv.core.trakt.KovelyxTvMdbListCredentials
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.theme.NuvioTheme

/** Edition locale des clés Trakt avec le clavier de la TV. */
@Composable
internal fun TvTraktCredentialsDialog(
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    var clientId by remember(context) { mutableStateOf(KovelyxTvTraktCredentials.savedClientId(context)) }
    var clientSecret by remember(context) { mutableStateOf(KovelyxTvTraktCredentials.savedClientSecret(context)) }
    var error by remember { mutableStateOf<String?>(null) }

    NuvioDialog(
        onDismiss = onDismiss,
        title = "Clés Trakt personnelles",
        subtitle = "Créer une application sur trakt.tv/oauth/applications puis saisir ses identifiants.",
        width = 700.dp,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TvCredentialInput(
                title = "identifiant client",
                value = clientId,
                onValueChange = { clientId = it; error = null },
            )
            TvCredentialInput(
                title = "secret client (nécessaire au flux actuel)",
                value = clientSecret,
                onValueChange = { clientSecret = it; error = null },
                secret = true,
            )
            Text(
                "Ces clés restent enregistrées sur cette télévision. Après modification, reconnectez le compte Trakt.",
                style = MaterialTheme.typography.bodySmall,
                color = NuvioTheme.colors.TextTertiary,
            )
            if (error != null) {
                Text(error.orEmpty(), color = NuvioTheme.colors.Error)
            }
        }
        SettingsDialogActionRow {
            SettingsDialogActionButton(text = "Annuler", onClick = onDismiss)
            SettingsDialogActionButton(
                text = "Enregistrer",
                primary = true,
                onClick = {
                    runCatching {
                        KovelyxTvTraktCredentials.save(context, clientId, clientSecret)
                    }.onSuccess { onSaved() }
                        .onFailure { error = it.message ?: "Enregistrement impossible." }
                },
            )
        }
    }
}

@Composable
private fun TvCredentialInput(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    secret: Boolean = false,
) {
    val focusRequester = remember { FocusRequester() }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Card(
            onClick = { focusRequester.requestFocus() },
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.colors(
                containerColor = NuvioTheme.colors.BackgroundElevated,
                focusedContainerColor = NuvioTheme.colors.BackgroundElevated,
            ),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = NuvioTheme.colors.TextPrimary),
                cursorBrush = SolidColor(NuvioTheme.colors.Primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .background(NuvioTheme.colors.BackgroundElevated)
                    .padding(14.dp),
            )
        }
    }
}

/** Le Client ID OAuth MDBList est distinct de la clé API des notes et classements. */
@Composable
internal fun TvMdbListCredentialsDialog(
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    var clientId by remember(context) {
        mutableStateOf(KovelyxTvMdbListCredentials.savedClientId(context))
    }
    var error by remember { mutableStateOf<String?>(null) }
    NuvioDialog(
        onDismiss = onDismiss,
        title = "identifiant client OAuth MDBList",
        subtitle = "Identifiant de votre application MDBList, différent de la clé API des notes.",
        width = 700.dp,
    ) {
        TvCredentialInput(
            title = "identifiant client MDBList",
            value = clientId,
            onValueChange = { clientId = it; error = null },
        )
        Text(
            "Pour la clé API des notes, utilisez plutôt Paramètres > Intégrations > MDBList.",
            style = MaterialTheme.typography.bodySmall,
            color = NuvioTheme.colors.TextTertiary,
        )
        error?.let {
            Text(it, color = NuvioTheme.colors.Error)
        }
        SettingsDialogActionRow {
            SettingsDialogActionButton(text = "Annuler", onClick = onDismiss)
            SettingsDialogActionButton(
                text = "Enregistrer",
                primary = true,
                onClick = {
                    runCatching { KovelyxTvMdbListCredentials.save(context, clientId) }
                        .onSuccess { onSaved() }
                        .onFailure { error = it.message ?: "Enregistrement impossible." }
                },
            )
        }
    }
}
