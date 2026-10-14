package com.nuvio.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.core.auth.AuthState
import com.nuvio.app.features.collection.CollectionSyncService
import com.nuvio.app.features.profiles.InlinePinEntry
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.AddAddonResult
import com.nuvio.app.features.collection.CollectionRepository
import com.nuvio.app.core.poster.CustomPosterUrlRepository
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
import com.nuvio.app.features.mdblist.MdbListSettingsRepository
import com.nuvio.app.features.debrid.DebridSettingsRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.coroutines.launch
import com.nuvio.app.features.profiles.ProfileRepository
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The collection is transferred directly from the user's web browser to the
 * running local desktop app. No Nuvio account, public upload, or cloud service.
 *
 * HTTP binds ONLY to IPv4 loopback. The exact origin is checked and the user
 * MUST approve replacement in the desktop app; POST never writes collections.
 */
internal object DesktopCollectionImportBridge {
    private const val Port = 38761
    private const val MaxBodyBytes = 16 * 1024 * 1024
    private const val HealthMarker = "Kovelyx-Ma-Collection"
    private val allowedOrigins = setOf(
        "https://anderson76389.github.io",
        "http://localhost:8000",
        "http://127.0.0.1:8000",
    )

    private val _pending = MutableStateFlow<String?>(null)
    val pending = _pending.asStateFlow()

    @Volatile private var server: HttpServer? = null

    /** Ancien tableau JSON ou enveloppe de configuration issue du site officiel. */
    internal fun extractCollectionJson(payload: String): String? = runCatching {
        when (val parsed = Json.parseToJsonElement(payload)) {
            is JsonArray -> parsed.toString()
            is JsonObject -> (parsed["collections"] as? JsonArray)?.toString()
            else -> null
        }
    }.getOrNull()

    /** On ne prend en charge que les clés intégrées nativement à Kovelyx PC. */
    internal fun extractSettings(payload: String): Map<String,String> {
        val parsed = runCatching { Json.parseToJsonElement(payload) as? JsonObject }.getOrNull()
        val fields = parsed?.get("settings") as? JsonObject ?: return emptyMap()
        return listOf("tmdbKey","mdblistKey","realDebridKey","tmdbLanguage","importMode","importAction",
            "posterProvider","rpdbTheme","rpdbKey","bttrQuality","bttrGenre","bttrRating",
            "bttrAge","bttrSource","bttrLanguage","topPosterKey").mapNotNull { name ->
            val value = (fields[name] as? JsonPrimitive)?.content?.trim().orEmpty()
            if (value.isNotEmpty() && value.length <= 512) name to value else null
        }.toMap()
    }

    internal fun rpdbPosterPattern(settings: Map<String, String>): String? {
        if (settings["posterProvider"] != "rpdb") return null
        val theme = settings["rpdbTheme"] ?: "t0-free-rpdb"
        val token = if (theme == "custom") {
            settings["rpdbKey"]?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,128}")) }
        } else {
            theme.takeIf { it in setOf("t0-free-rpdb", "t0-free-rpdb-blocks", "t0-free-rpdb-rounded-blocks") }
        } ?: return null
        return "https://api.ratingposterdb.com/$token/imdb/poster-default/{imdb_id}.jpg"
    }

    /** Extensions configurées par l'utilisateur : validation stricte avant tout accès réseau. */

    internal fun posterPattern(settings: Map<String, String>): String? {
        when (settings["posterProvider"]) {
            "rpdb" -> return rpdbPosterPattern(settings)
            "bttr" -> {
                val genre = settings["bttrGenre"] != "false"
                val rating = settings["bttrRating"] != "false"
                val modifiers = buildString {
                    if (!genre && !rating) append('n')
                    else {
                        if (!genre) append('r')
                        if (!rating) append('g')
                    }
                    if (settings["bttrQuality"] == "true") append('q')
                    if (settings["bttrAge"] == "true") append('a')
                }
                val format = if (modifiers.isEmpty()) "poster" else "poster-$modifiers"
                val lang = if (settings["bttrLanguage"] == "en") "en" else "fr"
                val source = settings["bttrSource"]?.takeIf { it in setOf("TM","IM","RT","TR") }
                val params = mutableListOf<String>()
                if (lang != "en") params.add("lang=$lang")
                if (source != null) params.add("rs=$source")
                val query = if (params.isEmpty()) "" else "?" + params.joinToString("&")
                return "https://btttr.cc/$format/imdb/poster-default/{imdb_id}.jpg$query"
            }
            "top" -> {
                val key = settings["topPosterKey"]?.takeIf {
                    it.matches(Regex("[A-Za-z0-9_-]{1,128}"))
                } ?: return null
                return "https://api.top-posters.com/$key/imdb/poster/{imdb_id}.jpg?lang=fr"
            }
            else -> return null
        }
    }

    internal fun mergeCollectionPayload(current: String, incoming: String): String {
        val old = Json.parseToJsonElement(current) as JsonArray
        val added = Json.parseToJsonElement(incoming) as JsonArray
        val collections = linkedMapOf<String, JsonObject>()
        fun idOf(item: JsonObject): String = (item["id"] as? JsonPrimitive)?.content.orEmpty()
        for (entry in old) {
            val item = entry as JsonObject
            collections[idOf(item)] = item
        }
        for (entry in added) {
            val item = entry as JsonObject
            val id = idOf(item)
            val prior = collections[id]
            if (prior == null) {
                collections[id] = item
            } else {
                val oldFolders = prior["folders"] as? JsonArray ?: JsonArray(emptyList())
                val newFolders = item["folders"] as? JsonArray ?: JsonArray(emptyList())
                val existingIds = oldFolders.map { idOf(it as JsonObject) }.toHashSet()
                val additions = newFolders.filter { idOf(it as JsonObject) !in existingIds }
                collections[id] = JsonObject(prior.toMutableMap().apply {
                    put("folders", JsonArray(oldFolders + additions))
                })
            }
        }
        return JsonArray(collections.values.toList()).toString()
    }

    internal fun extractAddonManifests(payload: String): List<String> {
        val root = Json.parseToJsonElement(payload) as? JsonObject ?: return emptyList()
        val settings = root["settings"] as? JsonObject ?: return emptyList()
        val manifests = settings["addonManifests"] as? JsonArray ?: return emptyList()
        require(manifests.size <= 15) { "Trop d'extensions (maximum 15)" }
        return manifests.map { item ->
            val url = (item as? JsonPrimitive)?.content ?: throw IllegalArgumentException("Manifest d'extension invalide")
            val parsed = URL(url)
            require(url.length <= 2048 && parsed.protocol.equals("https", true) &&
                parsed.userInfo == null && parsed.path.endsWith("/manifest.json")) {
                "Adresse d'extension invalide : HTTPS et manifest.json requis"
            }
            url
        }.distinct()
    }

    @Synchronized fun start() {
        if (server != null) return
        runCatching {
            val created = HttpServer.create(
                InetSocketAddress(InetAddress.getByName("127.0.0.1"), Port), 0,
            )
            created.createContext("/health", ::handleRequest)
            created.createContext("/import", ::handleRequest)
            created.executor = Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "kovelyx-collection-import").apply { isDaemon = true }
            }
            created.start()
            server = created
        }.onFailure { exception ->
            System.err.println("Kovelyx: local collection bridge unavailable: " + exception.message)
        }
    }

    /** A custom-protocol launch must not start a second instance of an already running Kovelyx. */
    fun alreadyRunning(): Boolean = runCatching {
        val connection = (URL("http://127.0.0.1:$Port/health").openConnection() as HttpURLConnection)
        connection.connectTimeout = 350
        connection.readTimeout = 350
        connection.requestMethod = "GET"
        try {
            connection.responseCode == 200 &&
                connection.getHeaderField("X-Kovelyx-Bridge") == HealthMarker
        } finally {
            connection.disconnect()
        }
    }.getOrDefault(false)

    /** Per-user URL registration, no administrator privileges, never claims nuvio:// or stremio://. */
    fun registerWindowsProtocol() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) return
        val launcher = System.getProperty("jpackage.app-path")?.takeIf(String::isNotBlank)
            ?: ProcessHandle.current().info().command().orElse(null)
            ?: return
        if (!File(launcher).isFile || !File(launcher).name.equals("Kovelyx.exe", ignoreCase = true)) return
        val root = "HKCU\\Software\\Classes\\kovelyx"
        val actions = listOf(
            listOf("reg.exe", "add", root, "/ve", "/t", "REG_SZ", "/d", "URL:Kovelyx", "/f"),
            listOf("reg.exe", "add", root, "/v", "URL Protocol", "/t", "REG_SZ", "/d", "", "/f"),
            listOf(
                "reg.exe", "add", "$root\\shell\\open\\command", "/ve", "/t", "REG_SZ",
                "/d", "\"$launcher\" \"%1\"", "/f",
            ),
        )
        actions.forEach { command ->
            runCatching {
                val process = ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
                if (!process.waitFor(4, TimeUnit.SECONDS)) process.destroyForcibly()
            }
        }
    }

    fun discard(json: String) {
        if (_pending.value == json) _pending.value = null
    }

    private fun handleRequest(exchange: HttpExchange) {
        try {
            val origin = exchange.requestHeaders.getFirst("Origin").orEmpty()
            if (origin !in allowedOrigins && origin.isNotEmpty()) {
                respond(exchange, 403, "Origine non autorisée")
                return
            }
            // Non-browser requests without Origin may read the health marker,
            // but NEVER submit a collection without an authorized browser origin.
            if (origin in allowedOrigins) {
                exchange.responseHeaders.set("Access-Control-Allow-Origin", origin)
                exchange.responseHeaders.set("Vary", "Origin")
                exchange.responseHeaders.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
                exchange.responseHeaders.set("Access-Control-Allow-Headers", "Content-Type")
                exchange.responseHeaders.set("Access-Control-Allow-Private-Network", "true")
                exchange.responseHeaders.set("Access-Control-Max-Age", "600")
            }

            if (exchange.requestMethod == "OPTIONS" && origin in allowedOrigins) {
                exchange.sendResponseHeaders(204, -1)
                return
            }
            if (exchange.httpContext.path == "/health" && exchange.requestMethod == "GET") {
                exchange.responseHeaders.set("X-Kovelyx-Bridge", HealthMarker)
                respond(exchange, 200, "Kovelyx prêt")
                return
            }
            if (exchange.httpContext.path != "/import" || exchange.requestMethod != "POST") {
                respond(exchange, 405, "Méthode indisponible")
                return
            }
            if (origin !in allowedOrigins) {
                respond(exchange, 403, "Site non autorisé")
                return
            }
            if (!exchange.requestHeaders.getFirst("Content-Type").orEmpty()
                    .startsWith("application/json", ignoreCase = true)) {
                respond(exchange, 415, "Format JSON requis")
                return
            }
            val bytes = exchange.requestBody.use { it.readNBytes(MaxBodyBytes + 1) }
            if (bytes.size > MaxBodyBytes) {
                respond(exchange, 413, "Collection trop volumineuse")
                return
            }
            val json = String(bytes, StandardCharsets.UTF_8)
            if (extractCollectionJson(json) == null) {
                respond(exchange, 422, "La collection doit être un tableau JSON ou une enveloppe valide")
                return
            }
            if (_pending.value != null) {
                respond(exchange, 409, "Un import est déjà en attente dans Kovelyx")
                return
            }
            _pending.value = json
            respond(exchange, 202, "Confirmez l'import dans Kovelyx")
        } catch (exception: Exception) {
            runCatching { respond(exchange, 500, "Erreur du transfert local") }
        } finally {
            exchange.close()
        }
    }

    private fun respond(exchange: HttpExchange, code: Int, message: String) {
        val bytes = message.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
        exchange.responseHeaders.set("Cache-Control", "no-store")
        exchange.sendResponseHeaders(code, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
}

@Composable
internal fun DesktopCollectionImportPrompt(isAppReady: Boolean) {
    val incoming by DesktopCollectionImportBridge.pending.collectAsState()
    if (!isAppReady) return
    val payload = incoming ?: return
    val profileState by ProfileRepository.state.collectAsState()
    val authState by AuthRepository.state.collectAsState()
    val activeProfile = profileState.activeProfile

    val collectionJson = remember(payload) { DesktopCollectionImportBridge.extractCollectionJson(payload).orEmpty() }
    val importedSettings = remember(payload) { DesktopCollectionImportBridge.extractSettings(payload) }
    val posterPattern = remember(payload) { DesktopCollectionImportBridge.posterPattern(importedSettings) }
    val addonResult = remember(payload) { runCatching { DesktopCollectionImportBridge.extractAddonManifests(payload) } }
    val addonUrls = addonResult.getOrDefault(emptyList())
    val accountMode = importedSettings["importMode"] == "account"
    val auth = authState as? AuthState.Authenticated
    val accountConnected = auth != null && !auth.isAnonymous
    val scope = rememberCoroutineScope()

    var selectedProfileIndex by remember(payload) { mutableStateOf<Int?>(null) }
    var replaceCollections by remember(payload) { mutableStateOf(importedSettings["importAction"] != "merge") }
    var email by remember(payload) { mutableStateOf("") }
    var password by remember(payload) { mutableStateOf("") }
    var errorMessage by remember(payload) { mutableStateOf<String?>(null) }
    var processing by remember(payload) { mutableStateOf(false) }
    var completed by remember(payload) { mutableStateOf(false) }
    var pinVerifiedIdentity by remember(payload) { mutableStateOf<String?>(null) }

    LaunchedEffect(payload, accountConnected) {
        if (accountMode && accountConnected && auth != null) {
            ProfileRepository.ensureLoaded(auth.userId)
            ProfileRepository.pullProfiles()
        }
    }

    val availableProfiles = if (accountMode && accountConnected) profileState.profiles else emptyList()
    val targetProfile = if (accountMode) {
        availableProfiles.firstOrNull { it.profileIndex == selectedProfileIndex }
    } else activeProfile
    val activeCount by CollectionRepository.collections.collectAsState()

    DialogSurface(
        onDismissRequest = { if (!processing) DesktopCollectionImportBridge.discard(payload) },
        title = "Importer depuis Ma-Collection",
        message = "Choisissez votre profil et confirmez la mise à jour des catalogues dans Kovelyx PC.",
    ) {
        if (accountMode && !accountConnected) {
            Text(
                "Connexion facultative : vous avez choisi le mode compte. " +
                    "Connectez-vous ici avec votre compte Kovelyx pour récupérer ses profils. " +
                    "Les identifiants sont transmis par l'application à votre serveur d'authentification, jamais au site Ma-Collection.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = email, onValueChange = { email = it },
                label = { Text("Adresse e-mail Kovelyx") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text("Mot de passe Kovelyx") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            DialogButtons {
                DialogButton(text = "Annuler", onClick = { DesktopCollectionImportBridge.discard(payload) })
                DialogButton(
                    text = if (processing) "Connexion en cours…" else "Se connecter",
                    style = DialogButtonStyle.Primary,
                    onClick = {
                        if (processing) return@DialogButton
                        if (email.isBlank() || password.isBlank()) {
                            errorMessage = "Renseignez votre e-mail et votre mot de passe."
                            return@DialogButton
                        }
                        processing = true
                        scope.launch {
                            try {
                                AuthRepository.signInWithEmail(email.trim(), password).getOrThrow()
                                password = ""
                                val authenticated = AuthRepository.state.value as? AuthState.Authenticated
                                if (authenticated != null && !authenticated.isAnonymous) {
                                    ProfileRepository.ensureLoaded(authenticated.userId)
                                    ProfileRepository.pullProfiles()
                                }
                                errorMessage = null
                            } catch (error: Exception) {
                                password = ""
                                errorMessage = "Connexion impossible. Vérifiez votre compte ou le serveur Kovelyx."
                            } finally {
                                processing = false
                            }
                        }
                    },
                )
            }
            return@DialogSurface
        }

        if (accountMode) {
            Text("Profils de votre compte Kovelyx :", style = MaterialTheme.typography.bodyMedium)
            if (availableProfiles.isEmpty()) {
                Text("Aucun profil disponible. Vérifiez votre connexion au compte et vos profils dans Kovelyx.")
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                availableProfiles.forEach { profile ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable(enabled = !processing) { selectedProfileIndex = profile.profileIndex }
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selectedProfileIndex == profile.profileIndex,
                            onClick = { if (!processing) selectedProfileIndex = profile.profileIndex },
                        )
                        Text(
                            profile.name + if (profile.pinEnabled) " (protégé par code PIN)" else "",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        } else {
            Text(
                "Sans connexion : import dans le profil local actif « " +
                    (activeProfile?.name ?: "aucun profil sélectionné") + " ».",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Text(
            "Méthode d'importation :", style = MaterialTheme.typography.bodyMedium,
        )
        Row(
            modifier = Modifier.fillMaxWidth().clickable(enabled = !processing) { replaceCollections = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = replaceCollections, onClick = { replaceCollections = true })
            Text("Remplacer toutes les anciennes collections (avec sauvegarde)")
        }
        Row(
            modifier = Modifier.fillMaxWidth().clickable(enabled = !processing) { replaceCollections = false },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = !replaceCollections, onClick = { replaceCollections = false })
            Text("Conserver les anciennes collections et ajouter les nouvelles")
        }
        Text(
            if (targetProfile?.profileIndex == activeProfile?.profileIndex) {
                "Le profil sélectionné contient actuellement " + activeCount.size + " catégories."
            } else {
                "Les catégories du profil ciblé seront chargées avant leur sauvegarde et l'importation."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "Une sauvegarde JSON locale du profil choisi sera créée AVANT la modification. " +
                (if (importedSettings.isNotEmpty()) "Les paramètres compatibles seront aussi appliqués." else "") +
                (if (addonUrls.isNotEmpty()) " " + addonUrls.size + " extension(s) seront vérifiées." else ""),
            style = MaterialTheme.typography.bodyMedium,
        )
        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        val identity = (auth?.userId ?: "local") + ":" + (targetProfile?.profileIndex ?: -1)
        if (targetProfile?.pinEnabled == true && pinVerifiedIdentity != identity) {
            Text("Le profil est protégé par un code PIN. Vérifiez-le avant de remplacer ses collections.")
            InlinePinEntry(
                profileName = targetProfile.name,
                onVerified = { pinVerifiedIdentity = identity },
                onCancel = { DesktopCollectionImportBridge.discard(payload) },
                verifyPin = { pin -> ProfileRepository.verifyPin(targetProfile.profileIndex, pin) },
            )
            return@DialogSurface
        }
        DialogButtons {
            DialogButton(
                text = "Annuler",
                onClick = { if (!processing) DesktopCollectionImportBridge.discard(payload) },
            )
            DialogButton(
                text = when {
                    processing -> "Importation en cours…"
                    completed -> "Terminer"
                    replaceCollections -> "Sauvegarder et remplacer"
                    else -> "Sauvegarder et compléter"
                },
                style = DialogButtonStyle.Primary,
                onClick = {
                    if (processing) return@DialogButton
                    if (completed) {
                        DesktopCollectionImportBridge.discard(payload)
                        return@DialogButton
                    }
                    val chosen = targetProfile
                    if (chosen == null) {
                        errorMessage = if (accountMode) "Sélectionnez un profil de votre compte."
                            else "Activez d'abord un profil local dans Kovelyx."
                        return@DialogButton
                    }
                    // Ne jamais contourner le PIN en changeant de profil via le pont local.
                    if (chosen.pinEnabled &&
                        pinVerifiedIdentity != (auth?.userId ?: "local") + ":" + chosen.profileIndex) {
                        errorMessage = "Vérifiez le code PIN avant de continuer."
                        return@DialogButton
                    }
                    try {
                        addonResult.getOrThrow()
                        val provider = importedSettings["posterProvider"]
                        if (provider in setOf("rpdb", "bttr", "top") && posterPattern == null) {
                            throw IllegalArgumentException("Paramètres d'affiches invalides.")
                        }
                        val validation = CollectionRepository.validateJson(collectionJson)
                        if (!validation.valid) throw IllegalArgumentException(validation.error ?: "Collection JSON invalide")
                    } catch (error: Exception) {
                        errorMessage = error.message ?: "Configuration invalide."
                        return@DialogButton
                    }
                    processing = true
                    scope.launch {
                        try {
                            if (accountMode) {
                                val latestAuth = AuthRepository.state.value as? AuthState.Authenticated
                                check(latestAuth != null && !latestAuth.isAnonymous) {
                                    "Votre session a expiré. Reconnectez-vous dans Kovelyx."
                                }
                            }
                            if (ProfileRepository.activeProfileId != chosen.profileIndex) {
                                ProfileRepository.switchToProfile(chosen.profileIndex)
                            }
                            // Synchroniser le profil de compte avant de sauvegarder ses collections.
                            CollectionRepository.initialize()
                            if (accountMode) CollectionSyncService.pullFromServer(chosen.profileIndex)
                            val currentJson = CollectionRepository.exportToJson()
                            val finalJson = if (replaceCollections) collectionJson
                                else DesktopCollectionImportBridge.mergeCollectionPayload(currentJson, collectionJson)
                            val finalValidation = CollectionRepository.validateJson(finalJson)
                            check(finalValidation.valid) { finalValidation.error ?: "Import JSON invalide" }
                            val backupDir = File(DesktopStorage.rootDir.toFile(), "collection-backups")
                            check(backupDir.exists() || backupDir.mkdirs()) { "Dossier de sauvegarde inaccessible" }
                            val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"))
                            File(backupDir, "profile-" + chosen.profileIndex + "-" + stamp + ".json")
                                .writeText(currentJson, Charsets.UTF_8)
                            CollectionRepository.importFromJson(finalJson).getOrThrow()

                            importedSettings["tmdbKey"]?.let { key ->
                                TmdbSettingsRepository.setApiKey(key)
                                TmdbSettingsRepository.setEnabled(true)
                            }
                            // Corrige la régression des résumés en anglais au moment de l'import.
                            TmdbSettingsRepository.setLanguage(
                                if (importedSettings["tmdbLanguage"] == "en") "en" else "fr"
                            )
                            importedSettings["mdblistKey"]?.let { key ->
                                MdbListSettingsRepository.setApiKey(key)
                                MdbListSettingsRepository.setEnabled(true)
                            }
                            importedSettings["realDebridKey"]?.let { key ->
                                DebridSettingsRepository.setRealDebridApiKey(key)
                                DebridSettingsRepository.setEnabled(true)
                            }
                            posterPattern?.let { CustomPosterUrlRepository.setPattern(it) }
                            var failedAddons = 0
                            if (addonUrls.isNotEmpty()) {
                                AddonRepository.initialize()
                                for (url in addonUrls) {
                                    when (AddonRepository.addAddon(url)) {
                                        is AddAddonResult.Success -> Unit
                                        is AddAddonResult.Error -> failedAddons++
                                    }
                                }
                            }
                            if (failedAddons == 0) {
                                DesktopCollectionImportBridge.discard(payload)
                            } else {
                                errorMessage = "Collections importées, mais " + failedAddons +
                                    " extension(s) ont échoué. Vérifiez leurs manifests puis cliquez sur Terminer."
                                completed = true
                            }
                        } catch (error: Exception) {
                            errorMessage = error.message ?: "Importation impossible. Vérifiez les sauvegardes."
                        } finally {
                            processing = false
                        }
                    }
                },
            )
        }
    }
}
