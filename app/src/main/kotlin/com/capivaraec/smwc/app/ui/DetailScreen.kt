package com.capivaraec.smwc.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.capivaraec.smwc.app.SmwcApplication
import com.capivaraec.smwc.app.ui.components.Badge
import com.capivaraec.smwc.app.ui.components.DifficultyChip
import com.capivaraec.smwc.app.ui.components.InfoChip
import com.capivaraec.smwc.app.ui.components.LocalSnackbar
import com.capivaraec.smwc.app.ui.components.SectionLabel
import com.capivaraec.smwc.core.backlog.BacklogStatus
import com.capivaraec.smwc.core.model.HackDetail
import com.capivaraec.smwc.core.model.lengthDisplay
import com.capivaraec.smwc.core.ra.RaMatchKind
import com.capivaraec.smwc.core.ra.RaProgress
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * A hack's details. [onBack] null when shown as a pane beside a list (no back button then).
 */
@Composable
fun DetailScreen(
    app: SmwcApplication,
    hackId: Long,
    onBack: (() -> Unit)?,
    onOpenRaSet: (gameId: Long) -> Unit,
    /** Shows another hack in place of this one (its newer version). */
    onReplaceWithHack: (hackId: Long) -> Unit,
) {
    val vm: DetailViewModel = viewModel(key = "hack-$hackId") { DetailViewModel(app, hackId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val entry by vm.entry.collectAsStateWithLifecycle()
    val ra by vm.ra.collectAsStateWithLifecycle()
    val raProgress by vm.raProgress.collectAsStateWithLifecycle()
    val patch by vm.patch.collectAsStateWithLifecycle()
    val switching by vm.switching.collectAsStateWithLifecycle()
    LaunchedEffect(vm) { vm.openHack.collect(onReplaceWithHack) }

    // "Apply patch": choose the clean ROM if needed, patch, then save where the user picks.
    val hasCleanRom by app.cleanRom.available.collectAsStateWithLifecycle()
    val pickRom = rememberCleanRomPicker(app) { error -> if (error == null) vm.applyPatch() else vm.reportPatchError(error) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        vm.save(uri)
    }
    LaunchedEffect(patch) {
        (patch as? PatchUiState.Save)?.let {
            saveLauncher.launch(it.rom.fileName)
            vm.onSavePickerShown()
        }
    }
    // The outcome ("Saved …" or why it failed) as a snackbar.
    val snackbar = LocalSnackbar.current
    LaunchedEffect(patch) {
        val message = (patch as? PatchUiState.Done)?.message ?: (patch as? PatchUiState.Failed)?.message ?: return@LaunchedEffect
        // Show first, then clear: clearing changes the key and would cancel this effect (and the snackbar).
        snackbar.showSnackbar(message, withDismissAction = true)
        vm.dismissPatch()
    }
    (patch as? PatchUiState.Choose)?.let { choose ->
        AlertDialog(
            onDismissRequest = vm::dismissPatch,
            title = { Text("Choose a patch") },
            text = {
                Column {
                    choose.roms.forEach { rom ->
                        TextButton(onClick = { vm.choose(rom) }) { Text(rom.patchName) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = vm::dismissPatch) { Text("Cancel") } },
        )
    }

    when {
        state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        state.error != null -> Column(Modifier.fillMaxSize().padding(16.dp)) {
            onBack?.let { IconButton(onClick = it) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            Text(state.error!!, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = vm::reload) { Text("Try again") }
        }
        else -> state.detail?.let { detail ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Screenshots(detail.screenshotUrls, onBack, newVersion = detail.latestVersionId != null)
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Header(detail)
                    if (detail.latestVersionId != null) {
                        NewerVersionNotice(inBacklog = entry != null, switching = switching, onSwitch = vm::switchToNewerVersion)
                    }
                    RaSetCard(ra, raProgress, onOpenRaSet)
                    StatusActions(
                        status = entry?.status,
                        onAdd = vm::add,
                        onStatus = { vm.setStatus(it) },
                        onRemove = { vm.remove() },
                    )
                    LinkActions(
                        detail = detail,
                        patching = patch == PatchUiState.Working,
                        onApplyPatch = { if (hasCleanRom) vm.applyPatch() else pickRom() },
                    )
                    entry?.let {
                        SectionLabel("Notes")
                        NotesEditor(notes = it.notes, onSave = vm::setNotes)
                    }
                    detail.descriptionHtml?.let {
                        SectionLabel("About")
                        Text(AnnotatedString.fromHtml(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                    if (detail.tags.isNotEmpty()) {
                        SectionLabel("Tags")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            detail.tags.forEach { InfoChip(it) }
                        }
                    }
                }
            }
        }
    }
}

/** Big screenshots at the top, swiped through, with the back button over them. */
@Composable
private fun Screenshots(urls: List<String>, onBack: (() -> Unit)?, newVersion: Boolean) {
    val pager = rememberPagerState { urls.size.coerceAtLeast(1) }
    // SNES proportions (256×224), but at most 200dp tall so landscape screens keep room for the rest.
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height((maxWidth * 224f / 256f).coerceAtMost(200.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
            if (urls.isNotEmpty()) {
                HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                    AsyncImage(model = urls[page], contentDescription = "Screenshot ${page + 1} of ${urls.size}", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            onBack?.let {
                IconButton(
                    onClick = it,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
                    modifier = Modifier.statusBarsPadding().padding(8.dp),
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            }
            if (newVersion) Badge("New version", Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp))
            if (urls.size > 1) {
                Row(Modifier.align(Alignment.BottomCenter).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(urls.size) { index ->
                        val current = index == pager.currentPage
                        Box(
                            Modifier.size(width = if (current) 18.dp else 6.dp, height = 6.dp).clip(RoundedCornerShape(3.dp))
                                .background(Color.White.copy(alpha = if (current) 1f else 0.5f)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(detail: HackDetail) {
    val s = detail.summary
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(s.name, style = MaterialTheme.typography.headlineSmall)
        val byline = listOfNotNull(
            s.authors.takeIf { it.isNotEmpty() }?.let { "by ${it.joinToString()}" },
            s.acceptedAt?.let { "released ${releaseDate.format(Instant.ofEpochSecond(it))}" },
        ).joinToString(" · ")
        if (byline.isNotEmpty()) Text(byline, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DifficultyChip(s.difficulty)
            lengthDisplay(s.length)?.let { InfoChip(it) }
            s.rating?.let { InfoChip("★ %.1f".format(it)) }
            s.downloads?.let { InfoChip("%,d downloads".format(it)) }
        }
    }
}

/** e.g. "2025-12-26", in the device's time zone. */
private val releaseDate = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneId.systemDefault())

/** Whether the hack has a RetroAchievements set and the user's progress in it; tapping opens the set. */
@Composable
private fun RaSetCard(ra: RaLookup, progress: RaProgress?, onOpenRaSet: (gameId: Long) -> Unit) {
    val (title, detail, gameId) = when (ra) {
        RaLookup.NotConfigured -> return
        RaLookup.Loading -> Triple("RetroAchievements", "Checking for a set…", null)
        RaLookup.NoSet -> Triple("RetroAchievements", "No set for this hack", null)
        is RaLookup.Failed -> Triple("RetroAchievements", ra.message, null)
        is RaLookup.Found -> {
            val game = ra.match.game
            val counts = progress?.takeIf { it.game.id == game.id }
                ?.let { "${it.earned}/${it.achievements.size} unlocked · ${it.earnedPoints}/${game.points} points" }
                ?: "${game.achievements} achievements · ${game.points} points"
            val how = if (ra.match.kind == RaMatchKind.HASH) "verified by ROM hash" else "matched by name"
            Triple("RetroAchievements set", "$counts · $how", game.id)
        }
    }
    Surface(
        onClick = { gameId?.let(onOpenRaSet) },
        enabled = gameId != null,
        shape = RoundedCornerShape(14.dp),
        color = if (gameId != null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = if (ra is RaLookup.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (gameId != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Adding to the lists or moving between them: the next step big, the rest beside it. */
@Composable
private fun StatusActions(status: BacklogStatus?, onAdd: (BacklogStatus) -> Unit, onStatus: (BacklogStatus) -> Unit, onRemove: () -> Unit) {
    val big = Modifier.height(48.dp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        when (status) {
            null -> {
                Button(shape = MaterialTheme.shapes.medium, onClick = { onAdd(BacklogStatus.BACKLOG) }, modifier = big.weight(1f)) { Text("Add to backlog") }
                OutlinedButton(shape = MaterialTheme.shapes.medium, onClick = { onAdd(BacklogStatus.PLAYING) }, modifier = big.weight(1f)) { Text("Start playing") }
            }
            BacklogStatus.BACKLOG -> {
                Button(shape = MaterialTheme.shapes.medium, onClick = { onStatus(BacklogStatus.PLAYING) }, modifier = big.weight(1f)) { Text("Start playing") }
                OutlinedButton(shape = MaterialTheme.shapes.medium, onClick = { onStatus(BacklogStatus.COMPLETED) }, modifier = big.weight(1f)) { Text("Mark completed") }
            }
            BacklogStatus.PLAYING -> {
                Button(shape = MaterialTheme.shapes.medium, onClick = { onStatus(BacklogStatus.COMPLETED) }, modifier = big.weight(1f)) { Text("Mark completed") }
                OutlinedButton(shape = MaterialTheme.shapes.medium, onClick = { onStatus(BacklogStatus.BACKLOG) }, modifier = big.weight(1f)) { Text("Back to backlog") }
            }
            BacklogStatus.COMPLETED -> {
                OutlinedButton(shape = MaterialTheme.shapes.medium, onClick = { onStatus(BacklogStatus.BACKLOG) }, modifier = big.weight(1f)) { Text("Move back to backlog") }
            }
        }
    }
    if (status != null) {
        TextButton(onClick = onRemove, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
            Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Remove from my hacks")
        }
    }
}

/** Site, download and patch, as three icon buttons. */
@Composable
private fun LinkActions(detail: HackDetail, patching: Boolean, onApplyPatch: () -> Unit) {
    val uri = LocalUriHandler.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        IconAction("Open site", Icons.AutoMirrored.Filled.OpenInNew, Modifier.weight(1f)) { uri.openUri(detail.pageUrl) }
        detail.downloadUrl?.let { url ->
            IconAction("Download", Icons.Filled.Download, Modifier.weight(1f)) { uri.openUri(url) }
            IconAction(
                when {
                    patching -> "Patching…"
                    detail.latestVersionId != null -> "Patch (old version)"
                    else -> "Apply patch"
                },
                Icons.Filled.SportsEsports,
                Modifier.weight(1f),
                highlighted = true,
                enabled = !patching,
                onClick = onApplyPatch,
            )
        }
    }
}

@Composable
private fun IconAction(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent,
        contentColor = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline),
        modifier = modifier.height(64.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

/** Shown on an obsolete version of a hack: SMW Central has a newer one. */
@Composable
private fun NewerVersionNotice(inBacklog: Boolean, switching: Boolean, onSwitch: () -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "A newer version is on SMW Central.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = onSwitch,
                enabled = !switching,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary),
            ) {
                Text(
                    when {
                        switching -> "Switching…"
                        inBacklog -> "Switch version"
                        else -> "Open it"
                    },
                )
            }
        }
    }
}

/** The user's notes for a hack in their lists; saved when they tap Save. */
@Composable
fun NotesEditor(notes: String, onSave: (String) -> Unit) {
    var text by rememberSaveable(notes) { mutableStateOf(notes) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Notes") },
            placeholder = { Text("Where you stopped, tips, why you gave up…") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        if (text.trim() != notes) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(shape = MaterialTheme.shapes.medium, onClick = { onSave(text) }) { Text("Save notes") }
                TextButton(onClick = { text = notes }) { Text("Discard") }
            }
        }
    }
}
