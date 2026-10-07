package com.capivaraec.smwc.app.data

import android.content.Context
import androidx.core.content.edit
import com.capivaraec.smwc.core.backlog.BacklogFilter
import com.capivaraec.smwc.core.backlog.BacklogSort
import com.capivaraec.smwc.core.backlog.RaSetFilter
import com.capivaraec.smwc.core.ra.AchievementFilter
import com.capivaraec.smwc.core.ra.AchievementSort
import com.capivaraec.smwc.core.ra.ListFilter
import com.capivaraec.smwc.core.ra.MasteredFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Per-device display choices: sort and filters (not synced: they are about this screen, not the backlog). */
class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)

    private val _backlogSort = MutableStateFlow(
        prefs.getString(KEY_BACKLOG_SORT, null)
            ?.let { name -> BacklogSort.entries.firstOrNull { it.name == name } }
            ?: BacklogSort.DEFAULT,
    )
    val backlogSort: StateFlow<BacklogSort> = _backlogSort.asStateFlow()

    fun setBacklogSort(sort: BacklogSort) {
        prefs.edit { putString(KEY_BACKLOG_SORT, sort.name) }
        _backlogSort.value = sort
    }

    private val _backlogFilter = MutableStateFlow(prefs.getString(KEY_BACKLOG_FILTER, null)?.let(::decodeFilter) ?: BacklogFilter())
    val backlogFilter: StateFlow<BacklogFilter> = _backlogFilter.asStateFlow()

    fun setBacklogFilter(filter: BacklogFilter) {
        prefs.edit { putString(KEY_BACKLOG_FILTER, encodeFilter(filter)) }
        _backlogFilter.value = filter
    }

    private fun encodeFilter(filter: BacklogFilter): String = buildJsonObject {
        put("difficulties", JsonArray(filter.difficulties.map(::JsonPrimitive)))
        filter.minRating?.let { put("minRating", it) }
        put("raSet", filter.raSet.name)
        put("releaseYears", JsonArray(filter.releaseYears.map(::JsonPrimitive)))
    }.toString()

    /** Anything unreadable (e.g. from an older version) falls back to no filter. */
    private fun decodeFilter(json: String): BacklogFilter? = runCatching {
        val o = Json.parseToJsonElement(json).jsonObject
        BacklogFilter(
            difficulties = o["difficulties"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet().orEmpty(),
            minRating = o["minRating"]?.jsonPrimitive?.doubleOrNull,
            raSet = o["raSet"]?.jsonPrimitive?.content?.let { name -> RaSetFilter.entries.firstOrNull { it.name == name } } ?: RaSetFilter.ANY,
            releaseYears = o["releaseYears"]?.jsonArray?.mapNotNull { it.jsonPrimitive.intOrNull }?.toSet().orEmpty(),
        )
    }.getOrNull()

    private val _achievementSort = MutableStateFlow(enumPref(KEY_ACHIEVEMENT_SORT, AchievementSort.DEFAULT))
    val achievementSort: StateFlow<AchievementSort> = _achievementSort.asStateFlow()

    fun setAchievementSort(sort: AchievementSort) {
        prefs.edit { putString(KEY_ACHIEVEMENT_SORT, sort.name) }
        _achievementSort.value = sort
    }

    private val _achievementFilter = MutableStateFlow(
        AchievementFilter(
            list = enumPref(KEY_ACHIEVEMENT_LIST, ListFilter.ANY),
            mastered = enumPref(KEY_ACHIEVEMENT_MASTERED, MasteredFilter.ANY),
        ),
    )
    val achievementFilter: StateFlow<AchievementFilter> = _achievementFilter.asStateFlow()

    fun setAchievementFilter(filter: AchievementFilter) {
        prefs.edit {
            putString(KEY_ACHIEVEMENT_LIST, filter.list.name)
            putString(KEY_ACHIEVEMENT_MASTERED, filter.mastered.name)
        }
        _achievementFilter.value = filter
    }

    private val _dismissedCompletionSuggestions = MutableStateFlow(
        prefs.getStringSet(KEY_DISMISSED_COMPLETION, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet(),
    )

    /** Hacks for which the user dismissed the "you finished it, mark as completed?" suggestion. */
    val dismissedCompletionSuggestions: StateFlow<Set<Long>> = _dismissedCompletionSuggestions.asStateFlow()

    fun dismissCompletionSuggestion(hackId: Long) {
        val updated = _dismissedCompletionSuggestions.value + hackId
        prefs.edit { putStringSet(KEY_DISMISSED_COMPLETION, updated.map { it.toString() }.toSet()) }
        _dismissedCompletionSuggestions.value = updated
    }

    private val _useSystemColors = MutableStateFlow(prefs.getBoolean(KEY_SYSTEM_COLORS, false))

    /** Material You (colors from the wallpaper) instead of the app's own palette. */
    val useSystemColors: StateFlow<Boolean> = _useSystemColors.asStateFlow()

    fun setUseSystemColors(value: Boolean) {
        prefs.edit { putBoolean(KEY_SYSTEM_COLORS, value) }
        _useSystemColors.value = value
    }

    /** A saved enum value, or [default] if missing or unknown (e.g. renamed in a newer version). */
    private inline fun <reified E : Enum<E>> enumPref(key: String, default: E): E =
        prefs.getString(key, null)?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

    private companion object {
        const val KEY_BACKLOG_SORT = "backlog_sort"
        const val KEY_BACKLOG_FILTER = "backlog_filter"
        const val KEY_ACHIEVEMENT_SORT = "achievement_sort"
        const val KEY_ACHIEVEMENT_LIST = "achievement_filter_list"
        const val KEY_ACHIEVEMENT_MASTERED = "achievement_filter_mastered"
        const val KEY_DISMISSED_COMPLETION = "dismissed_completion_suggestions"
        const val KEY_SYSTEM_COLORS = "use_system_colors"
    }
}
