package com.capivaraec.smwc.core.backlog

import com.capivaraec.smwc.core.model.HackSummary
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest

@Serializable
enum class BacklogStatus(val label: String) {
    @SerialName("backlog") BACKLOG("Backlog"),
    @SerialName("playing") PLAYING("Playing"),
    @SerialName("completed") COMPLETED("Completed"),
}

@Serializable
data class BacklogEntry(
    val hackId: Long,
    val name: String,
    val authors: List<String> = emptyList(),
    val difficulty: String? = null,
    val status: BacklogStatus = BacklogStatus.BACKLOG,
    /** Epoch millis. */
    val addedAt: Long,
    val completedAt: Long? = null,
    /** Epoch millis of the last change; decides which side wins when merging. */
    val updatedAt: Long,
    /** Tombstone, so a removal on one device is not undone by a sync from another. */
    val deleted: Boolean = false,
    /** When SMW Central accepted the hack ("Accepted by" date), Unix epoch seconds; for sorting. */
    val acceptedAt: Long? = null,
    /** Number of exits; for sorting. */
    val exits: Int? = null,
    /** SMW Central rating (0–5); null if unrated. For filtering. */
    val rating: Double? = null,
    /** When the release date, exits and rating were last read from SMW Central, epoch millis. */
    val detailsFetchedAt: Long? = null,
    /**
     * Added by name because it is not on SMW Central (e.g. only in the author's files). Such
     * entries have a negative [hackId] (see [customId]) and no details page.
     */
    val custom: Boolean = false,
    /**
     * The id of the current version on SMW Central when this one became obsolete (a new version
     * is a new file with a new id); null while this is the latest. Kept fresh by [withDetails].
     */
    val latestVersionId: Long? = null,
    /** The user's own notes (where they stopped, tips, why they gave up…). */
    val notes: String = "",
    /** The hack's first screenshot, for lists. */
    val thumbnailUrl: String? = null,
) {
    /** A newer version of this hack is on SMW Central. */
    val hasNewerVersion: Boolean get() = latestVersionId != null && latestVersionId != hackId

    companion object {
        /**
         * The id of a hack added by name: negative, so it never clashes with SMW Central ids,
         * and derived from the name, so adding the same name on two devices merges into one entry.
         */
        fun customId(name: String): Long {
            val key = name.trim().replace(Regex("\\s+"), " ").lowercase()
            val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
            val positive = digest.take(8).fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) } and Long.MAX_VALUE
            return -(positive % (Long.MAX_VALUE - 1)) - 1
        }
    }
}

/** The whole backlog as stored locally and as `backlog.json` in Google Drive. */
@Serializable
data class BacklogDocument(
    val schemaVersion: Int = 1,
    val entries: List<BacklogEntry> = emptyList(),
) {
    /** Entries the UI shows (tombstones hidden). */
    val visible: List<BacklogEntry> get() = entries.filterNot { it.deleted }

    fun entry(hackId: Long): BacklogEntry? = entries.firstOrNull { it.hackId == hackId && !it.deleted }

    fun add(hack: HackSummary, now: Long): BacklogDocument {
        val existing = entries.firstOrNull { it.hackId == hack.id }
        if (existing != null && !existing.deleted) return this
        return upsert(
            BacklogEntry(
                hackId = hack.id,
                name = hack.name,
                authors = hack.authors,
                difficulty = hack.difficulty,
                addedAt = now,
                updatedAt = now,
                acceptedAt = hack.acceptedAt,
                exits = hack.exits,
                rating = hack.rating,
                detailsFetchedAt = now,
                thumbnailUrl = hack.thumbnailUrl,
            ),
        )
    }

    /**
     * Stores the release date, exits and rating read from SMW Central (filled in for entries
     * added before they were stored, and refreshed now and then since ratings change).
     * [BacklogEntry.updatedAt] is kept, so this never wins a merge over a real change made on
     * another device.
     */
    fun withDetails(hack: HackSummary, now: Long, latestVersionId: Long? = null): BacklogDocument {
        val existing = entries.firstOrNull { it.hackId == hack.id } ?: return this
        val updated = existing.copy(
            acceptedAt = hack.acceptedAt ?: existing.acceptedAt,
            exits = hack.exits ?: existing.exits,
            rating = hack.rating ?: existing.rating,
            detailsFetchedAt = now,
            latestVersionId = latestVersionId,
            thumbnailUrl = hack.thumbnailUrl ?: existing.thumbnailUrl,
        )
        return if (updated == existing) this else upsert(updated)
    }

    /**
     * Moves an entry to the newer version [latest] of its hack: the old id becomes a tombstone
     * and the new one keeps the status, dates and notes. If the new version is already listed,
     * only the old entry is removed.
     */
    fun switchToVersion(oldId: Long, latest: HackSummary, now: Long): BacklogDocument {
        val old = entry(oldId) ?: return this
        val removed = upsert(old.copy(deleted = true, updatedAt = now))
        if (removed.entry(latest.id) != null) return removed
        return removed.upsert(
            old.copy(
                hackId = latest.id,
                name = latest.name,
                authors = latest.authors,
                difficulty = latest.difficulty,
                acceptedAt = latest.acceptedAt,
                exits = latest.exits,
                rating = latest.rating,
                detailsFetchedAt = now,
                latestVersionId = null,
                thumbnailUrl = latest.thumbnailUrl ?: old.thumbnailUrl,
                updatedAt = now,
            ),
        )
    }

    /** Adds a hack that is not on SMW Central, by name; no change if it is already listed. */
    fun addCustom(name: String, status: BacklogStatus, now: Long): BacklogDocument {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "The name is empty" }
        val id = BacklogEntry.customId(trimmed)
        if (entry(id) != null) return this
        return upsert(
            BacklogEntry(
                hackId = id,
                name = trimmed,
                status = status,
                addedAt = now,
                completedAt = if (status == BacklogStatus.COMPLETED) now else null,
                updatedAt = now,
                custom = true,
            ),
        )
    }

    /** Moves an entry to [status]; the completion date is set when completing and cleared otherwise. */
    fun setStatus(hackId: Long, status: BacklogStatus, now: Long): BacklogDocument = update(hackId) {
        if (it.status == status) it
        else it.copy(status = status, completedAt = if (status == BacklogStatus.COMPLETED) now else null, updatedAt = now)
    }

    fun markCompleted(hackId: Long, now: Long): BacklogDocument = setStatus(hackId, BacklogStatus.COMPLETED, now)

    fun markBacklog(hackId: Long, now: Long): BacklogDocument = setStatus(hackId, BacklogStatus.BACKLOG, now)

    fun setNotes(hackId: Long, notes: String, now: Long): BacklogDocument = update(hackId) {
        val trimmed = notes.trim()
        if (it.notes == trimmed) it else it.copy(notes = trimmed, updatedAt = now)
    }

    fun remove(hackId: Long, now: Long): BacklogDocument =
        update(hackId) { it.copy(deleted = true, updatedAt = now) }

    private fun update(hackId: Long, change: (BacklogEntry) -> BacklogEntry): BacklogDocument {
        val existing = entry(hackId) ?: return this
        val changed = change(existing)
        return if (changed == existing) this else upsert(changed)
    }

    private fun upsert(entry: BacklogEntry): BacklogDocument =
        copy(entries = entries.filterNot { it.hackId == entry.hackId } + entry)

    fun toJson(): String = codec.encodeToString(serializer(), this)

    companion object {
        private val codec = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            encodeDefaults = true
        }

        fun fromJson(json: String): BacklogDocument = codec.decodeFromString(serializer(), json)

        /**
         * Merges two copies of the backlog (e.g. this phone and Google Drive): for each hack the
         * most recently updated entry wins. On a tie the local copy wins.
         */
        fun merge(local: BacklogDocument, remote: BacklogDocument): BacklogDocument {
            val byId = LinkedHashMap<Long, BacklogEntry>()
            for (entry in remote.entries) byId[entry.hackId] = entry
            for (entry in local.entries) {
                val other = byId[entry.hackId]
                if (other == null || entry.updatedAt >= other.updatedAt) byId[entry.hackId] = entry
            }
            return BacklogDocument(
                schemaVersion = maxOf(local.schemaVersion, remote.schemaVersion),
                entries = byId.values.sortedBy { it.addedAt },
            )
        }
    }
}
