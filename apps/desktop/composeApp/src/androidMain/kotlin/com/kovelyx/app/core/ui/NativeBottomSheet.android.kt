package com.kovelyx.app.core.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

internal actual val usesNativeKovelyxBottomSheet: Boolean = false

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal actual fun KovelyxNativeModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier,
    containerColor: Color,
    contentColor: Color,
    showDragHandle: Boolean,
    fullHeight: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) = Unit

internal actual fun dismissNativeKovelyxBottomSheet() = Unit
