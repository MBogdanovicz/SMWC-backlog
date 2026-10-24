package com.capivaraec.smwc.core.api

import com.capivaraec.smwc.core.model.HackDetail
import com.capivaraec.smwc.core.model.HackSummary
import com.capivaraec.smwc.core.model.SearchPage
import com.capivaraec.smwc.core.text.Links
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Parses SMW Central's `ajax.php` responses.
 *
 * The API is undocumented, so parsing is deliberately lenient: every field is optional and
 * values are looked up in `raw_fields` first, then `fields` (which may hold HTML), then the
 * top level of the file object.
 */
object SmwcParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseSearchPage(body: String): SearchPage {
        val root = json.parseToJsonElement(body).jsonObject
        root.apiError()?.let { throw SmwcApiException(it) }
        val items = (root["data"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.toSummary() }
        val page = root.int("current_page") ?: 1
        return SearchPage(
            items = items,
            page = page,
            lastPage = root.int("last_page") ?: page,
            total = root.int("total"),
        )
    }

    fun parseHackDetail(body: String): HackDetail {
        val root = json.parseToJsonElement(body).jsonObject
        root.apiError()?.let { throw SmwcApiException(it) }
        // Some responses wrap the file in "data"; handle both shapes.
        val file = (root["data"] as? JsonObject) ?: root
        val summary = file.toSummary() ?: throw SmwcApiException("Response has no id/name")
        return HackDetail(
            summary = summary,
            descriptionHtml = file.descriptionHtml(),
            tags = file.fieldElement("tags").stringList(),
            downloadUrl = file.string("download_url")?.let(::absoluteUrl),
            sizeBytes = file.long("size"),
            screenshotUrls = (file["images"] ?: file["screenshots"]).stringList().map(::absoluteUrl),
            latestVersionId = file.latestVersionId(),
        )
    }

    private fun JsonObject.toSummary(): HackSummary? {
        val id = long("id") ?: return null
        val name = string("name") ?: return null
        return HackSummary(
            id = id,
            name = name,
            authors = (this["authors"] ?: fieldElement("authors")).stringList(),
            difficulty = field("difficulty") ?: field("type"),
            length = field("length"),
            rating = double("rating"),
            downloads = long("downloads"),
            submittedAt = long("submitted_at") ?: long("time"),
            acceptedAt = long("moderated_at") ?: long("time"),
            thumbnailUrl = (this["images"] ?: this["screenshots"]).stringList().firstOrNull()?.let(::absoluteUrl),
        )
    }

    /**
     * For an obsolete file: the current version's id, from the version list (the one not marked
     * obsolete) or else from `obsoleted_by` (which may itself be outdated). Null if current.
     */
    private fun JsonObject.latestVersionId(): Long? {
        val obsoletedBy = long("obsoleted_by") ?: return null
        val current = (this["versions"] as? JsonArray).orEmpty()
            .mapNotNull { it as? JsonObject }
            .firstOrNull { (it["obsolete"] as? JsonPrimitive)?.contentOrNull == "false" }
            ?.long("id")
        return current ?: obsoletedBy
    }

    /**
     * The description as HTML: `fields` holds it rendered (with relative links made absolute);
     * a description only in `raw_fields` is the author's BBCode, of which only line breaks and
     * links are kept.
     */
    private fun JsonObject.descriptionHtml(): String? {
        val html = ((this["fields"] as? JsonObject)?.string("description") ?: string("description"))?.takeIf { it.isNotBlank() }
        if (html != null) return relativeHref.replace(html) { "href=\"${absoluteUrl(it.groupValues[1])}\"" }
        val raw = (this["raw_fields"] as? JsonObject)?.string("description")?.takeIf { it.isNotBlank() } ?: return null
        val linked = Links.parse(raw)
        return buildString {
            var last = 0
            for (link in linked.links) {
                append(linked.text.substring(last, link.start).escapeHtml())
                append("<a href=\"").append(link.url.escapeHtml()).append("\">")
                append(linked.text.substring(link.start, link.end).escapeHtml()).append("</a>")
                last = link.end
            }
            append(linked.text.substring(last).escapeHtml())
        }.replace(Regex("\r?\n"), "<br>")
    }

    private val relativeHref = Regex("""href="(/[^"]*)"""")

    private fun String.escapeHtml() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun JsonObject.apiError(): String? =
        if (this["data"] == null && this["id"] == null) string("message") ?: string("error") else null

    /** A per-section field, preferring the structured `raw_fields` over HTML `fields`. */
    private fun JsonObject.field(key: String): String? =
        fieldElement(key)?.let { el ->
            when (el) {
                is JsonPrimitive -> el.contentOrNull?.takeIf { it.isNotBlank() }
                is JsonArray -> el.stringList().takeIf { it.isNotEmpty() }?.joinToString(", ")
                else -> null
            }
        }

    private fun JsonObject.fieldElement(key: String): JsonElement? =
        listOf(this["raw_fields"], this["fields"])
            .firstNotNullOfOrNull { (it as? JsonObject)?.get(key)?.takeUnless { v -> v is JsonNull } }
            ?: this[key]?.takeUnless { it is JsonNull }

    /** Strings, or objects with a `name`/`url`, flattened into a list of strings. */
    private fun JsonElement?.stringList(): List<String> = when (this) {
        is JsonArray -> mapNotNull { el ->
            when (el) {
                is JsonPrimitive -> el.contentOrNull
                is JsonObject -> (el["name"] ?: el["url"] ?: el["src"])?.let { (it as? JsonPrimitive)?.contentOrNull }
                else -> null
            }
        }
        is JsonPrimitive -> contentOrNull?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        else -> emptyList()
    }.filter { it.isNotBlank() }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.long(key: String): Long? = string(key)?.toDoubleOrNull()?.toLong()
    private fun JsonObject.int(key: String): Int? = long(key)?.toInt()
    private fun JsonObject.double(key: String): Double? = string(key)?.toDoubleOrNull()

    private fun absoluteUrl(url: String): String = when {
        url.startsWith("//") -> "https:$url"
        url.startsWith("/") -> "https://www.smwcentral.net$url"
        else -> url
    }
}

class SmwcApiException(message: String) : Exception(message)
