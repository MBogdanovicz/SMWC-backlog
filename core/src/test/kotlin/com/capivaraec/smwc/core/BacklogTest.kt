package com.capivaraec.smwc.core

import com.capivaraec.smwc.core.backlog.BacklogDocument
import com.capivaraec.smwc.core.backlog.RaSetFilter
import com.capivaraec.smwc.core.backlog.BacklogFilter
import com.capivaraec.smwc.core.backlog.BacklogSort
import com.capivaraec.smwc.core.backlog.BacklogStatus
import com.capivaraec.smwc.core.backlog.sortedWith
import com.capivaraec.smwc.core.model.HackSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BacklogTest {
    private fun hack(id: Long) = HackSummary(id, "Hack $id", listOf("Autor"), "Standard: Easy", null, null, null, null)

    @Test
    fun addCompleteAndRemove() {
        var doc = BacklogDocument().add(hack(1), now = 10).add(hack(2), now = 20)
        assertEquals(listOf(1L, 2L), doc.visible.map { it.hackId })

        doc = doc.markCompleted(1, now = 30)
        assertEquals(BacklogStatus.COMPLETED, doc.entry(1)!!.status)
        assertEquals(30, doc.entry(1)!!.completedAt)

        doc = doc.markBacklog(1, now = 35)
        assertNull(doc.entry(1)!!.completedAt)

        doc = doc.remove(2, now = 40)
        assertEquals(listOf(1L), doc.visible.map { it.hackId })
        assertTrue(doc.entries.single { it.hackId == 2L }.deleted)

        // Adding again revives a removed hack.
        doc = doc.add(hack(2), now = 50)
        assertEquals(listOf(1L, 2L), doc.visible.map { it.hackId }.sorted())
    }

    @Test
    fun addingTwiceKeepsOriginalEntry() {
        val doc = BacklogDocument().add(hack(1), now = 10)
        assertEquals(doc, doc.add(hack(1), now = 99))
    }

    @Test
    fun mergeKeepsNewestEntryPerHack() {
        val base = BacklogDocument().add(hack(1), 10).add(hack(2), 10)
        val phone = base.markCompleted(1, 50).add(hack(3), 60)
        val drive = base.remove(1, 40).remove(2, 70)

        val merged = BacklogDocument.merge(local = phone, remote = drive)

        assertEquals(BacklogStatus.COMPLETED, merged.entry(1)!!.status) // phone change is newer
        assertNull(merged.entry(2)) // removal from the other device survives
        assertEquals("Hack 3", merged.entry(3)!!.name)
    }

    @Test
    fun jsonRoundTrip() {
        val doc = BacklogDocument().add(hack(1), 10).markCompleted(1, 20)
        val json = doc.toJson()
        assertTrue(json.contains("\"completed\""))
        assertEquals(doc, BacklogDocument.fromJson(json))
    }

    @Test
    fun hacksNotOnSmwCentralAreAddedByName() {
        var doc = BacklogDocument().addCustom("  My Secret Hack ", BacklogStatus.BACKLOG, now = 10)
        val entry = doc.visible.single()
        assertTrue(entry.custom)
        assertTrue(entry.hackId < 0)
        assertEquals("My Secret Hack", entry.name)

        // Same name, other spacing or case: the same entry, so nothing changes.
        assertEquals(doc, doc.addCustom("my  secret hack", BacklogStatus.COMPLETED, now = 20))

        doc = doc.addCustom("Finished One", BacklogStatus.COMPLETED, now = 30)
        assertEquals(30, doc.visible.first { it.name == "Finished One" }.completedAt)
    }

    @Test
    fun customIdsMatchAcrossDevicesAndSurviveJson() {
        val a = BacklogDocument().addCustom("Doom Hack", BacklogStatus.BACKLOG, now = 10)
        val b = BacklogDocument().addCustom("doom hack", BacklogStatus.BACKLOG, now = 20)
        assertEquals(1, BacklogDocument.merge(a, b).visible.size)

        val restored = BacklogDocument.fromJson(a.toJson())
        assertTrue(restored.visible.single().custom)
    }

    @Test
    fun oldJsonWithoutTheCustomFieldStillLoads() {
        val json = """{"schemaVersion":1,"entries":[{"hackId":5,"name":"Old","addedAt":1,"updatedAt":1}]}"""
        assertEquals(false, BacklogDocument.fromJson(json).visible.single().custom)
    }

    private fun released(id: Long, acceptedAt: Long?, exits: Int?) =
        HackSummary(id, "Hack $id", emptyList(), null, exits?.toString(), null, null, null, acceptedAt = acceptedAt)

    @Test
    fun addStoresReleaseDateAndExits() {
        val entry = BacklogDocument().add(released(1, acceptedAt = 500, exits = 14), now = 10).entry(1)!!
        assertEquals(500, entry.acceptedAt)
        assertEquals(14, entry.exits)
    }

    @Test
    fun detailsAreFilledInWithoutCountingAsAChange() {
        val old = BacklogDocument().add(hack(1), now = 10)
        val filled = old.withDetails(released(1, acceptedAt = 500, exits = 3), now = 99)
        assertEquals(500, filled.entry(1)!!.acceptedAt)
        assertEquals(99, filled.entry(1)!!.detailsFetchedAt)
        assertEquals(10, filled.entry(1)!!.updatedAt)
    }

    @Test
    fun sortOptions() {
        val doc = BacklogDocument()
            .add(released(1, acceptedAt = 300, exits = 5), now = 10)
            .add(released(2, acceptedAt = 100, exits = 50), now = 20)
            .addCustom("By name", BacklogStatus.BACKLOG, now = 30)
            .add(released(3, acceptedAt = 200, exits = 1), now = 40)
        val custom = doc.visible.first { it.custom }.hackId
        fun order(sort: BacklogSort) = doc.visible.sortedWith(sort).map { it.hackId }

        assertEquals(listOf(1L, 2L, custom, 3L), order(BacklogSort.ADDED_OLDEST_FIRST))
        assertEquals(listOf(3L, custom, 2L, 1L), order(BacklogSort.ADDED_NEWEST_FIRST))
        assertEquals(listOf(2L, 3L, 1L, custom), order(BacklogSort.RELEASED_OLDEST_FIRST))
        assertEquals(listOf(1L, 3L, 2L, custom), order(BacklogSort.RELEASED_NEWEST_FIRST))
        assertEquals(listOf(3L, 1L, 2L, custom), order(BacklogSort.EXITS_FEWEST_FIRST))
        assertEquals(listOf(2L, 1L, 3L, custom), order(BacklogSort.EXITS_MOST_FIRST))
    }

    @Test
    fun alphabeticalSortIgnoresCaseAndAccents() {
        val doc = BacklogDocument()
            .addCustom("banana", BacklogStatus.BACKLOG, now = 10)
            .addCustom("Échec", BacklogStatus.BACKLOG, now = 20)
            .addCustom("apple", BacklogStatus.BACKLOG, now = 30)
            .addCustom("Zelda", BacklogStatus.BACKLOG, now = 40)
        assertEquals(listOf("apple", "banana", "Échec", "Zelda"), doc.visible.sortedWith(BacklogSort.NAME_A_TO_Z).map { it.name })
        assertEquals(listOf("Zelda", "Échec", "banana", "apple"), doc.visible.sortedWith(BacklogSort.NAME_Z_TO_A).map { it.name })
    }

    @Test
    fun exitCountReadsTheLeadingNumber() {
        assertEquals(14, com.capivaraec.smwc.core.model.exitCount("14 exit(s)"))
        assertEquals(56, com.capivaraec.smwc.core.model.exitCount("56"))
        assertNull(com.capivaraec.smwc.core.model.exitCount("unknown"))
    }

    @Test
    fun filters() {
        val utc = java.time.ZoneOffset.UTC
        val year2020 = 1_590_000_000L // May 2020
        val year2024 = 1_710_000_000L // March 2024
        fun entry(difficulty: String?, rating: Double?, acceptedAt: Long?) =
            BacklogDocument().add(HackSummary(1, "H", emptyList(), difficulty, null, rating, null, null, acceptedAt = acceptedAt), now = 1).entry(1)!!
        val expert2024 = entry("diff_5", 4.6, year2024)
        val casual2020 = entry("diff_2", 3.2, year2020)
        val unrated = entry("diff_5", null, null)

        assertTrue(BacklogFilter().matches(unrated, hasRaSet = false, zone = utc))

        val experts = BacklogFilter(difficulties = setOf("diff_5"))
        assertTrue(experts.matches(expert2024, false, utc))
        assertFalse(experts.matches(casual2020, false, utc))

        val good = BacklogFilter(minRating = 4.0)
        assertTrue(good.matches(expert2024, false, utc))
        assertFalse(good.matches(casual2020, false, utc))
        assertFalse(good.matches(unrated, false, utc))

        assertTrue(BacklogFilter(raSet = RaSetFilter.WITH_SET).matches(casual2020, hasRaSet = true, zone = utc))
        assertFalse(BacklogFilter(raSet = RaSetFilter.WITH_SET).matches(casual2020, hasRaSet = false, zone = utc))
        assertTrue(BacklogFilter(raSet = RaSetFilter.WITHOUT_SET).matches(casual2020, hasRaSet = false, zone = utc))

        val years = BacklogFilter(releaseYears = setOf(2020, 2021))
        assertTrue(years.matches(casual2020, false, utc))
        assertFalse(years.matches(expert2024, false, utc))
        assertFalse(years.matches(unrated, false, utc))

        val combined = BacklogFilter(difficulties = setOf("diff_5"), minRating = 4.5, releaseYears = setOf(2024))
        assertEquals(3, combined.activeCount)
        assertTrue(combined.matches(expert2024, false, utc))
    }

    @Test
    fun playingStatusAndCompletionDate() {
        var doc = BacklogDocument().add(hack(1), now = 10)
        doc = doc.setStatus(1, BacklogStatus.PLAYING, now = 20)
        assertEquals(BacklogStatus.PLAYING, doc.entry(1)!!.status)
        assertNull(doc.entry(1)!!.completedAt)

        doc = doc.setStatus(1, BacklogStatus.COMPLETED, now = 30)
        assertEquals(30, doc.entry(1)!!.completedAt)
        doc = doc.setStatus(1, BacklogStatus.PLAYING, now = 40)
        assertNull(doc.entry(1)!!.completedAt)

        // Setting the same status again is not a change.
        assertEquals(doc, doc.setStatus(1, BacklogStatus.PLAYING, now = 50))
        assertEquals(doc, BacklogDocument.fromJson(doc.toJson()))
    }

    @Test
    fun notesAreTrimmedAndCountAsAChange() {
        var doc = BacklogDocument().add(hack(1), now = 10)
        doc = doc.setNotes(1, "  stopped at world 3  ", now = 20)
        assertEquals("stopped at world 3", doc.entry(1)!!.notes)
        assertEquals(20, doc.entry(1)!!.updatedAt)
        assertEquals(doc, doc.setNotes(1, "stopped at world 3", now = 30))
    }

    @Test
    fun newerVersionIsDetectedAndSwitchedTo() {
        var doc = BacklogDocument().add(hack(1), now = 10).setStatus(1, BacklogStatus.PLAYING, now = 15).setNotes(1, "notes", now = 16)
        doc = doc.withDetails(hack(1), now = 20, latestVersionId = 2)
        assertTrue(doc.entry(1)!!.hasNewerVersion)

        doc = doc.switchToVersion(1, released(2, acceptedAt = 900, exits = 20), now = 30)
        assertNull(doc.entry(1))
        val switched = doc.entry(2)!!
        assertEquals(BacklogStatus.PLAYING, switched.status)
        assertEquals("notes", switched.notes)
        assertEquals(10, switched.addedAt)
        assertEquals(900, switched.acceptedAt)
        assertFalse(switched.hasNewerVersion)
        // The old id stays as a tombstone, so other devices drop it on sync.
        assertTrue(doc.entries.first { it.hackId == 1L }.deleted)
    }

    @Test
    fun switchingWhenTheNewVersionIsAlreadyListedOnlyRemovesTheOld() {
        val doc = BacklogDocument().add(hack(1), now = 10).add(hack(2), now = 20).switchToVersion(1, hack(2), now = 30)
        assertEquals(listOf(2L), doc.visible.map { it.hackId })
        assertEquals(20, doc.entry(2)!!.addedAt)
    }
}
