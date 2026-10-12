package com.capivaraec.smwc.core

import com.capivaraec.smwc.core.backlog.BacklogStatus
import com.capivaraec.smwc.core.ra.AchievementFilter
import com.capivaraec.smwc.core.ra.AchievementSort
import com.capivaraec.smwc.core.ra.ListFilter
import com.capivaraec.smwc.core.ra.MasteredFilter
import com.capivaraec.smwc.core.ra.RaAchievement
import com.capivaraec.smwc.core.ra.RaGame
import com.capivaraec.smwc.core.ra.RaProgress
import com.capivaraec.smwc.core.ra.mastered
import com.capivaraec.smwc.core.ra.shownAchievements
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AchievementsListTest {
    private data class Item(val name: String, val game: RaGame)

    private fun item(name: String, achievements: Int, points: Int) =
        Item(name, RaGame(achievements.toLong(), "~Hack~ $name", achievements, points, iconUrl = null))

    private val items = listOf(item("banana", 40, 100), item("Échec", 10, 300), item("apple", 25, 200), item("cherry", 25, 50))

    private fun order(sort: AchievementSort) = items.sortedWith(sort.comparator(Item::name, Item::game)).map { it.name }

    @Test
    fun sortOptions() {
        assertEquals(listOf("apple", "banana", "cherry", "Échec"), order(AchievementSort.NAME_A_TO_Z))
        assertEquals(listOf("Échec", "cherry", "banana", "apple"), order(AchievementSort.NAME_Z_TO_A))
        assertEquals(listOf("Échec", "apple", "cherry", "banana"), order(AchievementSort.ACHIEVEMENTS_FEWEST_FIRST))
        assertEquals(listOf("banana", "apple", "cherry", "Échec"), order(AchievementSort.ACHIEVEMENTS_MOST_FIRST))
        assertEquals(listOf("cherry", "banana", "apple", "Échec"), order(AchievementSort.POINTS_FEWEST_FIRST))
        assertEquals(listOf("Échec", "apple", "banana", "cherry"), order(AchievementSort.POINTS_MOST_FIRST))
    }

    private fun progress(hardcore: Int, softcoreOnly: Int, locked: Int): RaProgress {
        var id = 0L
        fun a(soft: String?, hard: String?) = RaAchievement(id++, "A$id", null, 5, null, soft, hard)
        val list = List(hardcore) { a("d", "d") } + List(softcoreOnly) { a("d", null) } + List(locked) { a(null, null) }
        return RaProgress(RaGame(1, "~Hack~ X", list.size, 0, null), list)
    }

    @Test
    fun masteredMeansEveryAchievementInHardcore() {
        assertTrue(progress(hardcore = 3, softcoreOnly = 0, locked = 0).mastered)
        assertFalse(progress(hardcore = 2, softcoreOnly = 1, locked = 0).mastered)
        assertFalse(progress(hardcore = 2, softcoreOnly = 0, locked = 1).mastered)
    }

    @Test
    fun lockedOnlyHidesSoftcoreAndHardcoreUnlocks() {
        val progress = progress(hardcore = 1, softcoreOnly = 1, locked = 2)

        assertEquals(listOf(2L, 3L), progress.shownAchievements(lockedOnly = true).map { it.id })
        assertEquals(listOf(0L, 1L, 2L, 3L), progress.shownAchievements(lockedOnly = false).map { it.id })
        assertTrue(progress(hardcore = 2, softcoreOnly = 1, locked = 0).shownAchievements(lockedOnly = true).isEmpty())
    }

    @Test
    fun filters() {
        val mastered = progress(3, 0, 0)
        val partial = progress(1, 0, 2)

        assertTrue(AchievementFilter().matches(BacklogStatus.BACKLOG, null))
        assertTrue(AchievementFilter(list = ListFilter.COMPLETED).matches(BacklogStatus.COMPLETED, partial))
        assertFalse(AchievementFilter(list = ListFilter.COMPLETED).matches(BacklogStatus.BACKLOG, partial))
        assertTrue(AchievementFilter(mastered = MasteredFilter.YES).matches(BacklogStatus.BACKLOG, mastered))
        assertFalse(AchievementFilter(mastered = MasteredFilter.YES).matches(BacklogStatus.BACKLOG, partial))
        assertTrue(AchievementFilter(mastered = MasteredFilter.NO).matches(BacklogStatus.BACKLOG, partial))
        assertFalse(AchievementFilter(mastered = MasteredFilter.NO).matches(BacklogStatus.BACKLOG, null))
        assertEquals(2, AchievementFilter(ListFilter.BACKLOG, MasteredFilter.NO).activeCount)
    }
}
