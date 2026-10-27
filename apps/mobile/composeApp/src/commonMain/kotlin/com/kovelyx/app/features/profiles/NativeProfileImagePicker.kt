package com.kovelyx.app.features.profiles

import androidx.compose.runtime.Composable

/** Sélecteur local indépendant de Supabase et du Worker Cloudflare.
 * L'importation conserve une copie privée sous le stockage de l'application.
 * Une copie locale n'est PAS une synchronisation R2 entre appareils.
 */
internal expect val nativeProfileImagePickerAvailable: Boolean

@Composable
internal expect fun rememberNativeProfileImagePicker(
    onSelected: (background: Boolean, savedUrl: String) -> Unit,
    onError: (String) -> Unit,
): (background: Boolean) -> Unit
