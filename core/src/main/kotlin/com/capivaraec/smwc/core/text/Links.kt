package com.capivaraec.smwc.core.text

/** A link over the characters [start, end) of a text, opening [url]. */
data class TextLink(val start: Int, val end: Int, val url: String)

/** Text with its links, once any link markup has been taken out. */
data class LinkedText(val text: String, val links: List<TextLink>)

/** Finds the links in user-written text: bare URLs and BBCode `[url]` tags. */
object Links {
    // "www." only counts at the start of a word, so it does not match inside an "http://www." URL.
    private val urlPattern = Regex("""(?:\bhttps?://|(?<![\w./@-])www\.)[^\s<>"\[\]]+""", RegexOption.IGNORE_CASE)
    private val bbCodePattern = Regex("""\[url(?:=([^\]]+))?](.*?)\[/url]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private const val TRAILING_PUNCTUATION = ".,!?;:'\"*"

    /**
     * The `http(s)://` and `www.` URLs in [text], without the punctuation that usually follows a
     * link in a sentence (a closing parenthesis only when the URL has no matching opening one).
     */
    fun findUrls(text: String): List<TextLink> = urlPattern.findAll(text).mapNotNull { match ->
        val url = trimTrailing(match.value)
        if (url.endsWith("://") || url.equals("www.", ignoreCase = true)) return@mapNotNull null
        TextLink(match.range.first, match.range.first + url.length, normalize(url))
    }.toList()

    /**
     * [text] with `[url=target]label[/url]` and `[url]target[/url]` replaced by their label (or
     * target), linked to the target, plus every bare URL in the rest of the text.
     */
    fun parse(text: String): LinkedText {
        val out = StringBuilder()
        val links = mutableListOf<TextLink>()
        var last = 0
        fun appendPlain(segment: String) {
            val offset = out.length
            findUrls(segment).mapTo(links) { it.copy(start = it.start + offset, end = it.end + offset) }
            out.append(segment)
        }
        for (match in bbCodePattern.findAll(text)) {
            val label = match.groupValues[2]
            val target = match.groupValues[1].trim().trim('"', '\'').ifEmpty { label.trim() }
            if (!isWebUrl(target)) continue
            appendPlain(text.substring(last, match.range.first))
            val start = out.length
            out.append(label.ifBlank { target })
            links += TextLink(start, out.length, normalize(target))
            last = match.range.last + 1
        }
        appendPlain(text.substring(last))
        return LinkedText(out.toString(), links)
    }

    private fun isWebUrl(target: String) =
        target.startsWith("http://", ignoreCase = true) || target.startsWith("https://", ignoreCase = true) ||
            target.startsWith("www.", ignoreCase = true)

    private fun normalize(url: String) = if (url.startsWith("www.", ignoreCase = true)) "https://$url" else url

    private tailrec fun trimTrailing(url: String): String {
        val last = url.lastOrNull() ?: return url
        val unbalanced = when (last) {
            ')' -> url.count { it == '(' } < url.count { it == ')' }
            '}' -> url.count { it == '{' } < url.count { it == '}' }
            else -> false
        }
        return if (last in TRAILING_PUNCTUATION || unbalanced) trimTrailing(url.dropLast(1)) else url
    }
}
