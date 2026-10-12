package com.capivaraec.smwc.core.ra

import com.capivaraec.smwc.core.backlog.BacklogStatus
import java.text.Collator
import java.util.Locale

/** Ways to order the Achievements tab. */
enum class AchievementSort(val label: String) {
    NAME_A_TO_Z("Name: A to Z"),
    NAME_Z_TO_A("Name: Z to A"),
    ACHIEVEMENTS_FEWEST_FIRST("Achievements: fewest first"),
    ACHIEVEMENTS_MOST_FIRST("Achievements: most first"),
    POINTS_FEWEST_FIRST("Points: fewest first"),
    POINTS_MOST_FIRST("Points: most first"),
    ;

    /** Orders items by their [game] and [name]; ties are broken by name. */
    fun <T> comparator(name: (T) -> String, game: (T) -> RaGame): Comparator<T> {
        val byName = Comparator<T> { a, b -> nameOrder.compare(name(a), name(b)) }
        return when (this) {
            NAME_A_TO_Z -> byName
            NAME_Z_TO_A -> byName.reversed()
            ACHIEVEMENTS_FEWEST_FIRST -> compareBy<T> { game(it).achievements }.then(byName)
            ACHIEVEMENTS_MOST_FIRST -> compareByDescending<T> { game(it).achievements }.then(byName)
            POINTS_FEWEST_FIRST -> compareBy<T> { game(it).points }.then(byName)
            POINTS_MOST_FIRST -> compareByDescending<T> { game(it).points }.then(byName)
        }
    }

    companion object {
        val DEFAULT = NAME_A_TO_Z

        /** Alphabetical, ignoring case and accents. */
        private val nameOrder = Collator.getInstance(Locale.ROOT).apply { strength = Collator.PRIMARY }
    }
}

enum class ListFilter(val label: String, val status: BacklogStatus?) {
    ANY("Any", null),
    BACKLOG("Backlog", BacklogStatus.BACKLOG),
    PLAYING("Playing", BacklogStatus.PLAYING),
    COMPLETED("Completed", BacklogStatus.COMPLETED),
}

enum class MasteredFilter(val label: String) {
    ANY("Any"),
    YES("Yes"),
    NO("No"),
}

/** Filters for the Achievements tab. */
data class AchievementFilter(
    /** Which of the user's lists the hack is in. */
    val list: ListFilter = ListFilter.ANY,
    val mastered: MasteredFilter = MasteredFilter.ANY,
) {
    val activeCount: Int get() = listOf(list != ListFilter.ANY, mastered != MasteredFilter.ANY).count { it }
    val isActive: Boolean get() = activeCount > 0

    /** [progress] null (not loaded yet) is left out while the mastered filter is on. */
    fun matches(status: BacklogStatus, progress: RaProgress?): Boolean {
        if (list.status != null && status != list.status) return false
        return when (mastered) {
            MasteredFilter.ANY -> true
            MasteredFilter.YES -> progress?.mastered == true
            MasteredFilter.NO -> progress?.mastered == false
        }
    }
}

/** Mastered, as RetroAchievements defines it: every achievement unlocked in hardcore mode. */
val RaProgress.mastered: Boolean get() = achievements.isNotEmpty() && earnedHardcore == achievements.size

/**
 * The achievements to list on a set's screen, unlocked first. [lockedOnly] leaves out the ones
 * already unlocked (in softcore or hardcore).
 */
fun RaProgress.shownAchievements(lockedOnly: Boolean): List<RaAchievement> =
    if (lockedOnly) achievements.filterNot { it.earned } else achievements.sortedByDescending { it.earned }
