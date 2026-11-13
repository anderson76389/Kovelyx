package com.kovelyx.maintenance.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class StorageLine(val label: String, val bytes: Long, val clearKey: String? = null)
data class MaintenanceReport(val lines: List<StorageLine>, val pendingRestore: Boolean, val startupNotice: String?)

@Composable
fun MaintenancePanel(
    readStorage: suspend () -> MaintenanceReport,
    readSync: suspend () -> String,
    export: suspend (CharArray) -> String?,
    restore: suspend (CharArray) -> String?,
    clear: suspend (String) -> Unit,
    cancelRestore: suspend () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var storage by remember { mutableStateOf<MaintenanceReport?>(null) }
    var sync by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var confirmRestore by remember { mutableStateOf(false) }
    var clearKey by remember { mutableStateOf<String?>(null) }
    fun run(action: suspend () -> String?) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                message = action()
                storage = readStorage(); sync = readSync()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                message = "Opération impossible. Vérifie le mot de passe, le compte, le fichier et l'espace disponible. Tes données sont conservées."
            } finally { busy = false }
        }
    }
    LaunchedEffect(Unit) {
        try { storage = readStorage(); sync = readSync() }
        catch (_: Exception) { message = "Impossible de mesurer le stockage actuellement." }
    }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Stockage, synchronisation et sauvegarde", style = MaterialTheme.typography.titleLarge)
        Text("Les tailles sont celles des fichiers accessibles à Kovelyx. Les vidéos téléchargées et les données personnelles ne sont jamais nettoyées.", style = MaterialTheme.typography.bodySmall)
        storage?.lines?.forEach { line ->
            Column {
                Text("${line.label} : ${formatBytes(line.bytes)}")
                line.clearKey?.let { key ->
                    OutlinedButton(enabled = !busy, onClick = { clearKey = key }) { Text("Nettoyer au redémarrage") }
                }
            }
        }
        OutlinedButton(enabled = !busy, onClick = { run { "Mesures actualisées" } }) { Text("Actualiser le diagnostic") }
        Text("Synchronisation du profil actif", style = MaterialTheme.typography.titleMedium)
        Text(sync, style = MaterialTheme.typography.bodySmall)
        Text("Sauvegarde chiffrée par mot de passe", style = MaterialTheme.typography.titleMedium)
        Text("Bibliothèque, progression, profils, extensions et réglages locaux. Les sessions de connexion, PIN, caches, fichiers de plugins exécutables et vidéos téléchargées sont exclus. Restauration sur le même compte et le même type d'application. Garde ton mot de passe : il ne peut pas être récupéré. Mets la lecture en pause et évite les modifications pendant la sauvegarde.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(value = password, onValueChange = { password = it.take(256) },
            label = { Text("Mot de passe (12 caractères minimum)") }, enabled = !busy,
            visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = confirmation, onValueChange = { confirmation = it.take(256) },
            label = { Text("Confirmer pour créer une sauvegarde") }, enabled = !busy,
            visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(enabled = !busy && password.length >= 12 && password == confirmation, onClick = {
            val secret = password.toCharArray(); password = ""; confirmation = ""
            run { try { export(secret) } finally { secret.fill('\u0000') } }
        }) { Text("Créer une sauvegarde") }
        OutlinedButton(enabled = !busy && password.length >= 12, onClick = { confirmRestore = true }) { Text("Restaurer une sauvegarde") }
        if (storage?.pendingRestore == true) {
            Text("Restauration prête : ferme complètement Kovelyx, puis relance l'application.")
            OutlinedButton(enabled = !busy, onClick = { run { cancelRestore(); "Restauration annulée" } }) { Text("Annuler la restauration") }
        }
        storage?.startupNotice?.let { Text(it) }
        if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Opération en cours…") }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
    if (confirmRestore) AlertDialog(onDismissRequest = { confirmRestore = false },
        title = { Text("Restaurer au prochain démarrage ?") },
        text = { Text("Les données locales contenues dans la sauvegarde remplaceront leur version actuelle. Les données du serveur suivront ensuite les règles habituelles de synchronisation. Crée d'abord une sauvegarde de l'état actuel. Aucun fichier n'est remplacé avant validation complète.") },
        confirmButton = { TextButton(onClick = {
            confirmRestore = false
            val secret = password.toCharArray(); password = ""; confirmation = ""
            run { try { restore(secret) } finally { secret.fill('\u0000') } }
        }) { Text("Choisir le fichier") } },
        dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Annuler") } })
    clearKey?.let { key -> AlertDialog(onDismissRequest = { clearKey = null },
        title = { Text("Nettoyer ce cache au redémarrage ?") },
        text = { Text("Les fichiers temporaires seront téléchargés à nouveau si nécessaire. Ferme complètement Kovelyx puis relance-le pour appliquer le nettoyage.") },
        confirmButton = { TextButton(onClick = { clearKey = null; run { clear(key); "Nettoyage prévu au prochain démarrage" } }) { Text("Planifier le nettoyage") } },
        dismissButton = { TextButton(onClick = { clearKey = null }) { Text("Annuler") } }) }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "${bytes / (1024L * 1024)} Mio"
    bytes >= 1024 * 1024 -> "${bytes / (1024 * 1024)} Mio"
    bytes >= 1024 -> "${bytes / 1024} Kio"
    else -> "$bytes octets"
}
