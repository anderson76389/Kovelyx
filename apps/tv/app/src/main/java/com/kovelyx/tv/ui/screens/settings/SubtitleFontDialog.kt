package com.kovelyx.tv.ui.screens.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kovelyx.tv.data.local.SubtitleFontStore

/** Remote-friendly picker; OpenDocument uses the TV's available document provider. */
@Composable
internal fun SubtitleFontDialog(
    selected: String,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) { SubtitleFontStore.import(context, uri) } }
                .onSuccess(onSelected)
                .onFailure { error ->
                    Toast.makeText(context, error.message ?: "Police invalide", Toast.LENGTH_LONG).show()
                }
        }
    }
    val options = buildList {
        add(SettingsPickerOption(SubtitleFontStore.DEFAULT, "Par défaut"))
        add(SettingsPickerOption(SubtitleFontStore.SANS_SERIF, "Sans empattement"))
        add(SettingsPickerOption(SubtitleFontStore.SERIF, "Serif"))
        add(SettingsPickerOption(SubtitleFontStore.MONOSPACE, "Monospace"))
        if (SubtitleFontStore.file(context, selected) != null) {
            add(SettingsPickerOption(selected, "Police importée"))
        }
        add(SettingsPickerOption("__import__", "Importer une police", ".ttf / .otf • 10 Mo maximum • sur cet appareil"))
    }
    SettingsSingleChoiceDialog(
        title = "Police des sous-titres",
        options = options,
        selectedValue = selected,
        onOptionSelected = { choice ->
            if (choice == "__import__") picker.launch(arrayOf("*/*"))
            else onSelected(choice)
        },
        onDismiss = onDismiss,
        width = 480.dp,
        maxHeight = 540.dp
    )
}
