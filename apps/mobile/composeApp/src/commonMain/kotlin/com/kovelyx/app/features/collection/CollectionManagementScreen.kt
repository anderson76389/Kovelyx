package com.kovelyx.app.features.collection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Checkbox
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateDpAsState
import com.kovelyx.app.core.ui.DialogButton
import com.kovelyx.app.core.ui.DialogButtons
import com.kovelyx.app.core.ui.DialogButtonStyle
import com.kovelyx.app.core.ui.DialogSurface
import com.kovelyx.app.core.ui.KovelyxPrimaryButton
import com.kovelyx.app.core.ui.KovelyxScreen
import com.kovelyx.app.core.ui.KovelyxScreenHeader
import com.kovelyx.app.core.ui.KovelyxSectionLabel
import com.kovelyx.app.core.ui.KovelyxStatusModal
import com.kovelyx.app.core.ui.KovelyxSurfaceCard
import com.kovelyx.app.core.ui.withDuplicateSafeLazyKeys
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionManagementScreen(
    onBack: () -> Unit,
    onNavigateToEditor: (String?) -> Unit,
) {
    val collections by CollectionRepository.collections.collectAsState()
    val cloudStatus by com.kovelyx.app.core.cloud.CloudflareCatalogueSync.status.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    var showImportDialog by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf<String?>(null) }
    var mergeImport by remember { mutableStateOf(true) }
    var fileSummary by remember { mutableStateOf<String?>(null) }
    val pickCollectionFile = rememberCollectionFilePicker(
        onSelected = { text ->
            importText = text
            val result = CollectionRepository.validateJson(text)
            importError = result.error
            fileSummary = if (result.valid) "${result.collectionCount} catégories · ${result.folderCount} dossiers" else null
            showImportDialog = true
        },
        onError = { importError = it; showImportDialog = true },
    )
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }
    var showCopyError by remember { mutableStateOf(false) }

    KovelyxScreen {
        stickyHeader {
            KovelyxScreenHeader(
                title = stringResource(Res.string.collections_header),
                onBack = onBack,
            ) {
                IconButton(onClick = {
                    val json = CollectionRepository.exportToJson()
                    showCopyError = runCatching {
                        clipboardManager.setText(AnnotatedString(json))
                    }.isFailure
                }) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = stringResource(Res.string.collections_copy_json),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { showImportDialog = true }) {
                    Icon(
                        imageVector = Icons.Rounded.ContentPaste,
                        contentDescription = stringResource(Res.string.collections_import),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            KovelyxSurfaceCard {
                Text(
                    text = stringResource(
                        Res.string.collections_count_summary,
                        collections.size,
                        collections.sumOf { it.folders.size },
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (com.kovelyx.app.core.cloud.CloudflareAccountClient.currentSession != null) {
            item {
                KovelyxSurfaceCard {
                    val phase = cloudStatus.takeIf { it.profileId == com.kovelyx.app.features.profiles.ProfileRepository.activeProfileId }?.phase
                    Text(stringResource(when (phase) {
                        com.kovelyx.app.core.cloud.CatalogueSyncPhase.TRANSFERRING -> Res.string.catalogue_sync_transferring
                        com.kovelyx.app.core.cloud.CatalogueSyncPhase.DONE -> Res.string.catalogue_sync_done
                        com.kovelyx.app.core.cloud.CatalogueSyncPhase.FAILED -> Res.string.catalogue_sync_failed
                        else -> Res.string.catalogue_sync_pending
                    }), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    KovelyxPrimaryButton(text = stringResource(Res.string.catalogue_sync_now),
                        onClick = { com.kovelyx.app.core.cloud.CloudflareCatalogueSync.requestSync() })
                }
            }
        }

        item {
            KovelyxPrimaryButton(
                text = stringResource(Res.string.collections_new),
                onClick = { onNavigateToEditor(null) },
            )
        }

        if (collections.isNotEmpty()) {
            item { KovelyxSectionLabel(text = stringResource(Res.string.collections_your_collections)) }
        }

        if (collections.isNotEmpty()) {
            item {
                CollectionReorderableList(
                    collections = collections,
                    onEdit = { onNavigateToEditor(it) },
                    onDelete = { showDeleteConfirm = it },
                )
            }
        }

        if (collections.isEmpty()) {
            item {
                KovelyxSurfaceCard(
                    modifier = Modifier.padding(top = 24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(Res.string.collections_empty_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(Res.string.collections_empty_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showImportDialog) {
        ImportDialog(
            importText = importText,
            importError = importError,
            onTextChange = {
                importText = it
                importError = null
                fileSummary = null
            },
            onConfirm = {
                val result = CollectionRepository.validateJson(importText)
                if (result.valid) {
                    val imported = runCatching { CollectionRepository.importFromJson(importText, merge = mergeImport).getOrThrow() }
                    if (imported.isFailure) {
                        importError = "Import impossible. Vérifie le fichier et réessaie."
                        return@ImportDialog
                    }
                    showImportDialog = false
                    importText = ""
                    importError = null
                } else {
                    importError = result.error
                }
            },
            onPickFile = if (collectionFilePickerAvailable) pickCollectionFile else null,
            fileSummary = fileSummary,
            mergeImport = mergeImport,
            onMergeChange = { mergeImport = it },
            onDismiss = {
                showImportDialog = false
                importText = ""
                importError = null
            },
        )
    }

    KovelyxStatusModal(
        title = stringResource(Res.string.collections_copy_json),
        message = stringResource(Res.string.collections_copy_json_error),
        isVisible = showCopyError,
        onConfirm = { showCopyError = false },
        onDismiss = { showCopyError = false },
    )

    val deleteId = showDeleteConfirm
    val deleteCollection = deleteId?.let { id -> collections.find { it.id == id } }
    KovelyxStatusModal(
        title = stringResource(Res.string.collections_delete_title),
        message = stringResource(Res.string.collections_delete_message, deleteCollection?.title.orEmpty()),
        isVisible = deleteId != null,
        destructive = true,
        confirmText = stringResource(Res.string.action_delete),
        dismissText = stringResource(Res.string.action_cancel),
        onConfirm = {
            if (deleteId != null) {
                CollectionRepository.removeCollection(deleteId)
            }
            showDeleteConfirm = null
        },
        onDismiss = { showDeleteConfirm = null },
    )
}

@Composable
private fun CollectionReorderableList(
    collections: List<Collection>,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val lazyListState = rememberLazyListState()
    val keyedCollections = remember(collections) {
        collections.withDuplicateSafeLazyKeys { collection -> collection.id }
    }
    val reorderableLazyListState = rememberReorderableLazyListState(
        lazyListState = lazyListState,
    ) { from, to ->
        CollectionRepository.moveByIndex(from.index, to.index)
        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 720.dp),
        state = lazyListState,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(keyedCollections, key = { _, collection -> collection.lazyKey }) { _, keyedCollection ->
            val collection = keyedCollection.value
            ReorderableItem(reorderableLazyListState, key = keyedCollection.lazyKey) { isDragging ->
                val elevation by animateDpAsState(if (isDragging) 4.dp else 0.dp)

                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.extraLarge,
                    shadowElevation = elevation,
                ) {
                    CollectionListItem(
                        collection = collection,
                        onEdit = { onEdit(collection.id) },
                        onDelete = { onDelete(collection.id) },
                        dragHandleScope = this@ReorderableItem,
                    )
                }
            }
        }
    }
}

@Composable
private fun CollectionListItem(
    collection: Collection,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    dragHandleScope: ReorderableCollectionItemScope,
) {
    val hapticFeedback = LocalHapticFeedback.current

    KovelyxSurfaceCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val summary = buildString {
                    append(stringResource(Res.string.collections_folder_count, collection.folders.size))
                    if (collection.pinToTop) {
                        append(" · ")
                        append(stringResource(Res.string.collections_pinned))
                    }
                }
                Text(
                    text = collection.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                modifier = with(dragHandleScope) {
                    Modifier.draggableHandle(
                        onDragStarted = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDragStopped = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                    ).size(36.dp)
                },
                onClick = {},
            ) {
                Icon(
                    imageVector = Icons.Rounded.Menu,
                    contentDescription = stringResource(Res.string.action_reorder),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = stringResource(Res.string.action_edit),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = stringResource(Res.string.action_delete),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ImportDialog(
    importText: String,
    importError: String?,
    onTextChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onPickFile: (() -> Unit)?,
    fileSummary: String?,
    mergeImport: Boolean,
    onMergeChange: (Boolean) -> Unit,
) {
    DialogSurface(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.collections_import_header),
        message = stringResource(Res.string.collections_import_paste_description),
    ) {
        if (onPickFile != null) {
            KovelyxPrimaryButton(text = "Choisir un fichier JSON", onClick = onPickFile)
            Spacer(Modifier.height(12.dp))
        }
        fileSummary?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = mergeImport, onCheckedChange = onMergeChange)
            Text("Conserver les autres collections (mêmes catégories remplacées)", style = MaterialTheme.typography.bodyMedium)
        }
        if (!mergeImport) Text("L’import remplacera toutes les collections de ce profil.", color = MaterialTheme.colorScheme.error)
        Text("Les catalogues d’extensions nécessitent une extension compatible installée. MDBList ne les fournit pas.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        if (fileSummary == null) OutlinedTextField(
            value = importText,
            onValueChange = onTextChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            placeholder = {
                Text(
                    stringResource(Res.string.collections_import_json_placeholder),
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            isError = importError != null,
            supportingText = importError?.let {
                { Text(it, color = MaterialTheme.colorScheme.error) }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onConfirm() }),
            maxLines = 10,
            shape = RoundedCornerShape(14.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.outline,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
        )
        DialogButtons {
            DialogButton(
                text = stringResource(Res.string.action_cancel),
                onClick = onDismiss,
            )
            DialogButton(
                text = stringResource(Res.string.action_import),
                onClick = onConfirm,
                enabled = importText.isNotBlank(),
                style = DialogButtonStyle.Primary,
            )
        }
    }
}
