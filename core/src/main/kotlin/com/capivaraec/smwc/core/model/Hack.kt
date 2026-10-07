package com.capivaraec.smwc.core.model

/** One row of the SMW Central hack list (search results). */
data class HackSummary(
    val id: Long,
    val name: String,
    val authors: List<String>,
    /** e.g. "Standard: Easy", "Kaizo: Hard". */
    val difficulty: String?,
    /** e.g. "12 exit(s)". */
    val length: String?,
    val rating: Double?,
    val downloads: Long?,
    /** Unix epoch seconds. */
    val submittedAt: Long?,
    /** When a moderator accepted it (the site's "Accepted by" date), Unix epoch seconds. */
    val acceptedAt: Long? = null,
    /** The first screenshot, for lists. */
    val thumbnailUrl: String? = null,
) {
    /** Number of exits, read from [length] ("14" or "14 exit(s)"). */
    val exits: Int? get() = exitCount(length)
}

/** Everything the details screen shows for one hack. */
data class HackDetail(
    val summary: HackSummary,
    /** HTML as served by SMW Central. */
    val descriptionHtml: String?,
    val tags: List<String>,
    val downloadUrl: String?,
    val sizeBytes: Long?,
    val screenshotUrls: List<String>,
    /**
     * When this version is obsolete: the id of the current version (each new version of a hack
     * is a new file on SMW Central). Null while this is the latest.
     */
    val latestVersionId: Long? = null,
) {
    val pageUrl: String get() = hackPageUrl(summary.id)
}

fun hackPageUrl(id: Long): String = "https://www.smwcentral.net/?p=section&a=details&id=$id"

data class SearchPage(
    val items: List<HackSummary>,
    val page: Int,
    val lastPage: Int,
    val total: Int?,
) {
    val hasNext: Boolean get() = page < lastPage
}

enum class SortField(val apiValue: String) {
    DATE("date"),
    NAME("name"),
    RATING("rating"),
    DOWNLOADS("downloads"),
}

/**
 * SMW Central's difficulty ids and their names, as listed by `ajax.php?a=getsectioninfo&s=smwhacks`
 * (field "difficulty"). The backlog keeps the raw id; only the display uses the name.
 */
object Difficulty {
    private val names = mapOf(
        "diff_1" to "Newcomer",
        "diff_2" to "Casual",
        "diff_3" to "Intermediate",
        "diff_4" to "Advanced",
        "diff_5" to "Expert",
        "diff_6" to "Master",
        "diff_7" to "Grandmaster",
    )

    /** The name for [raw] (e.g. "diff_5" → "Expert"); anything unknown is shown as it came. */
    fun displayName(raw: String?): String? = raw?.let { names[it.trim().lowercase()] ?: it }
}

/**
 * The hack length for display. SMW Central sends the number of exits, usually as a bare number
 * ("20" → "20 exits", "1" → "1 exit"); any other text is shown as it came.
 */
/** The leading number of a length such as "14" or "14 exit(s)"; null if there is none. */
fun exitCount(length: String?): Int? = length?.trim()?.takeWhile { it.isDigit() }?.toIntOrNull()

fun lengthDisplay(length: String?): String? {
    val raw = length?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val exits = raw.toIntOrNull() ?: return raw
    return if (exits == 1) "1 exit" else "$exits exits"
}
