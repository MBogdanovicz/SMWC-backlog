package com.capivaraec.smwc.core.backlog

import java.text.Collator
import java.util.Locale

/** Ways to order the backlog lists. Entries without the value (e.g. added by name) go last. */
enum class BacklogSort(val label: String) {
    ADDED_OLDEST_FIRST("Added: oldest first"),
    ADDED_NEWEST_FIRST("Added: newest first"),
    NAME_A_TO_Z("Name: A to Z"),
    NAME_Z_TO_A("Name: Z to A"),
    RELEASED_OLDEST_FIRST("Released: oldest first"),
    RELEASED_NEWEST_FIRST("Released: newest first"),
    EXITS_FEWEST_FIRST("Exits: fewest first"),
    EXITS_MOST_FIRST("Exits: most first"),
    TIME_TO_MASTER_SHORTEST_FIRST("Time to master: shortest first"),
    TIME_TO_MASTER_LONGEST_FIRST("Time to master: longest first"),
    ;

    val byMasterTime: Boolean get() = this == TIME_TO_MASTER_SHORTEST_FIRST || this == TIME_TO_MASTER_LONGEST_FIRST

    companion object {
        val DEFAULT = ADDED_OLDEST_FIRST
    }
}

/** Alphabetical, ignoring case and accents ("Échec" sorts with "E"). */
private val nameOrder = Collator.getInstance(Locale.ROOT).apply { strength = Collator.PRIMARY }

/** [masterSeconds] is the time to master a hack (from RetroAchievements), for the time sorts. */
fun List<BacklogEntry>.sortedWith(sort: BacklogSort, masterSeconds: (BacklogEntry) -> Long? = { null }): List<BacklogEntry> {
    val byAdded = compareBy<BacklogEntry> { it.addedAt }
    val byName = Comparator<BacklogEntry> { a, b -> nameOrder.compare(a.name, b.name) }
    // Missing values sort last in both directions; ties keep the order they were added in.
    fun <T : Comparable<T>> by(descending: Boolean, value: (BacklogEntry) -> T?): Comparator<BacklogEntry> =
        compareBy<BacklogEntry> { value(it) == null }
            .then(if (descending) compareByDescending(value) else compareBy(value))
            .then(byAdded)
    val comparator = when (sort) {
        BacklogSort.ADDED_OLDEST_FIRST -> byAdded
        BacklogSort.ADDED_NEWEST_FIRST -> compareByDescending<BacklogEntry> { it.addedAt }
        BacklogSort.NAME_A_TO_Z -> byName.then(byAdded)
        BacklogSort.NAME_Z_TO_A -> byName.reversed().then(byAdded)
        BacklogSort.RELEASED_OLDEST_FIRST -> by(descending = false) { it.acceptedAt }
        BacklogSort.RELEASED_NEWEST_FIRST -> by(descending = true) { it.acceptedAt }
        BacklogSort.EXITS_FEWEST_FIRST -> by(descending = false) { it.exits }
        BacklogSort.EXITS_MOST_FIRST -> by(descending = true) { it.exits }
        BacklogSort.TIME_TO_MASTER_SHORTEST_FIRST -> by(descending = false, masterSeconds)
        BacklogSort.TIME_TO_MASTER_LONGEST_FIRST -> by(descending = true, masterSeconds)
    }
    return sortedWith(comparator)
}
