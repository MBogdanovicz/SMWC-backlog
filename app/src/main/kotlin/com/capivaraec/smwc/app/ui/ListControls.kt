package com.capivaraec.smwc.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.capivaraec.smwc.app.ui.components.ControlChip

/** The current order as a pill; tapping it opens a menu of every order. Shared by the list screens. */
@Composable
fun <T> SortMenu(current: T, options: List<T>, label: (T) -> String, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        ControlChip(label(current), onClick = { open = true }, icon = Icons.AutoMirrored.Filled.Sort)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option)) },
                    leadingIcon = { if (option == current) Icon(Icons.Default.Check, contentDescription = "Selected") },
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

/** A titled group of filter chips in a filter sheet. */
@Composable
fun FilterSection(title: String, chips: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { chips() }
    }
}

/**
 * Scrolls [listState] to the top when [key] (e.g. the sort order and filter) changes; otherwise
 * a list keeps the item that was in view and looks unsorted. The last key is saved, so coming
 * back to the screen (e.g. from a hack's details) keeps the position.
 */
@Composable
fun ScrollToTopOnChange(listState: LazyListState, key: String) {
    var shownKey by rememberSaveable { mutableStateOf(key) }
    LaunchedEffect(key) {
        if (key != shownKey) {
            shownKey = key
            listState.scrollToItem(0)
        }
    }
}

fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item
