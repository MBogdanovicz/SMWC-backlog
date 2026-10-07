package com.capivaraec.smwc.core.ra

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** [apiKey] is the user's "Web API Key" from retroachievements.org/settings. */
data class RaCredentials(val username: String, val apiKey: String)

class RaNotConfiguredException : Exception("Add your RetroAchievements user name and API key first")

/**
 * Client for the RetroAchievements Web API (https://api-docs.retroachievements.org).
 * [credentials] is read on every call, so changing them takes effect right away.
 */
class RetroAchievementsClient(
    private val http: HttpClient,
    private val credentials: suspend () -> RaCredentials?,
    private val baseUrl: String = "https://retroachievements.org/API",
) {
    /** Every SNES game with achievements and its supported ROM hashes, as raw JSON (it is cached as is). */
    suspend fun snesGameListJson(): String = get("API_GetGameList.php", requireCredentials()) {
        parameter("i", SNES_CONSOLE_ID)
        parameter("f", 1)
        parameter("h", 1)
    }

    suspend fun progress(gameId: Long): RaProgress = RaParser.parseProgress(progressJson(gameId))

    /** The user's progress in a set as raw JSON (it is cached as is). */
    suspend fun progressJson(gameId: Long): String {
        val credentials = requireCredentials()
        return get("API_GetGameInfoAndUserProgress.php", credentials) {
            parameter("g", gameId)
            parameter("u", credentials.username)
            parameter("a", 1) // award info (beaten/completed/mastered)
        }
    }

    /**
     * Checks that the key works and the user exists, before they are saved. Returns the user
     * name as RetroAchievements spells it. (The API cannot tell whose key it is, so any valid
     * key is accepted.)
     */
    suspend fun verify(credentials: RaCredentials): String {
        val body = get("API_GetUserProfile.php", credentials) { parameter("u", credentials.username) }
        return RaParser.parseUserName(body) ?: throw RaApiException("User \"${credentials.username}\" not found")
    }

    private suspend fun requireCredentials() = credentials() ?: throw RaNotConfiguredException()

    private suspend fun get(endpoint: String, credentials: RaCredentials, params: HttpRequestBuilder.() -> Unit): String {
        val response = http.get("$baseUrl/$endpoint") {
            parameter("y", credentials.apiKey)
            params()
        }
        when (response.status.value) {
            in 200..299 -> return response.bodyAsText()
            401, 403 -> throw RaApiException("RetroAchievements rejected the API key")
            404 -> throw RaApiException("User \"${credentials.username}\" not found")
            else -> throw RaApiException("RetroAchievements returned HTTP ${response.status.value}")
        }
    }

    companion object {
        const val SNES_CONSOLE_ID = 3
    }
}

/** Where the (large, rarely changing) game list is kept between app launches. */
interface RaListCache {
    suspend fun read(): CachedText?
    suspend fun write(text: String)
}

/** Where the user's progress in each set is kept between app launches. */
interface RaProgressCache {
    suspend fun read(gameId: Long): CachedText?
    suspend fun write(gameId: Long, text: String)
    suspend fun clear()
}

/** [savedAt] is in epoch millis. */
data class CachedText(val savedAt: Long, val text: String)

/**
 * Hack sets and the user's progress in them. The game list is big, so RetroAchievements asks
 * clients to cache it aggressively: it is downloaded at most once per [maxAgeMillis]. Progress
 * is downloaded at most once per [progressMaxAgeMillis] per set, unless refreshed. When a
 * download fails, an outdated copy is still used.
 */
class RetroAchievementsRepository(
    private val client: RetroAchievementsClient,
    private val cache: RaListCache,
    private val progressDiskCache: RaProgressCache,
    private val now: () -> Long = System::currentTimeMillis,
    private val maxAgeMillis: Long = 7 * DAY_MILLIS,
    private val progressMaxAgeMillis: Long = DAY_MILLIS,
) {
    private val indexLock = Mutex()
    private val _index = MutableStateFlow<RaHackIndex?>(null)

    /** Null until [index] has loaded once. */
    val loadedIndex: StateFlow<RaHackIndex?> = _index.asStateFlow()

    private val progressLock = Mutex()
    private val progressCache = HashMap<Long, Pair<Long, RaProgress>>() // gameId -> (savedAt, progress)

    suspend fun index(): RaHackIndex = indexLock.withLock {
        _index.value?.let { return it }
        val cached = cache.read()
        val text = if (cached != null && now() - cached.savedAt < maxAgeMillis) {
            cached.text
        } else {
            try {
                client.snesGameListJson().also { cache.write(it) }
            } catch (e: Exception) {
                cached?.text ?: throw e
            }
        }
        RaHackIndex(RaParser.parseHackList(text)).also { _index.value = it }
    }

    suspend fun find(hackName: String, romMd5s: Collection<String> = emptyList()): RaMatch? = index().find(hackName, romMd5s)

    /** Forgets the user's progress, e.g. after switching to another account. */
    suspend fun clearProgress() = progressLock.withLock {
        progressCache.clear()
        progressDiskCache.clear()
    }

    /**
     * The user's progress in a set: from memory or disk while younger than
     * [progressMaxAgeMillis], else from RetroAchievements. [refresh] always asks the server
     * (and fails instead of falling back to an old copy, so a failed refresh is visible).
     */
    suspend fun progress(gameId: Long, refresh: Boolean = false): RaProgress = progressLock.withLock {
        val fresh = { savedAt: Long -> now() - savedAt < progressMaxAgeMillis }
        if (!refresh) {
            progressCache[gameId]?.takeIf { fresh(it.first) }?.let { return it.second }
        }
        val disk = progressDiskCache.read(gameId)
        if (!refresh && disk != null && fresh(disk.savedAt)) {
            runCatching { RaParser.parseProgress(disk.text) }.getOrNull()?.let {
                progressCache[gameId] = disk.savedAt to it
                return it
            }
        }
        val progress = try {
            val json = client.progressJson(gameId)
            RaParser.parseProgress(json).also { progressDiskCache.write(gameId, json) }
        } catch (e: Exception) {
            if (refresh) throw e
            disk?.let { runCatching { RaParser.parseProgress(it.text) }.getOrNull() } ?: throw e
        }
        progressCache[gameId] = now() to progress
        progress
    }

    private companion object {
        const val DAY_MILLIS = 24 * 60 * 60 * 1000L
    }
}
