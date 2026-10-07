package com.capivaraec.smwc.core.backlog

import com.capivaraec.smwc.core.drive.DriveAppDataClient
import com.capivaraec.smwc.core.model.HackDetail
import com.capivaraec.smwc.core.model.HackSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Where the backlog lives on the device (a JSON file on Android). */
interface LocalBacklogStore {
    suspend fun read(): BacklogDocument
    suspend fun write(document: BacklogDocument)
}

/**
 * Single source of truth for the backlog. Changes are saved locally right away; [sync] merges
 * with the copy in Google Drive and uploads the result.
 */
class BacklogRepository(
    private val local: LocalBacklogStore,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(BacklogDocument())
    val state: StateFlow<BacklogDocument> = _state.asStateFlow()
    private var loaded = false

    suspend fun load() = mutex.withLock { ensureLoaded() }

    suspend fun add(hack: HackSummary) = change { it.add(hack, now()) }

    /** Adds a hack that is not on SMW Central. Returns false if that name is already listed. */
    suspend fun addCustom(name: String, status: BacklogStatus): Boolean =
        change { it.addCustom(name, status, now()) }

    /** Stores the release date, exits, rating and latest version of a hack that is already listed. */
    suspend fun updateDetails(detail: HackDetail) = change { it.withDetails(detail.summary, now(), detail.latestVersionId) }

    /** Replaces an outdated entry by the newer version of the hack (see [BacklogDocument.switchToVersion]). */
    suspend fun switchToVersion(oldId: Long, latest: HackSummary) = change { it.switchToVersion(oldId, latest, now()) }

    /**
     * SMW Central hacks whose release date, exits, rating and thumbnail were never read, or were
     * read more than [maxAgeMillis] ago (ratings change over time).
     */
    fun entriesNeedingDetails(maxAgeMillis: Long): List<Long> = _state.value.visible
        .filter {
            !it.custom && (it.detailsFetchedAt == null || it.thumbnailUrl == null || now() - it.detailsFetchedAt >= maxAgeMillis)
        }
        .map { it.hackId }

    suspend fun setStatus(hackId: Long, status: BacklogStatus) = change { it.setStatus(hackId, status, now()) }
    suspend fun markCompleted(hackId: Long) = setStatus(hackId, BacklogStatus.COMPLETED)
    suspend fun markBacklog(hackId: Long) = setStatus(hackId, BacklogStatus.BACKLOG)
    suspend fun setNotes(hackId: Long, notes: String) = change { it.setNotes(hackId, notes, now()) }
    suspend fun remove(hackId: Long) = change { it.remove(hackId, now()) }

    /** Pulls `backlog.json` from Drive, merges it with the local copy, and pushes the result. */
    suspend fun sync(drive: DriveAppDataClient) = mutex.withLock {
        ensureLoaded()
        val remote = drive.download()?.let(BacklogDocument::fromJson) ?: BacklogDocument()
        val merged = BacklogDocument.merge(_state.value, remote)
        local.write(merged)
        _state.value = merged
        if (merged != remote) drive.upload(merged.toJson())
    }

    /** Applies [transform]; returns whether it changed anything. */
    private suspend fun change(transform: (BacklogDocument) -> BacklogDocument): Boolean = mutex.withLock {
        ensureLoaded()
        val updated = transform(_state.value)
        if (updated == _state.value) return@withLock false
        local.write(updated)
        _state.value = updated
        true
    }

    /** Reads the local file once, before the first change, so an early edit never overwrites it. */
    private suspend fun ensureLoaded() {
        if (!loaded) {
            _state.value = local.read()
            loaded = true
        }
    }
}
