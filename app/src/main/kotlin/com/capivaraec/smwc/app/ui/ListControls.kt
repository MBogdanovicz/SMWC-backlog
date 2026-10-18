package com.capivaraec.smwc.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
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

/**
 * Filters a list as you type (no search button). While it has text, the list's filters are set
 * aside; clearing it brings them back.
 */
@Composable
fun ListSearchField(query: String, onQueryChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val keyboard = LocalSoftwareKeyboardController.current
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Default.Clear, contentDescription = "Clear search") }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        textStyle = MaterialTheme.typography.bodyMedium,
        shape = RoundedCornerShape(26.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item
