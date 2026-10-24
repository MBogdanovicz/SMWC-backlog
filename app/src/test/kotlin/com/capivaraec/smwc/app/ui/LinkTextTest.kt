package com.capivaraec.smwc.app.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import kotlin.test.Test
import kotlin.test.assertEquals

class LinkTextTest {
    private fun AnnotatedString.links() =
        getLinkAnnotations(0, length).map { text.substring(it.start, it.end) to (it.item as LinkAnnotation.Url).url }

    @Test
    fun linksCommentUrls() {
        val text = textWithLinks("Route: https://youtu.be/abc). Also [url=https://a.com]this guide[/url]!")

        assertEquals("Route: https://youtu.be/abc). Also this guide!", text.text)
        assertEquals(listOf("https://youtu.be/abc" to "https://youtu.be/abc", "this guide" to "https://a.com"), text.links())
    }

    @Test
    fun addsBareLinksWithoutTouchingExistingOnes() {
        val html = AnnotatedString.Builder("Trailer https://b.com and www.c.com").apply {
            addLink(LinkAnnotation.Url("https://a.com"), 0, 7)
            addLink(LinkAnnotation.Url("https://b.com/other"), 8, 21)
        }.toAnnotatedString()

        assertEquals(
            listOf("Trailer" to "https://a.com", "https://b.com" to "https://b.com/other", "www.c.com" to "https://www.c.com"),
            html.withBareLinks().links().sortedBy { html.text.indexOf(it.first) },
        )
    }
}
