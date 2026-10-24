package com.capivaraec.smwc.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import com.capivaraec.smwc.core.text.Links

/** Underlined links in the theme's gold, highlighted while pressed. */
@Composable
fun rememberLinkStyles(): TextLinkStyles {
    val colors = MaterialTheme.colorScheme
    return remember(colors.primary, colors.primaryContainer) {
        val link = SpanStyle(color = colors.primary, textDecoration = TextDecoration.Underline)
        TextLinkStyles(style = link, pressedStyle = link.copy(background = colors.primaryContainer))
    }
}

/** [html] with its `<a href>` links and any bare URLs in its text opening in the browser. */
@Composable
fun rememberHtmlWithLinks(html: String): AnnotatedString {
    val styles = rememberLinkStyles()
    return remember(html, styles) { AnnotatedString.fromHtml(html, linkStyles = styles).withBareLinks(styles) }
}

/** Plain text (BBCode `[url]` tags included) with its links opening in the browser. */
@Composable
fun rememberTextWithLinks(text: String): AnnotatedString {
    val styles = rememberLinkStyles()
    return remember(text, styles) { textWithLinks(text, styles) }
}

fun textWithLinks(text: String, styles: TextLinkStyles? = null): AnnotatedString {
    val linked = Links.parse(text)
    return AnnotatedString.Builder(linked.text).apply {
        linked.links.forEach { addLink(LinkAnnotation.Url(it.url, styles), it.start, it.end) }
    }.toAnnotatedString()
}

/** Links the URLs in this text that are not already part of a link. */
fun AnnotatedString.withBareLinks(styles: TextLinkStyles? = null): AnnotatedString {
    val existing = getLinkAnnotations(0, length)
    val bare = Links.findUrls(text).filter { url -> existing.none { it.start < url.end && url.start < it.end } }
    if (bare.isEmpty()) return this
    return AnnotatedString.Builder(this).apply {
        bare.forEach { addLink(LinkAnnotation.Url(it.url, styles), it.start, it.end) }
    }.toAnnotatedString()
}
