package com.kovelyx.tv.ui.screens.collection

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kovelyx.tv.R
import com.kovelyx.tv.core.sync.CollectionSyncService
import com.kovelyx.tv.data.local.CollectionsDataStore
import com.kovelyx.tv.data.local.ValidationResult
import com.kovelyx.tv.domain.model.Collection
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal const val MAX_COLLECTION_JSON_BYTES = 16 * 1024 * 1024

/** No unbounded reads from document providers or local downloads. */
internal fun readCollectionsJson(input: java.io.InputStream): String {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        require(output.size() + count <= MAX_COLLECTION_JSON_BYTES) { "Fichier JSON trop volumineux (16 Mo maximum)." }
        output.write(buffer, 0, count)
    }
    require(output.size() > 0) { "Fichier JSON vide." }
    return output.toByteArray().decodeToString(throwOnInvalidSequence = true).removePrefix("\uFEFF")
}

enum class ImportMode { PASTE, FILE, URL }

data class CollectionManagementUiState(
    val collections: List<Collection> = emptyList(),
    val isLoading: Boolean = true,
    val showImportDialog: Boolean = false,
    val importText: String = "",
    val importError: String? = null,
    val exportedJson: String? = null,
    val importMode: ImportMode = ImportMode.FILE,
    val importUrl: String = "",
    val validationResult: ValidationResult? = null,
    val validatedJson: String? = null,
    val isLoadingImport: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class CollectionManagementViewModel @Inject constructor(
    private val collectionsDataStore: CollectionsDataStore,
    private val collectionSyncService: CollectionSyncService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(CollectionManagementUiState())
    val uiState: StateFlow<CollectionManagementUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            collectionsDataStore.collections.collectLatest { collections ->
                _uiState.update {
                    it.copy(collections = collections, isLoading = false)
                }
            }
        }
    }

    fun deleteCollection(collectionId: String) {
        viewModelScope.launch {
            collectionsDataStore.removeCollection(collectionId)
            collectionSyncService.triggerPush()
        }
    }

    fun moveUp(index: Int) {
        if (index <= 0) return
        viewModelScope.launch {
            val current = _uiState.value.collections.toMutableList()
            val item = current.removeAt(index)
            current.add(index - 1, item)
            collectionsDataStore.setCollections(current)
            collectionSyncService.triggerPush()
        }
    }

    fun moveDown(index: Int) {
        val current = _uiState.value.collections
        if (index >= current.size - 1) return
        viewModelScope.launch {
            val mutableList = current.toMutableList()
            val item = mutableList.removeAt(index)
            mutableList.add(index + 1, item)
            collectionsDataStore.setCollections(mutableList)
            collectionSyncService.triggerPush()
        }
    }

    fun exportCollections(): String {
        val json = collectionsDataStore.exportToJson(_uiState.value.collections)
        _uiState.update { it.copy(exportedJson = json) }
        return json
    }

    fun clearExported() {
        _uiState.update { it.copy(exportedJson = null) }
    }

    fun showImportDialog() {
        _uiState.update {
            it.copy(
                showImportDialog = true, importText = "", importError = null,
                importMode = ImportMode.FILE, importUrl = "",
                validationResult = null, validatedJson = null, isLoadingImport = false
            )
        }
    }

    fun hideImportDialog() {
        _uiState.update {
            it.copy(
                showImportDialog = false, importText = "", importError = null,
                importMode = ImportMode.FILE, importUrl = "",
                validationResult = null, validatedJson = null, isLoadingImport = false
            )
        }
    }

    fun updateImportText(text: String) {
        _uiState.update {
            it.copy(importText = text, importError = null, validationResult = null, validatedJson = null)
        }
    }

    fun reportImportError(message: String) {
        _uiState.update {
            it.copy(importError = message, isLoadingImport = false, validationResult = null, validatedJson = null)
        }
    }

    /** Read a document explicitly selected by the user, not a hard-coded filename. */
    fun loadFromUri(uri: android.net.Uri) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoadingImport = true, importError = null, validationResult = null, validatedJson = null)
            }
            try {
                val content = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use(::readCollectionsJson)
                        ?: error("Impossible d'ouvrir le fichier sélectionné.")
                }
                _uiState.update { it.copy(importText = content, isLoadingImport = false) }
                validateJson(content)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                reportImportError(failure.message ?: "Fichier JSON invalide ou illisible.")
            }
        }
    }

    fun importCollections() {
        val json = _uiState.value.importText.trim()
        if (json.isBlank()) {
            _uiState.update { it.copy(importError = context.getString(R.string.collections_import_paste_json_required)) }
            return
        }
        val validation = collectionsDataStore.validateCollectionsJson(json)
        if (!validation.valid) {
            reportImportError(validation.error ?: "JSON de collections invalide.")
            return
        }
        val imported = collectionsDataStore.importFromJson(json)
        if (imported.isEmpty()) {
            _uiState.update { it.copy(importError = context.getString(R.string.collections_import_invalid_or_empty)) }
            return
        }
        viewModelScope.launch {
            val current = _uiState.value.collections.toMutableList()
            val existingIds = current.map { it.id }.toSet()
            for (collection in imported) {
                if (collection.id in existingIds) {
                    val index = current.indexOfFirst { it.id == collection.id }
                    if (index >= 0) current[index] = collection
                } else {
                    current.add(collection)
                }
            }
            collectionsDataStore.setCollections(current)
            collectionSyncService.triggerPush()
            _uiState.update { it.copy(showImportDialog = false, importText = "", importError = null) }
        }
    }

    fun setImportMode(mode: ImportMode) {
        _uiState.update {
            it.copy(importMode = mode, importError = null, validationResult = null, validatedJson = null)
        }
    }

    fun updateImportUrl(url: String) {
        _uiState.update {
            it.copy(importUrl = url, importError = null, validationResult = null, validatedJson = null)
        }
    }

    fun validateJson(json: String) {
        val result = collectionsDataStore.validateCollectionsJson(json)
        _uiState.update {
            if (result.valid) {
                it.copy(validationResult = result, validatedJson = json, importError = null)
            } else {
                it.copy(validationResult = null, validatedJson = null, importError = result.error)
            }
        }
    }

    fun validateCurrentText() {
        validateJson(_uiState.value.importText.trim())
    }

    fun handleFileContent(content: String) {
        _uiState.update { it.copy(importText = content) }
        validateJson(content)
    }

    fun fetchUrl() {
        val url = _uiState.value.importUrl.trim()
        if (url.isBlank()) {
            _uiState.update { it.copy(importError = context.getString(R.string.web_import_enter_url)) }
            return
        }
        _uiState.update {
            it.copy(isLoadingImport = true, importError = null, validationResult = null, validatedJson = null)
        }
        viewModelScope.launch {
            try {
                val client = okhttp3.OkHttpClient.Builder()
                    .dns(com.kovelyx.tv.core.network.IPv4FirstDns())
                    .socketFactory(com.kovelyx.tv.core.network.NoDelaySocketFactory)
                    .build()
                val request = okhttp3.Request.Builder().url(url).build()
                val (httpStatus, body) = withContext(Dispatchers.IO) {
                    client.newCall(request).execute().use { response ->
                        val json = if (response.isSuccessful) {
                            response.body?.byteStream()?.use(::readCollectionsJson) ?: ""
                        } else null
                        response.code to json
                    }
                }
                if (httpStatus !in 200..299) {
                    _uiState.update {
                        it.copy(
                            isLoadingImport = false,
                            importError = context.getString(
                                R.string.collections_import_failed_fetch_http,
                                httpStatus
                            )
                        )
                    }
                    return@launch
                }
                val downloadedJson = body.orEmpty()
                _uiState.update { it.copy(importText = downloadedJson, isLoadingImport = false) }
                validateJson(downloadedJson)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingImport = false,
                        importError = context.getString(
                            R.string.collections_import_failed_fetch_url,
                            e.message ?: context.getString(R.string.error_unknown)
                        )
                    )
                }
            }
        }
    }

    fun confirmImport() {
        val json = _uiState.value.validatedJson ?: _uiState.value.importText.trim()
        if (json.isBlank()) {
            _uiState.update { it.copy(importError = context.getString(R.string.collections_import_no_data)) }
            return
        }
        val validation = collectionsDataStore.validateCollectionsJson(json)
        if (!validation.valid) {
            reportImportError(validation.error ?: "JSON de collections invalide.")
            return
        }
        val imported = collectionsDataStore.importFromJson(json)
        if (imported.isEmpty()) {
            _uiState.update { it.copy(importError = context.getString(R.string.collections_import_invalid_or_empty)) }
            return
        }
        viewModelScope.launch {
            val current = _uiState.value.collections.toMutableList()
            val existingIds = current.map { it.id }.toSet()
            for (collection in imported) {
                if (collection.id in existingIds) {
                    val index = current.indexOfFirst { it.id == collection.id }
                    if (index >= 0) current[index] = collection
                } else {
                    current.add(collection)
                }
            }
            collectionsDataStore.setCollections(current)
            collectionSyncService.triggerPush()
            _uiState.update {
                it.copy(
                    showImportDialog = false, importText = "", importError = null,
                    validationResult = null, validatedJson = null, importUrl = "",
                    importMode = ImportMode.FILE
                )
            }
        }
    }

    fun loadFromFile(context: android.content.Context) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoadingImport = true, importError = null, validationResult = null, validatedJson = null)
            }
            try {
                val content = withContext(Dispatchers.IO) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        loadFromMediaStoreDownloads(context)
                    } else {
                        loadFromDownloadsDirectory()
                    }
                }
                if (content == null) {
                    _uiState.update {
                        it.copy(
                            isLoadingImport = false,
                            importError = context.getString(R.string.collections_import_file_not_found_downloads)
                        )
                    }
                    return@launch
                }
                _uiState.update { it.copy(importText = content, isLoadingImport = false) }
                validateJson(content)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingImport = false,
                        importError = context.getString(
                            R.string.collections_import_failed_read_file,
                            e.message ?: context.getString(R.string.error_unknown)
                        )
                    )
                }
            }
        }
    }

    fun getExportJson(): String {
        return collectionsDataStore.exportToJson(_uiState.value.collections)
    }

    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.Q)
    private fun loadFromMediaStoreDownloads(context: android.content.Context): String? {
        val resolver = context.contentResolver
        val uri = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val projection = arrayOf(android.provider.MediaStore.Downloads._ID)
        val selection = "${android.provider.MediaStore.Downloads.DISPLAY_NAME} IN (?, ?)"
        val selectionArgs = arrayOf("kovelyx-collections.json", "nuvio-collections.json")
        return resolver.query(uri, projection, selection, selectionArgs, "${android.provider.MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(android.provider.MediaStore.Downloads._ID))
                val fileUri = android.content.ContentUris.withAppendedId(uri, id)
                resolver.openInputStream(fileUri)?.use(::readCollectionsJson)
            } else null
        }
    }

    private fun loadFromDownloadsDirectory(): String? {
        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS
        )
        return listOf("kovelyx-collections.json", "nuvio-collections.json")
            .firstNotNullOfOrNull { name ->
                val file = java.io.File(downloadsDir, name)
                if (file.exists()) file.inputStream().use(::readCollectionsJson) else null
            }
    }
}
