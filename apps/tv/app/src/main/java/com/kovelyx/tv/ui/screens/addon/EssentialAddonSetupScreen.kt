package com.kovelyx.tv.ui.screens.addon

import com.kovelyx.tv.ui.theme.KovelyxTheme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kovelyx.tv.R
import com.kovelyx.tv.core.server.AddonWebConfigMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.TextStyle

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun EssentialAddonSetupScreen(
    onSkip: () -> Unit,
    viewModel: AddonManagerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current

    DisposableEffect(Unit) {
        onDispose { viewModel.stopQrMode() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 58.dp, vertical = 42.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.essential_addon_setup_title),
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold, textDirection = TextDirection.Content),
                color = KovelyxTheme.colors.TextPrimary,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.essential_addon_setup_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = KovelyxTheme.colors.TextSecondary
            )
            Spacer(modifier = Modifier.height(28.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(220.dp),
                    colors = CardDefaults.cardColors(containerColor = KovelyxTheme.colors.BackgroundCard),
                    shape = RoundedCornerShape(KovelyxTheme.radii.md)
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.addon_install_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, textDirection = TextDirection.Content),
                            color = KovelyxTheme.colors.TextPrimary,
                        )
                        BasicTextField(
                            value = uiState.installUrl,
                            onValueChange = viewModel::onInstallUrlChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(KovelyxTheme.spacing.xxxl),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    viewModel.installAddon()
                                    keyboardController?.hide()
                                }
                            ),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = KovelyxTheme.colors.TextPrimary,
                                            textDirection = TextDirection.Content),
                            cursorBrush = SolidColor(KovelyxTheme.colors.Primary),
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 14.dp, vertical = KovelyxTheme.spacing.md)
                                ) {
                                    if (uiState.installUrl.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.addon_install_placeholder),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = KovelyxTheme.colors.TextTertiary
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                        Button(
                            onClick = {
                                viewModel.installAddon()
                                keyboardController?.hide()
                            },
                            enabled = !uiState.isInstalling,
                            colors = ButtonDefaults.colors(
                                containerColor = KovelyxTheme.colors.Secondary,
                                contentColor = KovelyxTheme.colors.OnSecondary,
                                focusedContainerColor = KovelyxTheme.colors.SecondaryVariant
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(50))
                        ) {
                            Text(
                                text = if (uiState.isInstalling) {
                                    stringResource(R.string.addon_installing)
                                } else {
                                    stringResource(R.string.addon_install_btn)
                                }
                            )
                        }
                        AnimatedVisibility(visible = uiState.error != null) {
                            Text(
                                text = uiState.error.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = KovelyxTheme.colors.Error
                            )
                        }
                    }
                }
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(220.dp),
                    colors = CardDefaults.cardColors(containerColor = KovelyxTheme.colors.BackgroundCard),
                    shape = RoundedCornerShape(KovelyxTheme.radii.md)
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = KovelyxTheme.colors.Primary
                        )
                        Text(
                            text = stringResource(R.string.addon_manage_from_phone_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, textDirection = TextDirection.Content),
                            color = KovelyxTheme.colors.TextPrimary,
                        )
                        Text(
                            text = stringResource(R.string.addon_manage_addons_only_from_phone_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = KovelyxTheme.colors.TextSecondary
                        )
                        Button(
                            onClick = { viewModel.startQrMode(AddonWebConfigMode.ADDONS_ONLY) },
                            colors = ButtonDefaults.colors(
                                containerColor = KovelyxTheme.colors.BackgroundElevated,
                                contentColor = KovelyxTheme.colors.TextPrimary,
                                focusedContainerColor = KovelyxTheme.colors.FocusBackground,
                                focusedContentColor = KovelyxTheme.colors.Primary
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(50))
                        ) {
                            Icon(imageVector = Icons.Default.QrCode2, contentDescription = null)
                            Text(text = stringResource(R.string.essential_addon_show_qr))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(KovelyxTheme.spacing.xl))
            Button(
                onClick = onSkip,
                colors = ButtonDefaults.colors(
                    containerColor = KovelyxTheme.colors.BackgroundCard,
                    contentColor = KovelyxTheme.colors.TextPrimary,
                    focusedContainerColor = KovelyxTheme.colors.FocusBackground,
                    focusedContentColor = KovelyxTheme.colors.Primary
                ),
                shape = ButtonDefaults.shape(RoundedCornerShape(50))
            ) {
                Text(
                    text = stringResource(R.string.essential_addon_continue_for_now),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
                )
            }
        }

        if (uiState.isQrModeActive) {
            Popup(properties = PopupProperties(focusable = true)) {
                QrCodeOverlay(
                    qrBitmap = uiState.qrCodeBitmap,
                    serverUrl = uiState.serverUrl,
                    instruction = stringResource(R.string.addon_qr_addons_only_scan_instruction),
                    onClose = viewModel::stopQrMode,
                    hasPendingChange = uiState.pendingChange != null
                )
            }
        }

        if (uiState.pendingChange != null) {
            Popup(properties = PopupProperties(focusable = true)) {
                uiState.pendingChange?.let { pending ->
                    ConfirmAddonChangesDialog(
                        pendingChange = pending,
                        onConfirm = viewModel::confirmPendingChange,
                        onReject = viewModel::rejectPendingChange
                    )
                }
            }
        }

        AddonMessageOverlay(
            message = uiState.transientMessage,
            isError = uiState.transientMessageIsError
        )
    }
}
