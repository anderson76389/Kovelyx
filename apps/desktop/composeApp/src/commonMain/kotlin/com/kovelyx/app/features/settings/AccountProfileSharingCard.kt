package com.kovelyx.app.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kovelyx.app.core.cloud.CloudflareAccountClient
import com.kovelyx.app.core.cloud.CloudflareProfileSharing
import com.kovelyx.app.core.ui.KovelyxStatusModal
import com.kovelyx.app.core.ui.KovelyxSurfaceCard
import com.kovelyx.app.features.profiles.ProfileRepository
import kotlinx.coroutines.launch

/** Cloud share: manual and opt-in; never copy library, playback, PINs or images. */
@Composable
internal fun AccountProfileSharingCard() {
    val profileState by ProfileRepository.state.collectAsStateWithLifecycle()
    val account = CloudflareAccountClient.currentSession
    val source = profileState.activeProfile?.id?.takeIf { it.length == 36 }
    val sourceName = profileState.activeProfile?.name.orEmpty()
    val scope = rememberCoroutineScope()
    var profiles by remember(account?.userId, source) {
        mutableStateOf<List<CloudflareProfileSharing.CloudProfile>>(emptyList())
    }
    var selected by remember(account?.userId, source) { mutableStateOf(setOf<String>()) }
    var includeExtensions by remember(account?.userId, source) { mutableStateOf(true) }
    var includeCatalogues by remember(account?.userId, source) { mutableStateOf(true) }
    var loading by remember(account?.userId, source) { mutableStateOf(false) }
    var running by remember(account?.userId, source) { mutableStateOf(false) }
    var confirmation by remember(account?.userId, source) { mutableStateOf(false) }
    var feedback by remember(account?.userId, source) { mutableStateOf<String?>(null) }
    var failed by remember(account?.userId, source) { mutableStateOf(false) }

    LaunchedEffect(account?.userId, source) {
        profiles = emptyList()
        selected = emptySet()
        if (account == null || source == null) return@LaunchedEffect
        loading = true
        try {
            profiles = CloudflareProfileSharing.profiles().filter { it.id != source }
        } catch (error: Exception) {
            feedback = error.message ?: "Profils Cloudflare indisponibles"
            failed = true
        } finally { loading = false }
    }
    KovelyxSurfaceCard {
        Text("Synchroniser avec d'autres profils", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("Copiez depuis « $sourceName » les extensions et les catalogues externes, " +
            "y compris les collections, vers d'autres profils du même compte. " +
            "C'est une copie ponctuelle : les futures modifications ne sont pas partagées automatiquement.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        if (account == null || source == null) {
            Text("Connectez-vous à Kovelyx Cloud et sélectionnez un profil.")
        } else if (loading) {
            CircularProgressIndicator()
        } else if (profiles.isEmpty()) {
            Text("Aucun autre profil Cloudflare disponible.")
        } else {
            Text("Profils destinataires", fontWeight = FontWeight.Medium)
            for (profile in profiles) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = profile.id in selected,
                        onCheckedChange = { checked ->
                            selected = if (checked) selected + profile.id else selected - profile.id
                            feedback = null
                        }, enabled = !running)
                    Text(profile.name)
                }
            }
            Text("Contenu à partager", fontWeight = FontWeight.Medium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = includeExtensions,
                    onCheckedChange = { includeExtensions = it }, enabled = !running)
                Text("Extensions installées")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = includeCatalogues,
                    onCheckedChange = { includeCatalogues = it }, enabled = !running)
                Text("Catalogues externes et collections")
            }
            Text("Attention : des URL d'extensions peuvent contenir des clés privées. " +
                "Elles resteront dans les profils choisis de votre compte.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Button(modifier = Modifier.fillMaxWidth(),
                enabled = !running && selected.isNotEmpty() && (includeExtensions || includeCatalogues),
                onClick = { confirmation = true }) {
                Text(if (running) "Synchronisation…" else "Synchroniser les profils sélectionnés")
            }
        }
        feedback?.let { message ->
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium,
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }
    }

    KovelyxStatusModal(
        title = "Partager avec " + selected.size + " profil(s) ?",
        message = "Fusionner les éléments choisis depuis « $sourceName ». Les dossiers et " +
            "extensions déjà présents seront conservés. Les URL d'extensions " +
            "contenant des clés d'accès seront également copiées.",
        isVisible = confirmation,
        isBusy = running,
        confirmText = "Fusionner et synchroniser",
        dismissText = "Annuler",
        onConfirm = {
            if (!running && source != null) {
                running = true; failed = false; feedback = null
                val targets = selected.toSet()
                val extensions = includeExtensions
                val catalogues = includeCatalogues
                scope.launch {
                    try {
                        val result = CloudflareProfileSharing.copy(source,targets,extensions,catalogues)
                        feedback = "Terminé : " + result.profilesUpdated + " profil(s), " +
                            result.addonsUpdated + " liste(s) d'extensions, " +
                            result.cataloguesUpdated + " catalogue(s)."
                    } catch (error: Exception) {
                        failed = true
                        feedback = (error.message ?: "Partage interrompu") +
                            ". Les données déjà présentes ne sont pas supprimées."
                    } finally { running = false; confirmation = false }
                }
            }
        },
        onDismiss = { if (!running) confirmation = false },
    )
}
