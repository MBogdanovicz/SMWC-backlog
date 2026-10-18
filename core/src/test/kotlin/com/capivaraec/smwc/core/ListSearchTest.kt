package com.capivaraec.smwc.core

import com.capivaraec.smwc.core.backlog.BacklogEntry
import com.capivaraec.smwc.core.backlog.matchesSearch
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ListSearchTest {
    private val entry = BacklogEntry(
        hackId = 1,
        name = "Super Mario Échec World",
        authors = listOf("Cube", "Lazy"),
        addedAt = 0,
        updatedAt = 0,
    )

    @Test
    fun blankQueryMatchesEverything() {
        assertTrue(entry.matchesSearch(""))
        assertTrue(entry.matchesSearch("   "))
    }

    @Test
    fun matchesNameIgnoringCaseAndAccents() {
        assertTrue(entry.matchesSearch("mario"))
        assertTrue(entry.matchesSearch("ECHEC"))
        assertTrue(entry.matchesSearch("éch"))
        assertFalse(entry.matchesSearch("kaizo"))
    }

    @Test
    fun matchesAuthors() {
        assertTrue(entry.matchesSearch("cube"))
        assertFalse(entry.matchesSearch("someone"))
    }

    @Test
    fun everyWordMustMatchSomewhere() {
        assertTrue(entry.matchesSearch("  world   cube "))
        assertFalse(entry.matchesSearch("world kaizo"))
    }
}
