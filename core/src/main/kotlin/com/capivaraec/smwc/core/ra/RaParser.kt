package com.capivaraec.smwc.core.ra

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Parses RetroAchievements Web API responses. Field names are PascalCase in the v1 API, but
 * lookups ignore case so a camelCase response parses too.
 */
object RaParser {
    private val json = Json { ignoreUnknownKeys = true }

    /** `API_GetGameList.php`: keeps only hacks (titles tagged `~Hack~`), skipping subsets. */
    fun parseHackList(body: String): List<RaGame> {
        val root = json.parseToJsonElement(body)
        val items = root as? JsonArray ?: throw RaApiException(root.errorMessage() ?: "Unexpected game list response")
        return items.mapNotNull { (it as? JsonObject)?.toGame() }
            .filter { it.title.contains(HACK_TAG, ignoreCase = true) && !it.title.contains("[Subset", ignoreCase = true) }
    }

    /** `API_GetGameInfoAndUserProgress.php`. */
    fun parseProgress(body: String): RaProgress {
        val root = json.parseToJsonElement(body) as? JsonObject ?: throw RaApiException("Unexpected progress response")
        val game = root.toGame() ?: throw RaApiException(root.errorMessage() ?: "Response has no game")
        val achievements = when (val list = root.field("Achievements")) {
            is JsonObject -> list.values
            is JsonArray -> list
            else -> emptyList()
        }.mapNotNull { (it as? JsonObject)?.toAchievement() }
            .sortedWith(compareBy({ it.displayOrder }, { it.achievement.id }))
            .map { it.achievement }
        return RaProgress(game = game, achievements = achievements, highestAward = root.string("HighestAwardKind"))
    }

    /** `API_GetUserProfile.php`: the user name as RetroAchievements spells it, or null if not found. */
    fun parseUserName(body: String): String? =
        (runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject)?.string("User")

    /**
     * [progression] is `API_GetGameProgression.php` (medians, in seconds); [player] is the v2
     * `users/{user}/player-games` document with its achievement sets, or null if not available.
     * The user's own beat time (hardcore first) and mastery time win over the medians; a softcore
     * completion stands in for a mastery.
     */
    fun parseTimes(progression: String, player: String?, gameId: Long): RaTimes {
        val medians = json.parseToJsonElement(progression) as? JsonObject ?: throw RaApiException("Unexpected progression response")
        medians.errorMessage()?.let { throw RaApiException(it) }
        val document = player?.let { runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
        val attributes = ((document?.field("data") as? JsonArray)?.firstOrNull() as? JsonObject)?.field("attributes") as? JsonObject
        val sets = (document?.field("included") as? JsonArray).orEmpty()
            .mapNotNull { it as? JsonObject }
            .filter { it.string("type") == "player-achievement-sets" }
            .mapNotNull { it.field("attributes") as? JsonObject }
        // The game's own set, not a subset or bonus set attached to it.
        val core = sets.firstOrNull { set ->
            (set.field("setContext") as? JsonArray).orEmpty().any {
                (it as? JsonObject)?.let { c -> c.string("type") == "core" && c.long("gameId") == gameId } == true
            }
        } ?: sets.singleOrNull()
        val ownBeat = attributes?.long("timeToBeatHardcoreSeconds") ?: attributes?.long("timeToBeatSeconds")
        val ownMaster = core?.takeIf { it.string("completedHardcoreAt") != null }?.long("timeTakenHardcoreSeconds")
            ?: core?.takeIf { it.string("completedAt") != null }?.long("timeTakenSeconds")
        val beat = ownBeat ?: medians.long("MedianTimeToBeatHardcore") ?: medians.long("MedianTimeToBeat")
        val master = ownMaster ?: medians.long("MedianTimeToMaster") ?: medians.long("MedianTimeToComplete")
        return RaTimes(
            beatSeconds = beat?.takeIf { it > 0 },
            beatTypical = ownBeat == null,
            masterSeconds = master?.takeIf { it > 0 },
            masterTypical = ownMaster == null,
        )
    }

    /**
     * `API_GetComments.php`: one page of comments, in the order served. [RaCommentPage.total] is
     * at least the comments on the page, for responses without a `Total`.
     */
    fun parseComments(body: String): RaCommentPage {
        val root = json.parseToJsonElement(body) as? JsonObject ?: throw RaApiException("Unexpected comments response")
        val results = root.field("Results") as? JsonArray
        if (results == null) root.errorMessage()?.let { throw RaApiException(it) }
        val comments = results.orEmpty().mapNotNull { item ->
            val comment = item as? JsonObject ?: return@mapNotNull null
            val text = comment.string("CommentText") ?: return@mapNotNull null
            RaComment(user = comment.string("User") ?: "Unknown user", submittedAt = comment.string("Submitted")?.let(::parseDate), text = text)
        }
        return RaCommentPage(total = maxOf(root.int("Total") ?: 0, comments.size), comments = comments)
    }

    /** "2024-01-01T12:00:00.000000Z", or "2024-01-01 12:00:00" (taken as UTC). */
    private fun parseDate(text: String): Long? =
        runCatching { Instant.parse(text).toEpochMilli() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(text.replace(' ', 'T')).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()

    private fun JsonObject.toGame(): RaGame? {
        val id = long("ID") ?: return null
        val title = string("Title") ?: return null
        return RaGame(
            id = id,
            title = title,
            achievements = int("NumAchievements") ?: 0,
            points = int("Points") ?: 0,
            iconUrl = string("ImageIcon")?.let { "$MEDIA_URL$it" },
            hashes = (field("Hashes") as? JsonArray).orEmpty()
                .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.lowercase() },
        )
    }

    private class Ordered(val achievement: RaAchievement, val displayOrder: Int)

    private fun JsonObject.toAchievement(): Ordered? {
        val id = long("ID") ?: return null
        val achievement = RaAchievement(
            id = id,
            title = string("Title") ?: return null,
            description = string("Description"),
            points = int("Points") ?: 0,
            badgeUrl = string("BadgeName")?.let { "$MEDIA_URL/Badge/$it.png" },
            earnedAt = string("DateEarned"),
            earnedHardcoreAt = string("DateEarnedHardcore"),
        )
        return Ordered(achievement, int("DisplayOrder") ?: 0)
    }

    private fun JsonElement.errorMessage(): String? = (this as? JsonObject)?.let { it.string("message") ?: it.string("error") }

    /** The value under [key], matching its case first, then any case (camelCase responses). */
    private fun JsonObject.field(key: String): JsonElement? = this[key] ?: entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value
    private fun JsonObject.string(key: String): String? = (field(key) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    private fun JsonObject.long(key: String): Long? = string(key)?.toDoubleOrNull()?.toLong()
    private fun JsonObject.int(key: String): Int? = long(key)?.toInt()

    const val HACK_TAG = "~Hack~"
    private const val MEDIA_URL = "https://media.retroachievements.org"
}

