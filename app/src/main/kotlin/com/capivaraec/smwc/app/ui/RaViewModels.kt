package com.capivaraec.smwc.app.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capivaraec.smwc.app.SmwcApplication
import com.capivaraec.smwc.core.backlog.BacklogEntry
import com.capivaraec.smwc.core.backlog.BacklogStatus
import com.capivaraec.smwc.core.backlog.matchesSearch
import com.capivaraec.smwc.core.ra.AchievementFilter
import com.capivaraec.smwc.core.ra.AchievementSort
import com.capivaraec.smwc.core.ra.RaCredentials
import com.capivaraec.smwc.core.ra.RaMatch
import com.capivaraec.smwc.core.ra.RaProgress
import com.capivaraec.smwc.core.ra.mastered
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "SmwcRa"

private fun Throwable.raMessage(): String = "RetroAchievements: ${message ?: javaClass.simpleName}"

/** Whether a hack has a RetroAchievements set. */
sealed interface RaLookup {
    /** No RetroAchievements credentials yet (entered in the Achievements tab). */
    data object NotConfigured : RaLookup
    data object Loading : RaLookup
    data object NoSet : RaLookup
    data class Found(val match: RaMatch) : RaLookup
    data class Failed(val message: String) : RaLookup
}

/**
 * Loads the hack index whenever credentials are (re)entered and reports a failure as a
 * message; [onError] gets null when a load starts.
 */
private fun ViewModel.loadRaIndexWhenConfigured(app: SmwcApplication, onError: (String?) -> Unit) {
    viewModelScope.launch {
        app.raCredentials.credentials.filterNotNull().collectLatest {
            onError(null)
            runCatching { app.retroAchievements.index() }.onFailure {
                Log.w(TAG, "Could not load the RetroAchievements hack list", it)
                onError(it.raMessage())
            }
        }
    }
}

/**
 * Backlog entries (both statuses) that have a set, matched by ROM hash when known, else by
 * name. Null while RetroAchievements is not set up or the hack list is not loaded.
 */
private fun raMatches(app: SmwcApplication): Flow<Map<Long, RaMatch>?> = combine(
    app.backlog.state,
    app.retroAchievements.loadedIndex,
    app.romHashes.hashes,
    app.raCredentials.credentials,
) { doc, index, hashes, credentials ->
    if (credentials == null || index == null) return@combine null
    doc.visible.mapNotNull { entry ->
        index.find(entry.name, hashes[entry.hackId]?.md5s.orEmpty(), entry.raUnlinkedGameIds)?.let { entry.hackId to it }
    }.toMap()
}

/** For the Backlog tab: which entries have a set. */
class BacklogRaViewModel(app: SmwcApplication) : ViewModel() {
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    val matches: StateFlow<Map<Long, RaMatch>> = raMatches(app)
        .map { it.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    init {
        loadRaIndexWhenConfigured(app) { _error.value = it }
    }
}

data class AchievementRow(
    val entry: BacklogEntry,
    val match: RaMatch,
    /** Null while loading, or when loading failed (see [progressError]). */
    val progress: RaProgress? = null,
    val progressError: String? = null,
    /** RetroAchievements says the user finished it, but it is not in the completed list. */
    val suggestCompleting: Boolean = false,
)

/** Whether to suggest moving [entry] to completed: the set is finished and the user has not said no. */
private fun suggestCompleting(entry: BacklogEntry?, progress: RaProgress?, dismissed: Set<Long>): Boolean =
    entry != null && entry.status != BacklogStatus.COMPLETED && progress?.finished == true && entry.hackId !in dismissed

data class AchievementsUiState(
    val configured: Boolean,
    val loading: Boolean = true,
    /** Sorted and filtered. */
    val rows: List<AchievementRow> = emptyList(),
    /** How many hacks have a set, before filtering. */
    val totalRows: Int = 0,
    /** Before filtering: distinct sets, sets mastered and points earned, for the summary. */
    val setCount: Int = 0,
    val masteredCount: Int = 0,
    val earnedPoints: Int = 0,
    val error: String? = null,
    val sort: AchievementSort = AchievementSort.DEFAULT,
    val filter: AchievementFilter = AchievementFilter(),
    /** While not blank, [rows] are the hacks matching it and [filter] is set aside. */
    val query: String = "",
) {
    val searching: Boolean get() = query.isNotBlank()
}

/** For the Achievements tab: backlog hacks with a set, and the user's progress in each. */
class AchievementsViewModel(private val app: SmwcApplication) : ViewModel() {
    private val progress = MutableStateFlow<Map<Long, Result<RaProgress>>>(emptyMap())
    private val _error = MutableStateFlow<String?>(null)
    private val matches = raMatches(app)
    private val query = MutableStateFlow("")

    val state: StateFlow<AchievementsUiState> =
        combine(app.backlog.state, matches, progress, _error, app.raCredentials.credentials) { doc, matchesById, progressByGame, error, credentials ->
            if (credentials == null) return@combine AchievementsUiState(configured = false, loading = false)
            val rows = doc.visible.mapNotNull { entry ->
                val match = matchesById?.get(entry.hackId) ?: return@mapNotNull null
                val result = progressByGame[match.game.id]
                AchievementRow(
                    entry = entry,
                    match = match,
                    progress = result?.getOrNull(),
                    progressError = result?.exceptionOrNull()?.raMessage(),
                )
            }
            AchievementsUiState(
                configured = true,
                loading = matchesById == null && error == null,
                rows = rows,
                totalRows = rows.size,
                setCount = rows.distinctBy { it.match.game.id }.size,
                masteredCount = rows.distinctBy { it.match.game.id }.count { it.progress?.mastered == true },
                earnedPoints = rows.distinctBy { it.match.game.id }.sumOf { it.progress?.earnedPoints ?: 0 },
                error = error,
            )
        }.combine(combine(app.preferences.achievementSort, app.preferences.achievementFilter, query, ::Triple)) { state, (sort, filter, search) ->
            // A search sets the filters aside.
            val searching = search.isNotBlank()
            state.copy(
                rows = state.rows
                    .filter { if (searching) it.entry.matchesSearch(search) else filter.matches(it.entry.status, it.progress) }
                    .sortedWith(sort.comparator({ it.entry.name }, { it.match.game })),
                sort = sort,
                filter = filter,
                query = search,
            )
        }.combine(app.preferences.dismissedCompletionSuggestions) { state, dismissed ->
            state.copy(rows = state.rows.map { it.copy(suggestCompleting = suggestCompleting(it.entry, it.progress, dismissed)) })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AchievementsUiState(configured = app.raCredentials.credentials.value != null))

    init {
        loadRaIndexWhenConfigured(app) { _error.value = it }
        // Another account means other progress.
        viewModelScope.launch { app.raCredentials.credentials.collect { progress.value = emptyMap() } }
        // Progress is fetched one set at a time, for every set that shows up in the list.
        viewModelScope.launch {
            matches.filterNotNull().collectLatest { byId -> loadProgress(byId.values.map { it.game.id }.distinct(), refresh = false) }
        }
    }

    fun markCompleted(hackId: Long) = viewModelScope.launch { app.backlog.setStatus(hackId, BacklogStatus.COMPLETED) }
    fun dismissCompletionSuggestion(hackId: Long) = app.preferences.dismissCompletionSuggestion(hackId)

    /** The set matched by name is another game: stop linking it to this hack (synced). */
    fun unlinkRaSet(hackId: Long, gameId: Long) = viewModelScope.launch { app.backlog.unlinkRaSet(hackId, gameId) }

    fun setSort(sort: AchievementSort) = app.preferences.setAchievementSort(sort)
    fun setFilter(filter: AchievementFilter) = app.preferences.setAchievementFilter(filter)
    fun setQuery(query: String) { this.query.value = query }

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    fun refresh() {
        if (_refreshing.value) return
        _refreshing.value = true
        // Every set, not only those that pass the filter.
        viewModelScope.launch {
            try {
                loadProgress(matches.first()?.values?.map { it.game.id }?.distinct().orEmpty(), refresh = true)
            } finally {
                _refreshing.value = false
            }
        }
    }

    private suspend fun loadProgress(gameIds: List<Long>, refresh: Boolean) {
        for (id in gameIds) {
            if (!refresh && progress.value[id]?.isSuccess == true) continue
            val result = runCatching { app.retroAchievements.progress(id, refresh) }
            result.exceptionOrNull()?.let { Log.w(TAG, "Could not load progress for game $id", it) }
            progress.update { it + (id to result) }
        }
    }
}

data class RaSettingsUiState(
    val username: String = "",
    val apiKey: String = "",
    val saving: Boolean = false,
    val error: String? = null,
    /** Changing an account that is already set up. */
    val editing: Boolean = false,
)

/** The RetroAchievements account form in the Achievements tab. */
class RaSettingsViewModel(private val app: SmwcApplication) : ViewModel() {
    val credentials: StateFlow<RaCredentials?> = app.raCredentials.credentials

    private val _state = MutableStateFlow(RaSettingsUiState(username = credentials.value?.username.orEmpty()))
    val state: StateFlow<RaSettingsUiState> = _state

    fun onUsernameChange(value: String) = _state.update { it.copy(username = value, error = null) }
    fun onApiKeyChange(value: String) = _state.update { it.copy(apiKey = value, error = null) }
    fun startEditing() = _state.update { it.copy(editing = true) }
    fun cancelEditing() { _state.value = RaSettingsUiState(username = credentials.value?.username.orEmpty()) }

    /** Checks the user name and key with RetroAchievements, then stores them. */
    fun save() {
        val entered = RaCredentials(_state.value.username.trim(), _state.value.apiKey.trim())
        if (entered.username.isEmpty() || entered.apiKey.isEmpty()) {
            _state.update { it.copy(error = "Enter your user name and Web API key") }
            return
        }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            runCatching { app.raClient.verify(entered) }
                .onSuccess { username ->
                    app.retroAchievements.clearProgress()
                    app.raCredentials.save(entered.copy(username = username))
                    _state.value = RaSettingsUiState(username = username)
                }
                .onFailure { e ->
                    Log.w(TAG, "RetroAchievements credentials rejected", e)
                    _state.update { it.copy(saving = false, error = e.message ?: "Could not reach RetroAchievements") }
                }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            app.raCredentials.clear()
            app.retroAchievements.clearProgress()
            _state.value = RaSettingsUiState()
        }
    }
}

data class RaSetUiState(
    val loading: Boolean = true,
    val progress: RaProgress? = null,
    val error: String? = null,
)

/** One set in detail: every achievement and whether the user has it. */
class RaSetViewModel(private val app: SmwcApplication, private val gameId: Long, private val hackId: Long?) : ViewModel() {
    private val _state = MutableStateFlow(RaSetUiState())
    val state: StateFlow<RaSetUiState> = _state

    /** RetroAchievements says the user finished the hack, but it is not in the completed list. */
    val suggestCompleting: StateFlow<Boolean> = combine(_state, app.backlog.state, app.preferences.dismissedCompletionSuggestions) { s, doc, dismissed ->
        suggestCompleting(hackId?.let(doc::entry), s.progress, dismissed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun markCompleted() {
        val id = hackId ?: return
        viewModelScope.launch { app.backlog.setStatus(id, BacklogStatus.COMPLETED) }
    }

    fun dismissCompletionSuggestion() {
        hackId?.let(app.preferences::dismissCompletionSuggestion)
    }

    init {
        load(refresh = false)
    }

    fun load(refresh: Boolean = true) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { app.retroAchievements.progress(gameId, refresh) }
                .onSuccess { _state.value = RaSetUiState(loading = false, progress = it) }
                .onFailure {
                    Log.w(TAG, "Could not load set $gameId", it)
                    _state.update { s -> s.copy(loading = false, error = it.raMessage()) }
                }
        }
    }
}
