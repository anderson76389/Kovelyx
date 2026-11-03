package com.nuvio.app.features.profiles

import androidx.compose.runtime.Composable

/** Sélecteur local indépendant de Supabase et du Worker Cloudflare.
 * L'importation conserve une copie privée sous le stockage de l'application.
 * La synchronisation privée est traitée ensuite par le client Cloudflare.
 */
internal expect val nativeProfileImagePickerAvailable: Boolean

@Composable
internal expect fun rememberNativeProfileImagePicker(
    onSelected: (background: Boolean, savedUrl: String) -> Unit,
    onError: (String) -> Unit,
): (background: Boolean) -> Unit
