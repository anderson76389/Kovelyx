package com.nuvio.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.features.collection.CollectionRepository
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
import com.nuvio.app.features.mdblist.MdbListSettingsRepository
import com.nuvio.app.features.debrid.DebridSettingsRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
        return listOf("tmdbKey","mdblistKey","realDebridKey").mapNotNull { name ->
            val value = (fields[name] as? JsonPrimitive)?.content?.trim().orEmpty()
            if (value.isNotEmpty() && value.length <= 512) name to value else null
        }.toMap()
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
    val profileState by ProfileRepository.state.collectAsState()
    val activeProfile = profileState.activeProfile
    if (!isAppReady || activeProfile == null) return
    val json = incoming ?: return
    val collectionJson = remember(json) { DesktopCollectionImportBridge.extractCollectionJson(json).orEmpty() }
    val importKeys = remember(json) { DesktopCollectionImportBridge.extractSettings(json) }

    var errorMessage by remember(json) { mutableStateOf<String?>(null) }
    val currentCount by CollectionRepository.collections.collectAsState()

    DialogSurface(
        onDismissRequest = { DesktopCollectionImportBridge.discard(json) },
        title = "Importer depuis Ma-Collection",
        message = "Le site Ma-Collection souhaite installer votre sélection dans le profil « ${activeProfile.name} ».",
    ) {
        Text(
            "Cet import remplacera les ${currentCount.size} collections actuellement présentes dans ce profil. " +
                "Une sauvegarde JSON locale sera créée avant le remplacement." +
                if (importKeys.isNotEmpty()) " Après confirmation, ${importKeys.size} clé(s) de services seront également enregistrées dans ce profil." else "",
            style = MaterialTheme.typography.bodyMedium,
        )
        errorMessage?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error)
        }
        DialogButtons {
            DialogButton(
                text = "Annuler",
                onClick = { DesktopCollectionImportBridge.discard(json) },
            )
            DialogButton(
                text = "Importer dans Kovelyx",
                style = DialogButtonStyle.Primary,
                onClick = {
                    try {
                        val validation = CollectionRepository.validateJson(collectionJson)
                        if (!validation.valid) throw IllegalArgumentException(validation.error ?: "JSON invalide")
                        val backupDir = File(DesktopStorage.rootDir.toFile(), "collection-backups")
                        check(backupDir.exists() || backupDir.mkdirs()) { "Sauvegarde impossible : dossier inaccessible" }
                        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"))
                        File(backupDir, "profile-${activeProfile.profileIndex}-$stamp.json")
                            .writeText(CollectionRepository.exportToJson(), Charsets.UTF_8)
                        CollectionRepository.importFromJson(collectionJson).getOrThrow()
                        // Seulement après validation, sauvegarde et confirmation de l'utilisateur.
                        importKeys["tmdbKey"]?.let { key ->
                            TmdbSettingsRepository.ensureLoaded()
                            TmdbSettingsRepository.setApiKey(key)
                            TmdbSettingsRepository.setEnabled(true)
                        }
                        importKeys["mdblistKey"]?.let { key ->
                            MdbListSettingsRepository.ensureLoaded()
                            MdbListSettingsRepository.setApiKey(key)
                            MdbListSettingsRepository.setEnabled(true)
                        }
                        importKeys["realDebridKey"]?.let { key ->
                            DebridSettingsRepository.ensureLoaded()
                            DebridSettingsRepository.setRealDebridApiKey(key)
                            DebridSettingsRepository.setEnabled(true)
                        }
                        DesktopCollectionImportBridge.discard(json)
                    } catch (error: Exception) {
                        errorMessage = error.message ?: "Import impossible : vos collections restent inchangées."
                    }
                },
            )
        }
    }
}
