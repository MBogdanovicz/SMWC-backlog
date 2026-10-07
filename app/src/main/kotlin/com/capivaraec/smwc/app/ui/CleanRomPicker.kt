package com.capivaraec.smwc.app.ui

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.capivaraec.smwc.app.SmwcApplication
import kotlinx.coroutines.launch

/**
 * Returns a function that opens the system file picker for the clean Super Mario World ROM and
 * stores it. [onResult] gets null on success or an error message (nothing if the user cancels).
 */
@Composable
fun rememberCleanRomPicker(app: SmwcApplication, onResult: (error: String?) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = runCatching { app.cleanRom.import(uri) }
            result.exceptionOrNull()?.let { Log.w("SmwcRom", "Clean ROM rejected", it) }
            onResult(result.exceptionOrNull()?.let { it.message ?: "Could not use this file" })
        }
    }
    // ROM files have no registered MIME type, so allow any file; the content is checked instead.
    return { launcher.launch(arrayOf("*/*")) }
}

/** Shows whether the clean ROM is set, with a button to choose (or replace) it. */
@Composable
fun CleanRomRow(app: SmwcApplication, reason: String, modifier: Modifier = Modifier) {
    val available by app.cleanRom.available.collectAsStateWithLifecycle()
    var error by remember { mutableStateOf<String?>(null) }
    val pick = rememberCleanRomPicker(app) { error = it }

    Column(modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (available) "Clean ROM: Super Mario World (USA) ✓" else reason,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = pick) { Text(if (available) "Change" else "Choose ROM") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}
