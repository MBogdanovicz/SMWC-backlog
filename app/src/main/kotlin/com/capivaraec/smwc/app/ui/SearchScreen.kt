package com.capivaraec.smwc.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.capivaraec.smwc.app.SmwcApplication
import com.capivaraec.smwc.app.ui.components.AppMark
import com.capivaraec.smwc.app.ui.components.DifficultyChip
import com.capivaraec.smwc.app.ui.components.InfoChip
import com.capivaraec.smwc.app.ui.components.ListDetailLayout
import com.capivaraec.smwc.app.ui.components.LocalSnackbar
import com.capivaraec.smwc.app.ui.components.SectionLabel
import com.capivaraec.smwc.app.ui.components.Thumbnail
import com.capivaraec.smwc.app.ui.components.isWideLayout
import com.capivaraec.smwc.core.api.HackSearch
import com.capivaraec.smwc.core.backlog.BacklogStatus
import com.capivaraec.smwc.core.model.HackSummary
import com.capivaraec.smwc.core.model.lengthDisplay

/**
 * Search on SMW Central. On wide screens the chosen hack's details open beside the results
 * ([hackPane]); otherwise [onOpenHack] opens them full screen.
 */
@Composable
fun SearchScreen(
    app: SmwcApplication,
    onOpenHack: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    hackPane: @Composable (hackId: Long, select: (Long) -> Unit) -> Unit,
) {
    val vm: SearchViewModel = viewModel { SearchViewModel(HackSearch(app.smwc), app.backlog) }
    val wide = isWideLayout()
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }
    val open: (Long) -> Unit = { if (wide) selected = it else onOpenHack(it) }

    if (wide) {
        ListDetailLayout(
            // The side rail already shows the app mark and Settings.
            list = { SearchList(vm, selected, open, onOpenSettings = null) },
            detail = selected?.let { id -> { hackPane(id) { selected = it } } },
            emptyText = "Choose a hack to see its details",
        )
    } else {
        SearchList(vm, selectedId = null, onOpenHack = open, onOpenSettings = onOpenSettings)
    }
}

@Composable
private fun SearchList(vm: SearchViewModel, selectedId: Long?, onOpenHack: (Long) -> Unit, onOpenSettings: (() -> Unit)?) {
    val state by vm.state.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    val snackbar = LocalSnackbar.current
    // Show first, then clear: clearing changes the key and would cancel this effect (and the snackbar).
    LaunchedEffect(state.addedMessage) {
        state.addedMessage?.let {
            snackbar.showSnackbar(it, withDismissAction = true)
            vm.messageShown()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (onOpenSettings != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppMark(size = 32.dp)
                    Text(
                        "SMWC Backlog",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f).padding(start = 10.dp),
                    )
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") }
                }
            }
            TextField(
                value = state.query,
                onValueChange = vm::onQueryChange,
                placeholder = { Text("Search SMW Central", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    // Clears the text and goes back to the latest hacks.
                    if (state.query.isNotEmpty() || state.searchedQuery.isNotEmpty()) {
                        IconButton(onClick = { keyboard?.hide(); vm.clear() }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); vm.search() }),
                shape = RoundedCornerShape(26.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
            )
            SectionLabel(
                when {
                    state.searchedQuery.isEmpty() -> "Latest hacks"
                    state.approximate -> "Similar names"
                    else -> "Results"
                },
            )
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            state.error?.let { error ->
                item { Text(error, color = MaterialTheme.colorScheme.error) }
            }
            if (state.approximate && state.results.isNotEmpty()) {
                item {
                    Text(
                        "No exact match for \"${state.searchedQuery}\"; showing similar names.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.results, key = { it.id }) { hack ->
                HackCard(hack, selected = hack.id == selectedId, onClick = { onOpenHack(hack.id) })
            }
            item {
                Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                    when {
                        state.loading -> CircularProgressIndicator()
                        state.hasNext -> TextButton(onClick = vm::loadMore) { Text("Load more") }
                        state.results.isEmpty() && state.error == null -> Text("No hacks found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            // For hacks that never made it to SMW Central (e.g. only in the author's files).
            if (state.query.isNotBlank() && !state.loading) {
                item { AddByName(name = state.query.trim(), onAdd = vm::addByName) }
            }
        }
    }
}

/** A search result: screenshot, name, authors and difficulty/exits/rating chips. */
@Composable
fun HackCard(hack: HackSummary, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumbnail(hack.thumbnailUrl, Modifier.size(width = 96.dp, height = 84.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(hack.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (hack.authors.isNotEmpty()) {
                    Text(
                        "by ${hack.authors.joinToString()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DifficultyChip(hack.difficulty)
                    lengthDisplay(hack.length)?.let { InfoChip(it) }
                    hack.rating?.let { InfoChip("★ %.1f".format(it)) }
                }
            }
        }
    }
}

@Composable
private fun AddByName(name: String, onAdd: (BacklogStatus) -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Not on SMW Central?", style = MaterialTheme.typography.titleSmall)
            Text(
                "Add \"$name\" by name (for hacks only in the author's files):",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BacklogStatus.entries.forEach { status ->
                    OutlinedButton(shape = MaterialTheme.shapes.medium, onClick = { onAdd(status) }, modifier = Modifier.height(40.dp)) { Text(status.label) }
                }
            }
        }
    }
}
