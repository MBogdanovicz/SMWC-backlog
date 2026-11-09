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

/**
 * How long a set takes, in seconds. The user's own time when they beat / mastered it, else what
 * players typically take (RetroAchievements' median, the `*Typical` flag is then true).
 */
data class RaTimes(
    val beatSeconds: Long?,
    val beatTypical: Boolean,
    val masterSeconds: Long?,
    val masterTypical: Boolean,
)

/**
 * How many players reached each milestone of a set and their median time, in hardcore and in
 * casual, like the site's "Playtime Stats". Medians are kept even from small samples; callers
 * decide whether [Milestone.enoughData] matters.
 */
data class RaPlaytimeStats(
    /** Players who unlocked an achievement in hardcore; the rest of them count as casual. */
    val hardcorePlayers: Int,
    val casualPlayers: Int,
    val hardcore: Mode,
    val casual: Mode,
) {
    /** [finish] is a mastery in hardcore, a completion in casual. */
    data class Mode(val beat: Milestone, val finish: Milestone)

    /** [samples] is how many players' times went into [medianSeconds]. */
    data class Milestone(val samples: Int, val medianSeconds: Long?) {
        /** Whether RetroAchievements itself would show the median. */
        val enoughData: Boolean get() = samples >= RaParser.MIN_SAMPLES_FOR_MEDIAN
    }
}

/** A comment left on an achievement's page on RetroAchievements. */
data class RaComment(
    val user: String,
    /** Epoch millis; null if RetroAchievements did not say or the date could not be read. */
    val submittedAt: Long?,
    val text: String,
)

/** One page of comments and how many there are in all. */
data class RaCommentPage(val total: Int, val comments: List<RaComment>)

private val FINISHED_AWARDS = setOf("beaten-softcore", "beaten-hardcore", "completed", "mastered")

class RaApiException(message: String) : Exception(message)
