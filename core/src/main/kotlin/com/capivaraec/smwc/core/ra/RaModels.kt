package com.capivaraec.smwc.core.ra

/** A game (here: a hack) with an achievement set on RetroAchievements. */
data class RaGame(
    val id: Long,
    /** As shown on RetroAchievements, e.g. "~Hack~ Super Mario World: Learn 2 Kaizo". */
    val title: String,
    val achievements: Int,
    val points: Int,
    val iconUrl: String?,
    /** MD5s (lowercase) of the ROMs the set supports; only filled by the game list. */
    val hashes: List<String> = emptyList(),
) {
    val pageUrl: String get() = "https://retroachievements.org/game/$id"
}

/** One achievement of a set, with the user's unlock dates when [RaProgress] was fetched for a user. */
data class RaAchievement(
    val id: Long,
    val title: String,
    val description: String?,
    val points: Int,
    val badgeUrl: String?,
    /** "yyyy-MM-dd HH:mm:ss" as served by RetroAchievements; null if not unlocked. */
    val earnedAt: String?,
    val earnedHardcoreAt: String?,
) {
    val earned: Boolean get() = earnedAt != null || earnedHardcoreAt != null
}

/** A set and how far the user got in it. */
data class RaProgress(
    val game: RaGame,
    val achievements: List<RaAchievement>,
    /** The user's highest award for the set: "beaten-softcore", "beaten-hardcore", "completed" or "mastered". */
    val highestAward: String? = null,
) {
    /**
     * The user finished the game: RetroAchievements gave a beaten, completed or mastered award,
     * or (for sets without awards info) every achievement is unlocked.
     */
    val finished: Boolean
        get() = highestAward in FINISHED_AWARDS || (achievements.isNotEmpty() && earned == achievements.size)

    val earned: Int get() = achievements.count { it.earned }
    val earnedHardcore: Int get() = achievements.count { it.earnedHardcoreAt != null }
    val earnedPoints: Int get() = achievements.filter { it.earned }.sumOf { it.points }
}

private val FINISHED_AWARDS = setOf("beaten-softcore", "beaten-hardcore", "completed", "mastered")

class RaApiException(message: String) : Exception(message)
