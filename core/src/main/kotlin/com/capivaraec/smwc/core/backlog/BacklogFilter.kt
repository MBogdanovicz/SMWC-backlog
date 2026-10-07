package com.capivaraec.smwc.core.backlog

import java.time.Instant
import java.time.ZoneId

enum class RaSetFilter(val label: String) {
    ANY("Any"),
    WITH_SET("Has a set"),
    WITHOUT_SET("No set"),
}

/**
 * Filters for the backlog lists. Empty sets and nulls mean "any". An entry without the value a
 * filter needs (e.g. a hack added by name has no difficulty) is left out while that filter is on.
 */
data class BacklogFilter(
    /** Raw SMW Central difficulty ids, e.g. "diff_5". */
    val difficulties: Set<String> = emptySet(),
    /** Minimum SMW Central rating (0–5). */
    val minRating: Double? = null,
    val raSet: RaSetFilter = RaSetFilter.ANY,
    val releaseYears: Set<Int> = emptySet(),
) {
    val activeCount: Int
        get() = listOf(difficulties.isNotEmpty(), minRating != null, raSet != RaSetFilter.ANY, releaseYears.isNotEmpty()).count { it }

    val isActive: Boolean get() = activeCount > 0

    /** [hasRaSet] tells whether the entry has a RetroAchievements set. */
    fun matches(entry: BacklogEntry, hasRaSet: Boolean, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if (difficulties.isNotEmpty() && entry.difficulty !in difficulties) return false
        if (minRating != null && (entry.rating ?: return false) < minRating) return false
        when (raSet) {
            RaSetFilter.ANY -> Unit
            RaSetFilter.WITH_SET -> if (!hasRaSet) return false
            RaSetFilter.WITHOUT_SET -> if (hasRaSet) return false
        }
        if (releaseYears.isNotEmpty() && entry.releaseYear(zone) !in releaseYears) return false
        return true
    }

    companion object {
        /** Minimum ratings offered as filter choices. */
        val RATING_STEPS = listOf(3.0, 3.5, 4.0, 4.5)
    }
}

/** The year SMW Central accepted the hack, in [zone]; null if unknown. */
fun BacklogEntry.releaseYear(zone: ZoneId = ZoneId.systemDefault()): Int? =
    acceptedAt?.let { Instant.ofEpochSecond(it).atZone(zone).year }
