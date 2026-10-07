package com.capivaraec.smwc.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.capivaraec.smwc.app.SmwcApplication
import com.capivaraec.smwc.app.ui.components.ControlChip
import com.capivaraec.smwc.app.ui.components.ListDetailLayout
import com.capivaraec.smwc.app.ui.components.ProgressBar
import com.capivaraec.smwc.app.ui.components.isWideLayout
import com.capivaraec.smwc.core.ra.AchievementFilter
import com.capivaraec.smwc.core.ra.AchievementSort
import com.capivaraec.smwc.core.ra.ListFilter
import com.capivaraec.smwc.core.ra.MasteredFilter
import com.capivaraec.smwc.core.ra.RaAchievement
import com.capivaraec.smwc.core.ra.RaMatchKind
import com.capivaraec.smwc.core.ra.RaProgress
import com.capivaraec.smwc.core.ra.mastered

private fun RaProgress.summary(): String =
    "$earned/${achievements.size} achievements · $earnedPoints/${achievements.sumOf { it.points }} pts"

/** Names equal once case, punctuation and RA's "~Hack~" tag are ignored ("~ Sure Shot ~" = "Sure Shot ~"). */
private fun sameName(a: String, b: String): Boolean {
    fun key(s: String) = s.lowercase().replace("~hack~", "").filter { it.isLetterOrDigit() }
    return key(a) == key(b)
}

private fun RaProgress.fraction(): Float = earned.toFloat() / achievements.size.coerceAtLeast(1)

/**
 * The user's hacks that have a RetroAchievements set, with their progress. On wide screens the
 * chosen set opens beside the list ([setPane]); otherwise [onOpenSet] opens it full screen.
 */
@Composable
fun AchievementsScreen(
    app: SmwcApplication,
    onOpenSet: (gameId: Long, hackId: Long) -> Unit,
    onOpenSettings: () -> Unit,
    setPane: @Composable (gameId: Long, hackId: Long?) -> Unit,
) {
    val wide = isWideLayout()
    var selectedGame by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedHack by rememberSaveable { mutableStateOf<Long?>(null) }
    val open: (Long, Long) -> Unit = { gameId, hackId ->
        if (wide) {
            selectedGame = gameId
            selectedHack = hackId
        } else {
            onOpenSet(gameId, hackId)
        }
    }
    if (wide) {
        ListDetailLayout(
            list = { AchievementsList(app, selectedGame, open, onOpenSettings) },
            detail = selectedGame?.let { gameId -> { setPane(gameId, selectedHack) } },
            emptyText = "Choose a set to see its achievements",
        )
    } else {
        AchievementsList(app, selectedGameId = null, onOpenSet = open, onOpenSettings = onOpenSettings)
    }
}

@Composable
private fun AchievementsList(
    app: SmwcApplication,
    selectedGameId: Long?,
    onOpenSet: (gameId: Long, hackId: Long) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val vm: AchievementsViewModel = viewModel { AchievementsViewModel(app) }
    val state by vm.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val credentials by app.raCredentials.credentials.collectAsStateWithLifecycle()
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    ScrollToTopOnChange(listState, key = "${state.sort}|${state.filter}")

    if (showFilters) {
        AchievementFilterSheet(state.filter, onChange = vm::setFilter, onDismiss = { showFilters = false })
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Achievements", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                credentials?.let { Text(it.username, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (state.configured && state.totalRows > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("${state.setCount}", "sets", Modifier.weight(1f))
                    StatTile("${state.masteredCount}", "mastered", Modifier.weight(1f), highlight = true)
                    StatTile("%,d".format(state.earnedPoints), "points", Modifier.weight(1f))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SortMenu(state.sort, AchievementSort.entries, AchievementSort::label, onSelect = vm::setSort)
                    ControlChip(
                        if (state.filter.isActive) "Filters · ${state.filter.activeCount}" else "Filters",
                        onClick = { showFilters = true },
                        icon = Icons.Outlined.FilterAlt,
                        active = state.filter.isActive,
                    )
                    if (state.filter.isActive) TextButton(onClick = { vm.setFilter(AchievementFilter()) }) { Text("Clear") }
                }
            }
        }
        when {
            !state.configured -> ConnectCard(onOpenSettings)
            state.error != null -> Text(state.error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.totalRows == 0 -> Text(
                "None of your hacks has a RetroAchievements set yet.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            // Pull down to fetch the latest progress from RetroAchievements.
            else -> PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (state.rows.isEmpty()) {
                        item { Text("No hacks match the filters.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp)) }
                    }
                    items(state.rows.filter { it.suggestCompleting }, key = { "suggest-${it.entry.hackId}" }) { row ->
                        CompletionSuggestion(
                            name = row.entry.name,
                            iconUrl = row.match.game.iconUrl,
                            list = row.entry.status.label,
                            onComplete = { vm.markCompleted(row.entry.hackId) },
                            onDismiss = { vm.dismissCompletionSuggestion(row.entry.hackId) },
                        )
                    }
                    items(state.rows, key = { it.entry.hackId }) { row ->
                        SetRow(row, selected = row.match.game.id == selectedGameId, onClick = { onOpenSet(row.match.game.id, row.entry.hackId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ConnectCard(onOpenSettings: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Text("Connect RetroAchievements", style = MaterialTheme.typography.titleMedium)
            Text(
                "See which of your hacks have achievement sets and how far you got. Add your RetroAchievements user name and Web API key in Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(shape = MaterialTheme.shapes.medium, onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Open Settings", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** A set: icon (with a star when mastered), name, list, progress bar and counts. */
@Composable
private fun SetRow(row: AchievementRow, selected: Boolean, onClick: () -> Unit) {
    val progress = row.progress
    val mastered = progress?.mastered == true
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                AsyncImage(
                    model = row.match.game.iconUrl,
                    contentDescription = null,
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                )
                if (mastered) {
                    Box(
                        Modifier.align(Alignment.BottomEnd).offset(6.dp, 6.dp).size(22.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .border(2.dp, MaterialTheme.colorScheme.surfaceContainer, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Star, contentDescription = "Mastered", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(12.dp)) }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(row.entry.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(
                        if (mastered) "${row.entry.status.label} · Mastered" else row.entry.status.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when {
                    progress != null -> {
                        ProgressBar(progress.fraction(), if (mastered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
                        Text(progress.summary(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    row.progressError != null -> Text(row.progressError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    else -> Text(
                        "${row.match.game.achievements} achievements · ${row.match.game.points} pts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // One set can cover several hacks (e.g. "Cube's Kaizo World 1-4"): name it when it differs.
                val setTitle = row.match.game.title.removePrefix("~Hack~ ").trim()
                val note = listOfNotNull(
                    setTitle.takeIf { !sameName(it, row.entry.name) }?.let { "Set: $it" },
                    "matched by name".takeIf { row.match.kind == RaMatchKind.NAME },
                ).joinToString(" · ").replaceFirstChar { it.uppercase() }
                if (note.isNotEmpty()) {
                    Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/**
 * Every achievement of one set, unlocked first. [onBack] null when shown as a pane beside the
 * list (no back button then).
 */
@Composable
fun RaSetScreen(app: SmwcApplication, gameId: Long, hackId: Long?, onOpenHack: (() -> Unit)?, onBack: (() -> Unit)?) {
    val vm: RaSetViewModel = viewModel(key = "ra-$gameId-$hackId") { RaSetViewModel(app, gameId, hackId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val suggestCompleting by vm.suggestCompleting.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current

    Column(Modifier.fillMaxSize()) {
        onBack?.let {
            IconButton(onClick = it, modifier = Modifier.statusBarsPadding().padding(start = 4.dp, top = 4.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }
        when {
            state.loading && state.progress == null ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.progress == null -> Column(Modifier.padding(16.dp)) {
                Text(state.error ?: "Could not load the set", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { vm.load() }) { Text("Try again") }
            }
            else -> {
                val progress = state.progress!!
                val mastered = progress.mastered
                // Pull down to fetch the latest progress from RetroAchievements.
                PullToRefreshBox(isRefreshing = state.loading, onRefresh = { vm.load() }, modifier = Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 300.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    AsyncImage(
                                        model = progress.game.iconUrl,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                    )
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(progress.game.title.removePrefix("~Hack~ "), style = MaterialTheme.typography.titleLarge)
                                        Text(
                                            progress.summary() + if (progress.earnedHardcore > 0) " · ${progress.earnedHardcore} in hardcore" else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        ProgressBar(progress.fraction(), if (mastered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
                                    }
                                }
                                if (suggestCompleting) {
                                    CompletionSuggestion(name = null, iconUrl = null, list = null, onComplete = vm::markCompleted, onDismiss = vm::dismissCompletionSuggestion)
                                }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(shape = MaterialTheme.shapes.medium, onClick = { uri.openUri(progress.game.pageUrl) }) {
                                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Text("RetroAchievements", modifier = Modifier.padding(start = 6.dp))
                                    }
                                    onOpenHack?.let { OutlinedButton(shape = MaterialTheme.shapes.medium, onClick = it) { Text("Hack details") } }
                                }
                            }
                        }
                        items(progress.achievements.sortedByDescending { it.earned }, key = { it.id }) { achievement ->
                            AchievementItem(achievement)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AchievementItem(achievement: RaAchievement) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = achievement.badgeUrl,
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).alpha(if (achievement.earned) 1f else 0.35f),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(achievement.title, style = MaterialTheme.typography.titleSmall)
                achievement.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                val earned = achievement.earnedHardcoreAt?.let { "Unlocked in hardcore · ${it.take(10)}" } ?: achievement.earnedAt?.let { "Unlocked · ${it.take(10)}" }
                earned?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary) }
            }
            Text("${achievement.points}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AchievementFilterSheet(filter: AchievementFilter, onChange: (AchievementFilter) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Filters", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { onChange(AchievementFilter()) }, enabled = filter.isActive) { Text("Clear all") }
            }
            FilterSection("List") {
                ListFilter.entries.forEach { option ->
                    FilterChip(selected = filter.list == option, onClick = { onChange(filter.copy(list = option)) }, label = { Text(option.label) })
                }
            }
            FilterSection("Mastered (every achievement in hardcore)") {
                MasteredFilter.entries.forEach { option ->
                    FilterChip(selected = filter.mastered == option, onClick = { onChange(filter.copy(mastered = option)) }, label = { Text(option.label) })
                }
            }
        }
    }
}

/** "You finished it on RetroAchievements": offers to move the hack to the completed list. */
@Composable
private fun CompletionSuggestion(name: String?, iconUrl: String?, list: String?, onComplete: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (name != null) {
                    AsyncImage(model = iconUrl, contentDescription = null, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    name?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer) }
                    Text(
                        "You finished it on RetroAchievements" + (list?.let { " · still in $it" } ?: "") + ". Mark as completed?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onComplete,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary, contentColor = MaterialTheme.colorScheme.onTertiary),
                    modifier = Modifier.weight(1f).height(40.dp),
                ) { Text("Mark completed") }
                OutlinedButton(shape = MaterialTheme.shapes.medium, onClick = onDismiss, modifier = Modifier.weight(1f).height(40.dp)) { Text("Not now") }
            }
        }
    }
}
