package com.capivaraec.smwc.core.backlog

import java.text.Normalizer

/**
 * Local search for the user's lists: every word of [query] must appear in the hack's name or one of
 * its authors. Case and accents are ignored. A blank query matches everything.
 */
fun BacklogEntry.matchesSearch(query: String): Boolean {
    val words = normalize(query).split(' ').filter { it.isNotEmpty() }
    if (words.isEmpty()) return true
    val haystack = (listOf(name) + authors).map(::normalize)
    return words.all { word -> haystack.any { word in it } }
}

/** Lower case, without accents and with runs of spaces collapsed ("  Écho " → "echo"). */
private fun normalize(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .replace(Regex("\\s+"), " ")
        .trim()
