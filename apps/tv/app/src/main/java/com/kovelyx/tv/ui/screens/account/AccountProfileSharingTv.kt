@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package com.kovelyx.tv.ui.screens.account

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kovelyx.tv.core.auth.CloudflareTvAccountClient
import com.kovelyx.tv.core.sync.CloudflareTvProfileSharing
import com.kovelyx.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.launch

/** TV D-pad friendly manual one-shot sharing; cloud profiles are never linked implicitly. */
@Composable
internal fun AccountProfileSharingTv() {
    val account = CloudflareTvAccountClient.account
    var profiles by remember(account?.id) { mutableStateOf<List<Pair<String,String>>>(emptyList()) }
    var source by remember(account?.id) { mutableStateOf("") }
    var targets by remember(account?.id) { mutableStateOf(setOf<String>()) }
    var addons by remember(account?.id) { mutableStateOf(true) }
    var catalogues by remember(account?.id) { mutableStateOf(true) }
    var busy by remember(account?.id) { mutableStateOf(false) }
    var confirmation by remember(account?.id) { mutableStateOf(false) }
    var status by remember(account?.id) { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(account?.id) {
        if (account == null) return@LaunchedEffect
        try {
            profiles = CloudflareTvAccountClient.cloudProfiles()
            source = profiles.firstOrNull()?.first.orEmpty()
            targets = emptySet()
        } catch (error: Exception) {
            status = error.message ?: "Profils Cloudflare indisponibles"
        }
    }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("Synchroniser avec d'autres profils", style = MaterialTheme.typography.titleMedium,
            color = NuvioTheme.colors.TextPrimary)
        Text("Choisissez le profil source, les destinataires et le contenu à copier. " +
            "Les données existantes sont fusionnées, jamais supprimées. " +
            "Les futures modifications ne seront pas automatiquement partagées.",
            style = MaterialTheme.typography.bodySmall,color = NuvioTheme.colors.TextSecondary)
        if (profiles.size < 2) {
            Text("Deux profils Cloudflare sont nécessaires.",
                style = MaterialTheme.typography.bodySmall,color = NuvioTheme.colors.TextTertiary)
        } else {
            Text("Profil source",style = MaterialTheme.typography.bodyMedium,
                color = NuvioTheme.colors.TextPrimary)
            profiles.forEach { item ->
                ShareTvChoice(
                    text = (if (item.first == source) "✓ " else "○ ") + item.second,
                    enabled = !busy,
                    onClick = {
                        source = item.first
                        targets = targets - item.first
                        confirmation = false
                    }
                )
            }
            Text("Profils destinataires",style = MaterialTheme.typography.bodyMedium,
                color = NuvioTheme.colors.TextPrimary)
            profiles.filter { it.first != source }.forEach { item ->
                ShareTvChoice(
                    text = (if (item.first in targets) "✓ " else "○ ") + item.second,
                    enabled = !busy,
                    onClick = {
                        targets = if (item.first in targets) targets - item.first else targets + item.first
                        confirmation = false
                    }
                )
            }
            ShareTvChoice(
                text = (if (addons) "✓ " else "○ ") + "Extensions installées",
                enabled = !busy, onClick = { addons = !addons; confirmation = false }
            )
            ShareTvChoice(
                text = (if (catalogues) "✓ " else "○ ") + "Catalogues externes et collections",
                enabled = !busy, onClick = { catalogues = !catalogues; confirmation = false }
            )
            Text("Les URLs des extensions personnalisées peuvent contenir des clés privées. " +
                "Seuls les profils sélectionnés de votre compte les recevront.",
                style = MaterialTheme.typography.bodySmall,color = NuvioTheme.colors.TextTertiary)
            ShareTvChoice(
                text = if (busy) "Synchronisation en cours…"
                    else if (confirmation) "CONFIRMER LE PARTAGE VERS " + targets.size + " PROFIL(S)"
                    else "Synchroniser les profils sélectionnés",
                enabled = !busy && targets.isNotEmpty() && (addons || catalogues),
                onClick = {
                    if (!confirmation) {
                        confirmation = true
                        status = "Appuyez une seconde fois pour confirmer la fusion sans suppression."
                    } else {
                        confirmation = false
                        busy = true
                        status = "Copie Cloudflare en cours…"
                        val savedSource = source
                        val savedTargets = targets.toSet()
                        val shareAddons = addons
                        val shareCatalogues = catalogues
                        scope.launch {
                            try {
                                val result = CloudflareTvProfileSharing.share(
                                    savedSource,savedTargets,shareAddons,shareCatalogues)
                                status = "Terminé : " + result.profiles + " profil(s), " +
                                    result.addons + " listes d'extensions et " +
                                    result.catalogues + " ensembles de catalogues."
                            } catch (error: Exception) {
                                status = "Partage interrompu : " + (error.message ?: "Erreur Cloudflare") +
                                    ". Aucun écrasement forcé."
                            } finally { busy = false }
                        }
                    }
                }
            )
        }
        if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall,
            color = NuvioTheme.colors.TextSecondary)
    }
}

@Composable
private fun ShareTvChoice(text: String, enabled: Boolean, onClick: () -> Unit) {
    Card(
        onClick = { if (enabled) onClick() },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.colors(
            containerColor = NuvioTheme.colors.BackgroundCard,
            focusedContainerColor = NuvioTheme.colors.FocusBackground
        )
    ) {
        Text(text, modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) NuvioTheme.colors.TextPrimary else NuvioTheme.colors.TextTertiary)
    }
}
