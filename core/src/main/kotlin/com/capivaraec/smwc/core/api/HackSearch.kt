package com.capivaraec.smwc.core.api

import com.capivaraec.smwc.core.model.HackSummary
import java.text.Normalizer

/** Where to continue a search: the server term, its next page, and whether results are filtered. */
data class SearchCursor(val term: String, val page: Int, val filtered: Boolean)

data class SearchResult(
    val items: List<HackSummary>,
    /** Null when there is nothing more to load. */
    val next: SearchCursor?,
    /** True when nothing matched the exact text and these are close matches (punctuation ignored). */
    val approximate: Boolean = false,
)

/**
 * Search that tolerates punctuation. SMW Central's name filter only matches the exact text as a
 * substring, so "Ja Tack" misses "Ja, tack!" and "Cube's" misses "Cube´s". When the exact text
 * finds nothing, this asks the server for smaller pieces of the query (longest first) and keeps
 * the names that contain every typed word in order, ignoring case, accents and punctuation.
 */
class HackSearch(
    private val client: SmwcClient,
    /** Extra requests allowed per search or "load more", to stay under the rate limit. */
    private val maxFallbackRequests: Int = 6,
) {
    suspend fun search(query: String): SearchResult {
        val q = query.trim()
        val exact = client.search(q, page = 1)
        if (exact.items.isNotEmpty() || q.isEmpty()) {
            return SearchResult(exact.items, if (exact.hasNext) SearchCursor(q, 2, filtered = false) else null)
        }
        val words = SearchText.words(q)
        if (words.isEmpty()) return SearchResult(emptyList(), null)

        var budget = maxFallbackRequests
        for (term in SearchText.fallbackTerms(q)) {
            if (budget <= 0) break
            val (items, next, used) = scan(term, startPage = 1, words = words, budget = budget)
            budget -= used
            if (items.isNotEmpty()) return SearchResult(items, next, approximate = true)
        }
        return SearchResult(emptyList(), null)
    }

    suspend fun loadMore(cursor: SearchCursor, query: String): SearchResult {
        if (!cursor.filtered) {
            val page = client.search(cursor.term, cursor.page)
            return SearchResult(page.items, if (page.hasNext) cursor.copy(page = page.page + 1) else null)
        }
        val (items, next, _) = scan(cursor.term, cursor.page, SearchText.words(query), maxFallbackRequests)
        return SearchResult(items, next, approximate = true)
    }

    private data class Scan(val items: List<HackSummary>, val next: SearchCursor?, val requests: Int)

    /** Reads pages of [term] until some names match [words], the pages end, or [budget] runs out. */
    private suspend fun scan(term: String, startPage: Int, words: List<String>, budget: Int): Scan {
        var page = startPage
        var requests = 0
        while (requests < budget) {
            val result = client.search(term, page)
            requests++
            val matches = result.items.filter { SearchText.matches(it.name, words) }
            val next = if (result.hasNext) SearchCursor(term, result.page + 1, filtered = true) else null
            if (matches.isNotEmpty() || next == null) return Scan(matches, next, requests)
            page = next.page
        }
        return Scan(emptyList(), SearchCursor(term, page, filtered = true), requests)
    }
}

/** Text helpers for [HackSearch]. */
object SearchText {
    private val marks = Regex("\\p{M}+")
    private val separators = Regex("[^\\p{L}\\p{N}]+")

    /** Lowercase words without accents; any punctuation separates words. */
    fun words(text: String): List<String> =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(marks, "").lowercase()
            .split(separators).filter { it.isNotEmpty() }

    /**
     * True if every query word appears in [name] in the same order (the last word may be the
     * start of a name word, as when the user has not finished typing it), or if the query
     * without spaces is part of the name without spaces ("Doom scroll" finds "Doomscroll").
     */
    fun matches(name: String, queryWords: List<String>): Boolean {
        if (queryWords.isEmpty()) return true
        val nameWords = words(name)
        if (queryWords.joinToString("") in nameWords.joinToString("")) return true
        var i = 0
        for (word in nameWords) {
            val wanted = queryWords[i]
            val last = i == queryWords.lastIndex
            if (word == wanted || (last && word.startsWith(wanted))) {
                i++
                if (i == queryWords.size) return true
            }
        }
        return false
    }

    /**
     * Pieces of the query to send to the server when the whole text finds nothing: first, for 2
     * or 3 words, the words joined without spaces (the name may be one word), then runs of consecutive words,
     * longest first (more words means fewer, closer results), skipping pieces shorter than 3
     * characters.
     */
    fun fallbackTerms(query: String): List<String> {
        val words = query.trim().split(separators).filter { it.isNotEmpty() }
        val terms = LinkedHashMap<String, String>() // lowercase -> term, keeps the first spelling
        // Only for 2 or 3 words: longer queries are rarely a single word, and each try costs a request.
        if (words.size in 2..3) words.joinToString("").let { terms[it.lowercase()] = it }
        for (size in words.size downTo 1) {
            (0..words.size - size)
                .map { start -> words.subList(start, start + size).joinToString(" ") }
                .sortedByDescending { it.length }
                .forEach { if (it.length >= 3) terms.putIfAbsent(it.lowercase(), it) }
        }
        terms.remove(query.trim().lowercase())
        return terms.values.toList()
    }
}
