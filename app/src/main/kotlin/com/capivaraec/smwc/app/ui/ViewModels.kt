package com.capivaraec.smwc.app.ui

import android.app.PendingIntent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capivaraec.smwc.app.SmwcApplication
import com.capivaraec.smwc.app.data.DriveConsentRequired
import com.capivaraec.smwc.core.api.HackSearch
import com.capivaraec.smwc.core.api.SearchCursor
import com.capivaraec.smwc.core.api.SearchResult
import com.capivaraec.smwc.core.api.SmwcRateLimitedException
import com.capivaraec.smwc.core.backlog.BacklogEntry
import com.capivaraec.smwc.core.backlog.BacklogRepository
import com.capivaraec.smwc.core.backlog.BacklogFilter
import com.capivaraec.smwc.core.backlog.BacklogSort
import com.capivaraec.smwc.core.backlog.BacklogStatus
import com.capivaraec.smwc.core.backlog.sortedWith
import com.capivaraec.smwc.core.model.HackDetail
import com.capivaraec.smwc.core.model.HackSummary
import com.capivaraec.smwc.core.patch.PatchedRom
import com.capivaraec.smwc.core.ra.RaMatchKind
import kotlinx.coroutines.flow.collectLatest
import com.capivaraec.smwc.core.ra.RaProgress
import com.capivaraec.smwc.core.ra.RaTimes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun Throwable.userMessage(): String = when (this) {
    is SmwcRateLimitedException -> "SMW Central asked to wait a bit before the next search"
    else -> "Could not reach SMW Central (${message ?: javaClass.simpleName})"
}

data class SearchUiState(
    val query: String = "",
    /** The query the current results are for ("" = latest hacks). */
    val searchedQuery: String = "",
    val results: List<HackSummary> = emptyList(),
    val next: SearchCursor? = null,
    /** Nothing matched the exact text; these are close matches (punctuation ignored). */
    val approximate: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    /** Result of adding the typed name as a hack that is not on SMW Central. */
    val addedMessage: String? = null,
) {
    val hasNext: Boolean get() = next != null
}

/** Searches run on submit, not on every keystroke, to stay under SMW Central's rate limit. */
class SearchViewModel(private val search: HackSearch, private val backlog: BacklogRepository) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state
    private var job: Job? = null

    init {
        search() // latest hacks
    }

    fun onQueryChange(query: String) = _state.update { it.copy(query = query, addedMessage = null) }

    /** The screen has shown [SearchUiState.addedMessage]; clearing it lets the same message show again. */
    fun messageShown() = _state.update { it.copy(addedMessage = null) }

    /** Adds the typed name to the backlog (or completed) for a hack that is not on SMW Central. */
    fun addByName(status: BacklogStatus) {
        val name = _state.value.query.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            val added = backlog.addCustom(name, status)
            val list = when (status) {
                BacklogStatus.BACKLOG -> "backlog"
                BacklogStatus.PLAYING -> "playing list"
                BacklogStatus.COMPLETED -> "completed hacks"
            }
            _state.update {
                it.copy(addedMessage = if (added) "Added \"$name\" to your $list" else "\"$name\" is already in your list")
            }
        }
    }

    fun search() {
        val query = _state.value.query.trim()
        load { search.search(query) to query }
    }

    /** Empties the search box and shows the latest hacks again. */
    fun clear() {
        _state.update { it.copy(query = "") }
        search()
    }

    fun loadMore() {
        val s = _state.value
        val cursor = s.next ?: return
        if (!s.loading) load(append = true) { search.loadMore(cursor, s.searchedQuery) to s.searchedQuery }
    }

    private fun load(append: Boolean = false, fetch: suspend () -> Pair<SearchResult, String>) {
        job?.cancel()
        // A new search clears the list, so the progress indicator is in view (a loose search can take a few seconds).
        _state.update {
            if (append) it.copy(loading = true, error = null)
            else it.copy(results = emptyList(), next = null, approximate = false, loading = true, error = null, addedMessage = null)
        }
        job = viewModelScope.launch {
            runCatching { fetch() }
                .onSuccess { (result, query) ->
                    _state.update {
                        it.copy(
                            searchedQuery = query,
                            // A loose search can meet a hack again on a later page.
                            results = if (append) (it.results + result.items).distinctBy { hack -> hack.id } else result.items,
                            next = result.next,
                            approximate = result.approximate,
                            loading = false,
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.userMessage()) } }
        }
    }
}

data class DetailUiState(
    val detail: HackDetail? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

/** The "Apply patch" flow on the details screen. */
sealed interface PatchUiState {
    data object Idle : PatchUiState
    data object Working : PatchUiState

    /** The download has several patches (e.g. alternative versions): the user picks one. */
    data class Choose(val roms: List<PatchedRom>) : PatchUiState

    /** Ready to be saved: the screen opens the system "save as" picker once. */
    data class Save(val rom: PatchedRom) : PatchUiState

    /** The "save as" picker is open. */
    data class Saving(val rom: PatchedRom) : PatchUiState
    data class Done(val message: String) : PatchUiState
    data class Failed(val message: String) : PatchUiState
}

class DetailViewModel(private val app: SmwcApplication, private val hackId: Long) : ViewModel() {
    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state

    val entry: StateFlow<BacklogEntry?> = app.backlog.state
        .map { it.entry(hackId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Declared before init: with cached details, reload() sets it before the constructor returns.
    private val _ra = MutableStateFlow<RaLookup>(RaLookup.Loading)
    val ra: StateFlow<RaLookup> = _ra

    /** The hack's name and ROM hashes from the last lookup, to look again after (un)linking a set. */
    private var raHackName: String? = null
    private var raMd5s: List<String> = emptyList()

    /** The user's progress in the hack's set, when it has one (from the daily cache when fresh). */
    private val _raProgress = MutableStateFlow<RaProgress?>(null)
    val raProgress: StateFlow<RaProgress?> = _raProgress

    init {
        reload()
    }

    fun reload() {
        _state.value = DetailUiState(loading = true)
        viewModelScope.launch {
            runCatching { app.smwc.getHack(hackId) }
                .onSuccess {
                    _state.value = DetailUiState(detail = it, loading = false)
                    // Keeps the backlog entry's rating and "newer version" info current.
                    if (app.backlog.state.value.entry(hackId) != null) app.backlog.updateDetails(it)
                    findRaSet(it)
                }
                .onFailure { _state.value = DetailUiState(loading = false, error = it.userMessage()) }
        }
    }

    /** Looks up the set by name (and known hashes) right away, then again once the ROM hash is computed. */
    private suspend fun findRaSet(detail: HackDetail) {
        if (app.raCredentials.credentials.value == null) {
            _ra.value = RaLookup.NotConfigured
            return
        }
        raHackName = detail.summary.name
        _ra.value = RaLookup.Loading
        lookup(app.romHashes.hashes.value[hackId]?.md5s.orEmpty())
        val url = detail.downloadUrl ?: return
        if (!app.cleanRom.available.value || !app.romHashes.isOutdated(hackId, url)) return
        runCatching { app.romHashes.resolve(hackId, url) }
            .onSuccess { lookup(it.md5s) }
            .onFailure { Log.w("SmwcRom", "Could not hash hack $hackId", it) }
    }

    private suspend fun lookup(md5s: List<String> = raMd5s) {
        val ra = app.retroAchievements
        val name = raHackName ?: return
        raMd5s = md5s
        val unlinked = app.backlog.state.value.entry(hackId)?.raUnlinkedGameIds.orEmpty()
        _ra.value = runCatching { ra.find(name, md5s, unlinked) }.fold(
            onSuccess = { match -> match?.let { RaLookup.Found(it) } ?: RaLookup.NoSet },
            onFailure = { RaLookup.Failed("RetroAchievements: ${it.message ?: it.javaClass.simpleName}") },
        )
        (_ra.value as? RaLookup.Found)?.let { found ->
            if (_raProgress.value?.game?.id != found.match.game.id) {
                _raProgress.value = runCatching { ra.progress(found.match.game.id) }.getOrNull()
            }
        }
    }

    /** The set matched by name is another game: stop linking it to this hack (synced), then look again. */
    fun unlinkRaSet() {
        val match = (_ra.value as? RaLookup.Found)?.match?.takeIf { it.kind == RaMatchKind.NAME } ?: return
        viewModelScope.launch {
            app.backlog.unlinkRaSet(hackId, match.game.id)
            lookup()
        }
    }

    /** Undoes [unlinkRaSet], so name matching links those sets again. */
    fun relinkRaSets() {
        viewModelScope.launch {
            app.backlog.relinkRaSets(hackId)
            lookup()
        }
    }

    private val _patch = MutableStateFlow<PatchUiState>(PatchUiState.Idle)
    val patch: StateFlow<PatchUiState> = _patch

    /** Downloads the patch and applies it to the clean ROM (which must be chosen first). */
    fun applyPatch() {
        val url = _state.value.detail?.downloadUrl ?: return
        val current = _patch.value
        if (current == PatchUiState.Working || current is PatchUiState.Save || current is PatchUiState.Saving) return
        _patch.value = PatchUiState.Working
        viewModelScope.launch {
            _patch.value = runCatching { app.hackPatcher.patch(url) }.fold(
                onSuccess = { roms ->
                    // One ROM per patch file: the best guess when an IPS patch allows two.
                    val choices = roms.distinctBy { it.patchName }
                    if (choices.size == 1) PatchUiState.Save(choices.single()) else PatchUiState.Choose(choices)
                },
                onFailure = {
                    Log.w("SmwcRom", "Could not patch hack $hackId", it)
                    PatchUiState.Failed(it.message ?: "Could not apply the patch")
                },
            )
        }
    }

    fun choose(rom: PatchedRom) {
        _patch.value = PatchUiState.Save(rom)
    }

    /** Called once the "save as" picker is open, so it is not opened again (e.g. on rotation). */
    fun onSavePickerShown() {
        (_patch.value as? PatchUiState.Save)?.let { _patch.value = PatchUiState.Saving(it.rom) }
    }

    /** Writes the patched ROM to [uri] (from the "save as" picker); null if the user cancelled. */
    fun save(uri: Uri?) {
        val rom = (_patch.value as? PatchUiState.Saving)?.rom ?: return
        if (uri == null) {
            _patch.value = PatchUiState.Idle
            return
        }
        viewModelScope.launch {
            _patch.value = runCatching {
                withContext(Dispatchers.IO) {
                    app.contentResolver.openOutputStream(uri)?.use { it.write(rom.rom) } ?: error("Could not open the file")
                }
            }.fold(
                onSuccess = {
                    val guessed = if (rom.certain) "" else " (IPS patch: check that it runs)"
                    PatchUiState.Done("Saved ${rom.fileName}$guessed")
                },
                onFailure = { PatchUiState.Failed("Could not save the ROM: ${it.message}") },
            )
        }
    }

    /** E.g. the chosen file was not a clean Super Mario World ROM. */
    fun reportPatchError(message: String) {
        _patch.value = PatchUiState.Failed(message)
    }

    fun dismissPatch() {
        _patch.value = PatchUiState.Idle
    }

    /** Adds the hack to the backlog, or straight to [status] (e.g. playing). */
    fun add(status: BacklogStatus = BacklogStatus.BACKLOG) {
        val summary = _state.value.detail?.summary ?: return
        viewModelScope.launch {
            app.backlog.add(summary)
            app.backlog.setStatus(hackId, status)
        }
    }

    fun setStatus(status: BacklogStatus) = viewModelScope.launch { app.backlog.setStatus(hackId, status) }
    fun remove() = viewModelScope.launch { app.backlog.remove(hackId) }
    fun setNotes(notes: String) = viewModelScope.launch { app.backlog.setNotes(hackId, notes) }

    private val _openHack = Channel<Long>(Channel.BUFFERED)

    /** Hack ids to show instead of this one (after switching to its newer version). */
    val openHack = _openHack.receiveAsFlow()

    private val _switching = MutableStateFlow(false)
    val switching: StateFlow<Boolean> = _switching

    /**
     * Replaces this (outdated) version by the newer one in the backlog, keeping status and notes,
     * then asks the screen to show the new version. Without a backlog entry it just opens it.
     */
    fun switchToNewerVersion() {
        val latestId = _state.value.detail?.latestVersionId ?: entry.value?.latestVersionId ?: return
        if (_switching.value) return
        _switching.value = true
        viewModelScope.launch {
            runCatching {
                if (app.backlog.state.value.entry(hackId) != null) {
                    app.backlog.switchToVersion(hackId, app.smwc.getHack(latestId).summary)
                }
            }.onSuccess { _openHack.send(latestId) }
                .onFailure { _state.update { s -> s.copy(error = it.userMessage()) } }
            _switching.value = false
        }
    }
}

data class SortedEntries(val sort: BacklogSort, val items: List<BacklogEntry>)

class BacklogViewModel(private val app: SmwcApplication) : ViewModel() {
    val sort: StateFlow<BacklogSort> = app.preferences.backlogSort

    /** The entries with the order they are sorted in, so the screen knows when a new order has arrived. */
    val entries: StateFlow<SortedEntries> = combine(app.backlog.state, sort, raMatches(app), app.retroAchievements.loadedTimes) { doc, sort, matches, times ->
        val masterSeconds = { entry: BacklogEntry -> matches?.get(entry.hackId)?.let { times[it.game.id] }?.masterSeconds }
        SortedEntries(sort, doc.visible.sortedWith(sort, masterSeconds))
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SortedEntries(sort.value, emptyList()))

    fun setSort(sort: BacklogSort) = app.preferences.setBacklogSort(sort)

    val filter: StateFlow<BacklogFilter> = app.preferences.backlogFilter
    fun setFilter(filter: BacklogFilter) = app.preferences.setBacklogFilter(filter)

    /** Whether the RetroAchievements filter can be offered (an account is set up). */
    val raConfigured: StateFlow<Boolean> = app.raCredentials.credentials
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), app.raCredentials.credentials.value != null)

    /** Beat / master times of the sets loaded so far, by RetroAchievements game id. */
    val times: StateFlow<Map<Long, RaTimes>> = app.retroAchievements.loadedTimes

    init {
        viewModelScope.launch { app.backlog.load() }
        // Backlog and Playing cards show their set's times; sorting by time to master needs every set's.
        viewModelScope.launch {
            combine(sort, raMatches(app), app.backlog.state, ::Triple).collectLatest { (sort, matches, doc) ->
                if (matches == null) return@collectLatest
                val gameIds = matches
                    .filter { (hackId, _) -> sort.byMasterTime || doc.entry(hackId)?.status != BacklogStatus.COMPLETED }
                    .values.map { it.game.id }.distinct()
                for (id in gameIds) runCatching { app.retroAchievements.times(id) }
            }
        }
    }

    fun setStatus(id: Long, status: BacklogStatus) = viewModelScope.launch { app.backlog.setStatus(id, status) }
    fun remove(id: Long) = viewModelScope.launch { app.backlog.remove(id) }
    fun setNotes(id: Long, notes: String) = viewModelScope.launch { app.backlog.setNotes(id, notes) }
}

data class SyncUiState(val syncing: Boolean = false, val lastError: String? = null, val lastSyncAt: Long? = null)

/**
 * Syncs the backlog with Google Drive when the app opens, a few seconds after each change,
 * and on demand. Emits on [consentRequests] when Google needs the user's permission first.
 */
@OptIn(FlowPreview::class)
class SyncViewModel(private val app: SmwcApplication) : ViewModel() {
    private val _state = MutableStateFlow(SyncUiState())
    val state: StateFlow<SyncUiState> = _state

    private val consent = Channel<PendingIntent>(Channel.CONFLATED)
    val consentRequests = consent.receiveAsFlow()
    private var job: Job? = null
    private var askedConsent = false
    private var consentError: String? = null

    init {
        viewModelScope.launch {
            app.backlog.state.drop(1).debounce(3_000).collect { syncNow() }
        }
    }

    /** Automatic syncs ask for Drive consent only once per session; [manual] always asks. */
    fun syncNow(manual: Boolean = false) {
        if (job?.isActive == true) return
        _state.update { it.copy(syncing = true) }
        job = viewModelScope.launch {
            try {
                app.backlog.sync(app.drive)
                _state.value = SyncUiState(lastSyncAt = System.currentTimeMillis())
            } catch (e: DriveConsentRequired) {
                // Keep the reason from the last consent attempt until the user asks again.
                _state.update { it.copy(syncing = false, lastError = consentError ?: "Allow access to Google Drive") }
                if (manual || !askedConsent) {
                    askedConsent = true
                    consentError = null
                    consent.trySend(e.pendingIntent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Sync failed", e)
                _state.update { it.copy(syncing = false, lastError = "Sync failed: ${e.message}") }
            }
        }
    }

    /** [error] is null when the user granted Drive access on the consent screen. */
    fun onConsentResult(error: String?) {
        consentError = error
        if (error == null) syncNow() else _state.update { it.copy(syncing = false, lastError = error) }
    }

    private companion object {
        const val TAG = "SmwcSync"
    }
}
