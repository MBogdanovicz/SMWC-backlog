package com.capivaraec.smwc.core

import com.capivaraec.smwc.core.text.Links
import com.capivaraec.smwc.core.text.TextLink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LinksTest {
    private fun urls(text: String) = Links.findUrls(text).map { it.url }
    private fun linkedTexts(text: String) = Links.findUrls(text).map { text.substring(it.start, it.end) }

    @Test
    fun findsBareUrls() {
        val text = "Video: https://www.youtube.com/watch?v=V05rynulXDU and http://example.com/a_b-c"
        assertEquals(listOf("https://www.youtube.com/watch?v=V05rynulXDU", "http://example.com/a_b-c"), urls(text))
        assertEquals(urls(text), linkedTexts(text))
    }

    @Test
    fun leavesOutTrailingPunctuation() {
        assertEquals(listOf("https://a.com/x"), urls("See https://a.com/x."))
        assertEquals(listOf("https://a.com/x"), urls("Wow https://a.com/x!"))
        assertEquals(listOf("https://a.com/x", "https://b.com"), urls("https://a.com/x, https://b.com?"))
        assertEquals(listOf("https://a.com/x"), urls("(see https://a.com/x)"))
        assertEquals(listOf("https://a.com/x"), urls("(see https://a.com/x)."))
        assertEquals(listOf("https://a.com/x"), urls("\"https://a.com/x\""))
    }

    @Test
    fun keepsBalancedParentheses() {
        assertEquals(listOf("https://en.wikipedia.org/wiki/Kaizo_(series)"), urls("https://en.wikipedia.org/wiki/Kaizo_(series)"))
        assertEquals(listOf("https://en.wikipedia.org/wiki/Kaizo_(series)"), urls("(https://en.wikipedia.org/wiki/Kaizo_(series))"))
    }

    @Test
    fun linksWwwUrlsOverHttps() {
        val links = Links.findUrls("go to www.smwcentral.net/?p=section now")
        assertEquals(listOf(TextLink(6, 35, "https://www.smwcentral.net/?p=section")), links)
    }

    @Test
    fun doesNotLinkWwwInsideOtherWordsOrUrls() {
        assertEquals(listOf("http://www.a.com"), urls("http://www.a.com"))
        assertTrue(urls("mail me at me@www.a.com or awww.yes").isEmpty())
        assertTrue(urls("just https:// or www. alone").isEmpty())
    }

    @Test
    fun replacesBbCodeLinksWithTheirLabel() {
        val linked = Links.parse("Drawn by [url=https://annalavellan.de]AnnaLavellan[/url], see [URL]www.a.com[/URL] or https://b.com.")
        assertEquals("Drawn by AnnaLavellan, see www.a.com or https://b.com.", linked.text)
        assertEquals(
            listOf("AnnaLavellan" to "https://annalavellan.de", "www.a.com" to "https://www.a.com", "https://b.com" to "https://b.com"),
            linked.links.map { linked.text.substring(it.start, it.end) to it.url },
        )
    }

    @Test
    fun keepsNonWebBbCodeAsText() {
        val linked = Links.parse("[url=javascript:alert(1)]x[/url]")
        assertEquals("[url=javascript:alert(1)]x[/url]", linked.text)
        assertTrue(linked.links.isEmpty())
    }
}
