package com.capivaraec.smwc.app.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow

/** Asks before unlinking a RetroAchievements set that was matched to [hackName] by name only. */
@Composable
fun UnlinkRaSetDialog(setTitle: String, hackName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Unlink this set?") },
        text = {
            Text(
                "\"${setTitle.removePrefix("~Hack~ ").trim()}\" was matched to \"$hackName\" by name only. " +
                    "If it is a different game, unlink it and it won't be matched to this hack again " +
                    "(a set verified by ROM hash still shows up). You can undo this on the hack's page.",
                overflow = TextOverflow.Ellipsis,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) { Text("Unlink", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
