package com.capivaraec.smwc.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.capivaraec.smwc.app.SmwcApplication
import com.capivaraec.smwc.app.ui.components.Badge
import com.capivaraec.smwc.app.ui.components.ControlChip
import com.capivaraec.smwc.app.ui.components.DifficultyChip
import com.capivaraec.smwc.app.ui.components.InfoChip
import com.capivaraec.smwc.app.ui.components.ListDetailLayout
import com.capivaraec.smwc.app.ui.components.RaChip
import com.capivaraec.smwc.app.ui.components.SegmentedTabs
import com.capivaraec.smwc.app.ui.components.Thumbnail
import com.capivaraec.smwc.app.ui.components.isWideLayout
import com.capivaraec.smwc.core.backlog.BacklogEntry
import com.capivaraec.smwc.core.backlog.BacklogFilter
import com.capivaraec.smwc.core.backlog.BacklogSort
import com.capivaraec.smwc.core.backlog.BacklogStatus
import com.capivaraec.smwc.core.backlog.RaSetFilter
import com.capivaraec.smwc.core.backlog.matchesSearch
import com.capivaraec.smwc.core.backlog.releaseYear
import com.capivaraec.smwc.core.model.Difficulty
import com.capivaraec.smwc.core.model.lengthDisplay
import com.capivaraec.smwc.core.ra.RaMatch
import com.capivaraec.smwc.core.ra.RaMatchKind
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The user's lists (Backlog / Playing / Completed). On wide screens the chosen hack's details
 * open beside the list ([hackPane]); otherwise [onOpenHack] opens them full screen.
 */
@Composable
fun BacklogScreen(
    app: SmwcApplication,
    sync: SyncViewModel,
    onOpenHack: (Long) -> Unit,
    hackPane: @Composable (hackId: Long, select: (Long) -> Unit) -> Unit,
) {
    val wide = isWideLayout()
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }
    val open: (Long) -> Unit = { if (wide) selected = it else onOpenHack(it) }
    if (wide) {
        ListDetailLayout(
            list = { BacklogList(app, sync, selected, open) },
            detail = selected?.let { id -> { hackPane(id) { selected = it } } },
            emptyText = "Choose a hack to see its details",
        )
    } else {
        BacklogList(app, sync, selectedId = null, onOpenHack = open)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BacklogList(app: SmwcApplication, sync: SyncViewModel, selectedId: Long?, onOpenHack: (Long) -> Unit) {
    val vm: BacklogViewModel = viewModel { BacklogViewModel(app) }
    val sorted by vm.entries.collectAsStateWithLifecycle()
    val entries = sorted.items
    val sort by vm.sort.collectAsStateWithLifecycle()
    val raVm: BacklogRaViewModel = viewModel { BacklogRaViewModel(app) }
    val raMatches by raVm.matches.collectAsStateWithLifecycle()
    val raError by raVm.error.collectAsStateWithLifecycle()
    val syncState by sync.state.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val raConfigured by vm.raConfigured.collectAsStateWithLifecycle()
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val status = BacklogStatus.entries[tab]
    // Hacks added by name have no details page; tapping them edits their notes instead.
    var notesFor by rememberSaveable { mutableStateOf<Long?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val searching = query.isNotBlank()
    // The RA filter only applies while an account is set up. A search sets the filters aside.
    val activeFilter = if (raConfigured) filter else filter.copy(raSet = RaSetFilter.ANY)
    val filtered = if (searching) {
        entries.filter { it.matchesSearch(query) }
    } else {
        entries.filter { activeFilter.matches(it, hasRaSet = it.hackId in raMatches) }
    }
    val shown = filtered.filter { it.status == status }

    entries.firstOrNull { it.hackId == notesFor }?.let { entry ->
        NotesDialog(entry.name, entry.notes, onSave = { vm.setNotes(entry.hackId, it) }, onDismiss = { notesFor = null })
    }
    if (showFilters) {
        FilterSheet(filter = filter, entries = entries, raConfigured = raConfigured, onChange = vm::setFilter, onDismiss = { showFilters = false })
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("My hacks", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                SyncStatus(syncing = syncState.syncing, failed = syncState.lastError != null)
            }
            syncState.lastError?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { sync.syncNow(manual = true) }) { Text("Retry") }
                }
            }
            raError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            ListSearchField(query, onQueryChange = { query = it }, placeholder = "Search by name or author")
            SegmentedTabs(
                labels = BacklogStatus.entries.map { s -> "${s.label} (${filtered.count { it.status == s }})" },
                selected = tab,
                onSelect = { tab = it },
            )
            // Wraps on narrow screens, since the sort label can be long.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SortMenu(sort, BacklogSort.entries, BacklogSort::label, onSelect = vm::setSort)
                ControlChip(
                    if (activeFilter.isActive) "Filters · ${activeFilter.activeCount}" else "Filters",
                    onClick = { showFilters = true },
                    icon = Icons.Outlined.FilterAlt,
                    active = activeFilter.isActive && !searching,
                    enabled = !searching,
                )
                if (activeFilter.isActive && !searching) TextButton(onClick = { vm.setFilter(BacklogFilter()) }) { Text("Clear") }
            }
        }
        val listState = rememberLazyListState()
        ScrollToTopOnChange(listState, key = "${sorted.sort}|${activeFilter.hashCode()}|${query.trim()}")
        // Pull down to sync with Google Drive.
        PullToRefreshBox(isRefreshing = syncState.syncing, onRefresh = { sync.syncNow(manual = true) }, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (shown.isEmpty()) {
                    item {
                        EmptyState(
                            when {
                                searching -> "No hacks match “${query.trim()}”."
                                activeFilter.isActive && entries.any { it.status == status } -> "No hacks match the filters."
                                status == BacklogStatus.BACKLOG -> "Your backlog is empty. Add hacks from Search."
                                status == BacklogStatus.PLAYING -> "Not playing anything. Start a hack from your backlog."
                                else -> "No completed hacks yet."
                            },
                        )
                    }
                }
                items(shown, key = { it.hackId }) { entry ->
                    // The RetroAchievements set is not shown for completed hacks.
                    val match = raMatches[entry.hackId]?.takeIf { entry.status != BacklogStatus.COMPLETED }
                    BacklogCard(
                        entry = entry,
                        raMatch = match,
                        selected = entry.hackId == selectedId,
                        onClick = { if (entry.custom) notesFor = entry.hackId else onOpenHack(entry.hackId) },
                        onStatus = { vm.setStatus(entry.hackId, it) },
                        onNotes = { notesFor = entry.hackId },
                        onRemove = { vm.remove(entry.hackId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncStatus(syncing: Boolean, failed: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        when {
            syncing -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            failed -> Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
            else -> Icon(Icons.Outlined.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
        }
        Text(
            when {
                syncing -> "Syncing…"
                failed -> "Not synced"
                else -> "Synced"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyState(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 8.dp),
    )
}

/** One hack in the lists: screenshot, name, chips, notes, and the actions for its list. */
@Composable
private fun BacklogCard(
    entry: BacklogEntry,
    raMatch: RaMatch?,
    selected: Boolean,
    onClick: () -> Unit,
    onStatus: (BacklogStatus) -> Unit,
    onNotes: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Thumbnail(entry.thumbnailUrl, Modifier.size(width = 72.dp, height = 64.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (entry.hasNewerVersion) Badge("New version")
                    Text(entry.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (entry.custom) {
                        Text(
                            "Not on SMW Central",
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        DifficultyChip(entry.difficulty)
                        entry.exits?.let { lengthDisplay(it.toString())?.let { text -> InfoChip(text) } }
                        entry.releaseYear()?.let { InfoChip(it.toString()) }
                        raMatch?.let { RaChip(it.chipText()) }
                    }
                    if (entry.status == BacklogStatus.COMPLETED) {
                        entry.completedAt?.let {
                            Text(
                                "Completed ${completedDate.format(Instant.ofEpochMilli(it))}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                }
                // Completed hacks have no next step, so their only actions (⋯) sit in the corner
                // instead of an otherwise empty row of buttons.
                if (entry.status == BacklogStatus.COMPLETED) MoreActions(entry, onStatus, onNotes, onRemove)
            }
            if (entry.notes.isNotEmpty()) {
                Text(
                    "“${entry.notes}”",
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (entry.status != BacklogStatus.COMPLETED) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (entry.status == BacklogStatus.BACKLOG) {
                        CardAction("Play", Icons.Filled.PlayArrow, primary = true, onClick = { onStatus(BacklogStatus.PLAYING) }, modifier = Modifier.weight(1f))
                        CardAction("Done", Icons.Filled.Check, primary = false, onClick = { onStatus(BacklogStatus.COMPLETED) }, modifier = Modifier.weight(1f))
                    } else {
                        CardAction("Done", Icons.Filled.Check, primary = true, onClick = { onStatus(BacklogStatus.COMPLETED) }, modifier = Modifier.weight(1f))
                    }
                    MoreActions(entry, onStatus, onNotes, onRemove)
                }
            }
        }
    }
}

/** e.g. "2026-10-06", in the device's time zone. */
private val completedDate = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneId.systemDefault())

/** "38 · 400 pts", marked when the set was matched by name only (not by ROM hash). */
private fun RaMatch.chipText(): String =
    "${game.achievements} · ${game.points} pts" + if (kind == RaMatchKind.NAME) " · by name" else ""

@Composable
private fun CardAction(label: String, icon: ImageVector, primary: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = if (primary) {
        ButtonDefaults.buttonColors()
    } else {
        ButtonDefaults.outlinedButtonColors()
    }
    val content: @Composable RowScope.() -> Unit = {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label)
    }
    // Less side padding than Material's default, so the labels fit in narrow panes.
    val padding = PaddingValues(horizontal = 10.dp)
    if (primary) {
        Button(onClick = onClick, colors = colors, shape = RoundedCornerShape(10.dp), contentPadding = padding, modifier = modifier.height(40.dp), content = content)
    } else {
        OutlinedButton(onClick = onClick, colors = colors, shape = RoundedCornerShape(10.dp), contentPadding = padding, modifier = modifier.height(40.dp), content = content)
    }
}

/** "⋯": moving between lists, notes for hacks added by name, and removing. */
@Composable
private fun MoreActions(entry: BacklogEntry, onStatus: (BacklogStatus) -> Unit, onNotes: () -> Unit, onRemove: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreHoriz, contentDescription = "More actions") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            BacklogStatus.entries.filter { it != entry.status }.forEach { target ->
                DropdownMenuItem(
                    text = { Text("Move to ${target.label}") },
                    onClick = {
                        open = false
                        onStatus(target)
                    },
                )
            }
            if (entry.custom) {
                DropdownMenuItem(text = { Text("Notes") }, onClick = { open = false; onNotes() })
            }
            DropdownMenuItem(
                text = { Text("Remove", color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = {
                    open = false
                    onRemove()
                },
            )
        }
    }
}

/** Filter choices; options come from the hacks in the lists (difficulties and years present). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(
    filter: BacklogFilter,
    entries: List<BacklogEntry>,
    raConfigured: Boolean,
    onChange: (BacklogFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    val difficulties = entries.mapNotNull { it.difficulty }.distinct()
        .sortedWith(compareBy({ !it.startsWith("diff_") }, { it }))
    val years = entries.mapNotNull { it.releaseYear() }.distinct().sortedDescending()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Filters", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { onChange(BacklogFilter()) }, enabled = filter.isActive) { Text("Clear all") }
            }

            FilterSection("Difficulty") {
                difficulties.forEach { id ->
                    FilterChip(
                        selected = id in filter.difficulties,
                        onClick = { onChange(filter.copy(difficulties = filter.difficulties.toggle(id))) },
                        label = { Text(Difficulty.displayName(id) ?: id) },
                    )
                }
            }

            FilterSection("Rating") {
                (listOf<Double?>(null) + BacklogFilter.RATING_STEPS).forEach { min ->
                    FilterChip(
                        selected = filter.minRating == min,
                        onClick = { onChange(filter.copy(minRating = min)) },
                        label = { Text(min?.let { "★ %.1f+".format(it) } ?: "Any") },
                    )
                }
            }

            if (raConfigured) {
                FilterSection("RetroAchievements") {
                    RaSetFilter.entries.forEach { option ->
                        FilterChip(
                            selected = filter.raSet == option,
                            onClick = { onChange(filter.copy(raSet = option)) },
                            label = { Text(option.label) },
                        )
                    }
                }
            }

            FilterSection("Release year") {
                years.forEach { year ->
                    FilterChip(
                        selected = year in filter.releaseYears,
                        onClick = { onChange(filter.copy(releaseYears = filter.releaseYears.toggle(year))) },
                        label = { Text(year.toString()) },
                    )
                }
            }
        }
    }
}

/** Notes of a hack added by name (it has no details page to edit them in). */
@Composable
private fun NotesDialog(name: String, notes: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(notes) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Notes") },
                placeholder = { Text("Where you stopped, tips, why you gave up…") },
                minLines = 3,
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(text)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
