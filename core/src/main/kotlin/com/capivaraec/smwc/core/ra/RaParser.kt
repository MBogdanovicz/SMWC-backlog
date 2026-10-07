package com.capivaraec.smwc.core.ra

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

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

