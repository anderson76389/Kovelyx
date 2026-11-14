package com.kovelyx.app.features.collection

import androidx.compose.runtime.Composable

internal actual val collectionFilePickerAvailable = false
@Composable
internal actual fun rememberCollectionFilePicker(onSelected: (String) -> Unit, onError: (String) -> Unit): () -> Unit = {}
