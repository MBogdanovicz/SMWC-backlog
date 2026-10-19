package com.capivaraec.smwc.app.ui

import com.capivaraec.smwc.core.ra.RaTimes

/** "3h 20m", "45m"; the user's own time as is, a typical one with a leading "~". */
private fun formatDuration(seconds: Long, typical: Boolean): String {
    val minutes = (seconds / 60).coerceAtLeast(1)
    val text = if (minutes < 60) "${minutes}m" else "${minutes / 60}h" + (minutes % 60).takeIf { it > 0 }?.let { " ${it}m" }.orEmpty()
    return if (typical) "~$text" else text
}

/** "Beat ~2h · Master 10h 30m", or null when neither time is known. */
internal fun RaTimes.summary(): String? = listOfNotNull(
    beatSeconds?.let { "Beat ${formatDuration(it, beatTypical)}" },
    masterSeconds?.let { "Master ${formatDuration(it, masterTypical)}" },
).joinToString(" · ").ifEmpty { null }
