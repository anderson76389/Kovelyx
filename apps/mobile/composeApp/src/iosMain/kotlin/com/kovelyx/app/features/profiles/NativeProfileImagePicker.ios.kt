package com.kovelyx.app.features.profiles

import androidx.compose.runtime.Composable

// Le sélecteur Android ne doit pas rompre la compilation du client iOS.
internal actual val nativeProfileImagePickerAvailable: Boolean = false

@Composable
internal actual fun rememberNativeProfileImagePicker(
    onSelected: (background: Boolean, savedUrl: String) -> Unit,
    onError: (String) -> Unit,
): (background: Boolean) -> Unit = { _ ->
    onError("L'importation de photos depuis la galerie n'est pas encore disponible sur iOS.")
}
