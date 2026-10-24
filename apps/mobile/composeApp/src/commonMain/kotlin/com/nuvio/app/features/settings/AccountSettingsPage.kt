package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.core.auth.AuthState
import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.core.ui.NuvioPrimaryButton
import com.nuvio.app.core.ui.NuvioStatusModal
import com.nuvio.app.core.ui.NuvioSurfaceCard
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_cancel
import nuvio.composeapp.generated.resources.compose_settings_page_account
import nuvio.composeapp.generated.resources.auth_account_deletion_failed
import nuvio.composeapp.generated.resources.settings_account_delete_account
import nuvio.composeapp.generated.resources.settings_account_delete_account_description
import nuvio.composeapp.generated.resources.settings_account_delete_confirm_message
import nuvio.composeapp.generated.resources.settings_account_delete_confirm_title
import nuvio.composeapp.generated.resources.settings_account_email
import nuvio.composeapp.generated.resources.settings_account_not_signed_in
import nuvio.composeapp.generated.resources.settings_account_sign_out
import nuvio.composeapp.generated.resources.settings_account_sign_out_confirm_message
import nuvio.composeapp.generated.resources.settings_account_sign_out_confirm_title
import nuvio.composeapp.generated.resources.settings_account_status
import nuvio.composeapp.generated.resources.settings_account_status_anonymous
import nuvio.composeapp.generated.resources.settings_account_status_signed_in
import org.jetbrains.compose.resources.stringResource

internal fun LazyListScope.accountSettingsContent(
    isTablet: Boolean,
) {
    item {
        AccountSettingsBody(isTablet = isTablet)
    }
}

@Composable
private fun AccountSettingsBody(
    isTablet: Boolean,
) {
    val authState by AuthRepository.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showSignOutConfirm by remember { mutableStateOf(false) }
    var signingOut by remember { mutableStateOf(false) }
    var signOutError by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isDeletingAccount by remember { mutableStateOf(false) }
    var deleteErrorMessage by remember { mutableStateOf<String?>(null) }
    val deleteAccountFallbackMessage = stringResource(Res.string.auth_account_deletion_failed)
    val canDeleteAccount = AppFeaturePolicy.accountDeletionEnabled && authState is AuthState.Authenticated

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NuvioSurfaceCard {
            Text(
                text = stringResource(Res.string.compose_settings_page_account),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(14.dp))

            when (val state = authState) {
                is AuthState.Authenticated -> {
                    AccountInfoRow(
                        label = stringResource(Res.string.settings_account_status),
                        value = if (state.isAnonymous) {
                            stringResource(Res.string.settings_account_status_anonymous)
                        } else {
                            stringResource(Res.string.settings_account_status_signed_in)
                        },
                        valueColor = MaterialTheme.colorScheme.primary,
                    )
                    state.email?.takeUnless { state.isAnonymous }?.let { email ->
                        Spacer(modifier = Modifier.height(8.dp))
                        AccountInfoRow(
                            label = stringResource(Res.string.settings_account_email),
                            value = email,
                        )
                    }
                }
                else -> {
                    Text(
                        text = stringResource(Res.string.settings_account_not_signed_in),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Ne jamais proposer une déconnexion lorsque l'écran indique
        // « Non connecté ». Le mode invité a aussi un état Authenticated.
        if (authState is AuthState.Authenticated) {
            NuvioPrimaryButton(
                text = stringResource(Res.string.settings_account_sign_out),
                onClick = {
                    if (!signingOut) {
                        signOutError = null
                        showSignOutConfirm = true
                    }
                },
            )
        }
        signOutError?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (canDeleteAccount) {
            DeleteAccountCard(
                errorMessage = deleteErrorMessage,
                onDeleteClick = {
                    deleteErrorMessage = null
                    showDeleteConfirm = true
                },
            )
        }
    }

    NuvioStatusModal(
        title = stringResource(Res.string.settings_account_sign_out_confirm_title),
        message = stringResource(Res.string.settings_account_sign_out_confirm_message),
        isVisible = showSignOutConfirm && authState is AuthState.Authenticated,
        isBusy = signingOut,
        confirmText = stringResource(Res.string.settings_account_sign_out),
        dismissText = stringResource(Res.string.action_cancel),
        onConfirm = {
            if (!signingOut) {
                signingOut = true
                scope.launch {
                    val result = AuthRepository.signOut()
                    signingOut = false
                    showSignOutConfirm = false
                    signOutError = result.exceptionOrNull()?.message
                        ?: if (result.isFailure) "Déconnexion impossible. Réessayez." else null
                }
            }
        },
        onDismiss = {
            if (!signingOut) showSignOutConfirm = false
        },
    )

    NuvioStatusModal(
        title = stringResource(Res.string.settings_account_delete_confirm_title),
        message = stringResource(Res.string.settings_account_delete_confirm_message),
        isVisible = showDeleteConfirm,
        isBusy = isDeletingAccount,
        destructive = true,
        confirmText = stringResource(Res.string.settings_account_delete_account),
        dismissText = stringResource(Res.string.action_cancel),
        onConfirm = {
            if (isDeletingAccount) return@NuvioStatusModal
            isDeletingAccount = true
            scope.launch {
                val result = AuthRepository.deleteAccount()
                isDeletingAccount = false
                showDeleteConfirm = false
                deleteErrorMessage = if (result.isSuccess) {
                    null
                } else {
                    AuthRepository.error.value
                        ?: result.exceptionOrNull()?.message
                        ?: deleteAccountFallbackMessage
                }
            }
        },
        onDismiss = {
            if (!isDeletingAccount) {
                showDeleteConfirm = false
            }
        },
    )
}

@Composable
private fun DeleteAccountCard(
    errorMessage: String?,
    onDeleteClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio

    NuvioSurfaceCard {
        Text(
            text = stringResource(Res.string.settings_account_delete_account),
            style = MaterialTheme.typography.titleMedium,
            color = tokens.colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.settings_account_delete_account_description),
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.colors.textMuted,
        )
        errorMessage?.let { message ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.danger,
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Button(
            onClick = onDeleteClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(NuvioTokens.Space.s48 + NuvioTokens.Space.s4),
            shape = tokens.shapes.button,
            colors = ButtonDefaults.buttonColors(
                containerColor = tokens.colors.danger,
                contentColor = tokens.colors.textInverse,
            ),
        ) {
            Text(
                text = stringResource(Res.string.settings_account_delete_account),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AccountInfoRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = valueColor,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
