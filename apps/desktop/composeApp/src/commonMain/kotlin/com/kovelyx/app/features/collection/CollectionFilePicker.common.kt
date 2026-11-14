package com.kovelyx.app.features.collection

import androidx.compose.runtime.Composable

internal const val MAX_COLLECTION_FILE_BYTES = 16 * 1024 * 1024
internal expect val collectionFilePickerAvailable: Boolean

@Composable
internal expect fun rememberCollectionFilePicker(
    onSelected: (String) -> Unit,
    onError: (String) -> Unit,
): () -> Unit
