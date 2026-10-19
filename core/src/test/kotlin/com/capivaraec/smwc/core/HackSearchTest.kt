package com.capivaraec.smwc.core

import com.capivaraec.smwc.core.api.HackSearch
import com.capivaraec.smwc.core.api.RateLimiter
import com.capivaraec.smwc.core.api.SearchText
import com.capivaraec.smwc.core.api.SmwcClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HackSearchTest {
    @Test
    fun wordsIgnorePunctuationAccentsAndCase() {
        assertEquals(listOf("cube", "s", "kaizo", "world"), SearchText.words("Cube´s Kaizo World"))
        assertEquals(listOf("ja", "tack"), SearchText.words("Ja, tack!"))
        assertEquals(listOf("pokemon"), SearchText.words("Pokémon"))
    }

    @Test
    fun matchingNeedsEveryWordInOrder() {
        assertTrue(SearchText.matches("Cube´s Kaizo World", SearchText.words("Cube's Kaizo World")))
        assertTrue(SearchText.matches("Super \"Sonic Saves the World\" World", SearchText.words("Super Sonic Saves the World")))
        assertTrue(SearchText.matches("Ja, tack!", SearchText.words("Ja Tack")))
        assertTrue(SearchText.matches("Ja, tack!", SearchText.words("ja ta"))) // last word still being typed
        assertFalse(SearchText.matches("Attack of the Koopas", SearchText.words("Ja Tack")))
        assertFalse(SearchText.matches("Tack, ja!", SearchText.words("Ja Tack")))
        assertTrue(SearchText.matches("Doomscroll", SearchText.words("Doom scroll")))
        assertTrue(SearchText.matches("Doomscroll 2", SearchText.words("doom-scroll")))
        assertFalse(SearchText.matches("Doom Castle", SearchText.words("Doom scroll")))
    }

    @Test
    fun fallbackTermsGoFromLongestToShortest() {
        assertEquals(
            listOf("Cube s Kaizo World", "s Kaizo World", "Cube s Kaizo", "Kaizo World", "s Kaizo", "Cube s", "Kaizo", "World", "Cube"),
            SearchText.fallbackTerms("Cube's Kaizo World"),
        )
        assertEquals(listOf("JaTack", "Tack"), SearchText.fallbackTerms("Ja Tack"))
        assertEquals(listOf("Doomscroll", "scroll", "Doom"), SearchText.fallbackTerms("Doom scroll"))
    }

    /**
     * A fake SMW Central that matches names (or, for author searches, the [authors] of each name)
     * by plain substring, 2 hacks per page. Author requests are logged as "by:term#page".
     */
    private fun server(names: List<String>, requests: MutableList<String>, authors: Map<String, String> = emptyMap()) = SmwcClient(
        HttpClient(MockEngine { request ->
            val author = request.url.parameters["f[author]"]
            val term = author ?: request.url.parameters["f[name]"].orEmpty()
            val page = request.url.parameters["n"]!!.toInt()
            requests += (if (author != null) "by:" else "") + "$term#$page"
            val hits = names.filter { (if (author != null) authors[it].orEmpty() else it).contains(term, ignoreCase = true) }
            val lastPage = maxOf(1, (hits.size + 1) / 2)
            val body = buildJsonObject {
                put("current_page", page)
                put("last_page", lastPage)
                put("data", buildJsonArray {
                    hits.drop((page - 1) * 2).take(2).forEach { name ->
                        add(buildJsonObject { put("id", names.indexOf(name) + 1); put("name", name) })
                    }
                })
            }
            respond(body.toString())
        }),
        RateLimiter(0),
    )

    @Test
    fun exactTextIsTriedFirst() = runTest {
        val requests = mutableListOf<String>()
        val result = HackSearch(server(listOf("Kaizo World", "Other"), requests)).search("Kaizo")

        assertEquals(listOf("Kaizo World"), result.items.map { it.name })
        assertFalse(result.approximate)
        assertEquals(listOf("Kaizo#1", "by:Kaizo#1"), requests)
    }

    @Test
    fun punctuationIsIgnoredWhenTheExactTextFindsNothing() = runTest {
        val requests = mutableListOf<String>()
        val result = HackSearch(server(listOf("Ja, tack!", "Other"), requests)).search("Ja Tack")

        assertEquals(listOf("Ja, tack!"), result.items.map { it.name })
        assertTrue(result.approximate)
        assertEquals(listOf("Ja Tack#1", "by:Ja Tack#1", "JaTack#1", "Tack#1"), requests)
    }

    @Test
    fun wordsTypedApartFindAOneWordName() = runTest {
        val requests = mutableListOf<String>()
        val result = HackSearch(server(listOf("Doomscroll", "Doom Castle"), requests)).search("Doom scroll")

        assertEquals(listOf("Doomscroll"), result.items.map { it.name })
        assertEquals(listOf("Doom scroll#1", "by:Doom scroll#1", "Doomscroll#1"), requests)
    }

    @Test
    fun filteredPagesAreScannedUntilAMatch() = runTest {
        val requests = mutableListOf<String>()
        val names = listOf("Attack 1", "Attack 2", "Attack 3", "Attack 4", "Ja, tack!")
        val result = HackSearch(server(names, requests)).search("Ja Tack")

        assertEquals(listOf("Ja, tack!"), result.items.map { it.name })
        assertEquals(listOf("Ja Tack#1", "by:Ja Tack#1", "JaTack#1", "Tack#1", "Tack#2", "Tack#3"), requests)
        assertNull(result.next)
    }

    @Test
    fun fallbackStopsAtTheRequestBudget() = runTest {
        val requests = mutableListOf<String>()
        val result = HackSearch(server(listOf("Nothing alike"), requests), maxFallbackRequests = 3)
            .search("one two three four")

        assertTrue(result.items.isEmpty())
        assertEquals(2 + 3, requests.size)
    }

    @Test
    fun hacksByAnAuthorFollowTheNameMatches() = runTest {
        val requests = mutableListOf<String>()
        val names = listOf("Barbarian Quest", "Grand Poo World", "Grand Poo World 2", "Other")
        val authors = mapOf("Grand Poo World" to "Barbarian", "Grand Poo World 2" to "Barbarian", "Barbarian Quest" to "Someone")
        val result = HackSearch(server(names, requests, authors)).search("barbarian")

        assertEquals(listOf("Barbarian Quest", "Grand Poo World", "Grand Poo World 2"), result.items.map { it.name })
        assertFalse(result.approximate)
        assertNull(result.next)
        assertEquals(listOf("barbarian#1", "by:barbarian#1"), requests)
    }

    @Test
    fun authorPagesContinueAfterTheNamePages() = runTest {
        val requests = mutableListOf<String>()
        val names = listOf("Kaizo 1", "Kaizo 2", "Kaizo 3", "A", "B", "C")
        val authors = names.drop(3).associateWith { "Kaizo Author" }
        val search = HackSearch(server(names, requests, authors))

        val first = search.search("Kaizo")
        assertEquals(listOf("Kaizo 1", "Kaizo 2", "A", "B"), first.items.map { it.name })
        val second = search.loadMore(first.next!!, "Kaizo")
        assertEquals(listOf("Kaizo 3"), second.items.map { it.name })
        val third = search.loadMore(second.next!!, "Kaizo")
        assertEquals(listOf("C"), third.items.map { it.name })
        assertNull(third.next)
        assertEquals(listOf("Kaizo#1", "by:Kaizo#1", "Kaizo#2", "by:Kaizo#2"), requests)
    }
}
