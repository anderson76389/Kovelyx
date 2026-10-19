@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.account

import com.nuvio.tv.ui.theme.NuvioTheme

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.nuvio.tv.core.auth.CloudflareTvAccountClient
import com.nuvio.tv.domain.model.AuthState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.ui.res.stringResource
import com.nuvio.tv.R

@Composable
fun AuthSignInScreen(
    onBackPress: () -> Unit = {},
    onNavigateToQrSignIn: () -> Unit = {},
    onSuccess: () -> Unit = {},
    viewModel: AccountViewModel = hiltViewModel()
) {
    BackHandler { onBackPress() }
    val cloudflareEnabled = CloudflareTvAccountClient.enabled
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val canSignIn = email.isNotBlank() && password.isNotBlank() && !uiState.isLoading
    val submit = {
        if (canSignIn && cloudflareEnabled) {
            viewModel.signIn(email.trim(), password)
        }
    }
    LaunchedEffect(uiState.authState) {
        if (cloudflareEnabled && uiState.authState is AuthState.FullAccount) {
            password = ""
            onSuccess()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .background(
                    color = NuvioTheme.colors.BackgroundElevated,
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(NuvioTheme.spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.auth_signin_title),
                style = MaterialTheme.typography.headlineSmall,
                color = NuvioTheme.colors.TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))
            if (cloudflareEnabled) {
                Text(
                    text = "Connectez-vous avec votre compte Kovelyx Cloud.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NuvioTheme.colors.TextSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(18.dp))
                InputField(
                    value = email,
                    onValueChange = { email = it },
                    placeholder = "Adresse e-mail",
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                InputField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = "Mot de passe",
                    keyboardType = KeyboardType.Password,
                    isPassword = true,
                    imeAction = ImeAction.Done,
                    onImeAction = submit,
                    modifier = Modifier.fillMaxWidth()
                )
                uiState.error?.let { failure ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = failure,
                        style = MaterialTheme.typography.bodySmall,
                        color = NuvioTheme.colors.Error,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = submit,
                    enabled = canSignIn,
                    colors = ButtonDefaults.colors(
                        containerColor = NuvioTheme.colors.Secondary,
                        focusedContainerColor = NuvioTheme.colors.SecondaryVariant,
                        contentColor = NuvioTheme.colors.OnSecondary,
                        focusedContentColor = NuvioTheme.colors.OnSecondaryVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (uiState.isLoading) "Connexion…" else "Se connecter",
                        modifier = Modifier.padding(vertical = NuvioTheme.spacing.xs)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Les données locales ne seront pas écrasées. La synchronisation automatique est encore en développement.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NuvioTheme.colors.TextTertiary,
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = stringResource(R.string.auth_signin_tv_disabled),
                style = MaterialTheme.typography.bodyMedium,
                color = NuvioTheme.colors.TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(22.dp))
            Button(
                onClick = onNavigateToQrSignIn,
                colors = ButtonDefaults.colors(
                    containerColor = NuvioTheme.colors.Secondary,
                    focusedContainerColor = NuvioTheme.colors.SecondaryVariant,
                    contentColor = NuvioTheme.colors.OnSecondary,
                    focusedContentColor = NuvioTheme.colors.OnSecondaryVariant
                ),
                shape = ButtonDefaults.shape(RoundedCornerShape(50)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.auth_signin_qr_btn),
                    modifier = Modifier.padding(vertical = NuvioTheme.spacing.xs),
                    fontWeight = FontWeight.Medium
                )
            }
            }
        }
    }
}
