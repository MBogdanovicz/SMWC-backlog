package com.capivaraec.smwc.app.ui

import com.capivaraec.smwc.core.ra.RaTimes
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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

/** "just now", "5m ago", "3h ago", "2d ago", then the date ("2024-01-31", in the device's time zone). */
internal fun formatCommentDate(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = (now - epochMillis) / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 24 * 60 -> "${minutes / 60}h ago"
        minutes < 7 * 24 * 60 -> "${minutes / (24 * 60)}d ago"
        else -> DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(epochMillis))
    }
}
