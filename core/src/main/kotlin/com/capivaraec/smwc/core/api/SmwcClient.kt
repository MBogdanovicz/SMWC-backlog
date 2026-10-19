package com.capivaraec.smwc.core.api

import com.capivaraec.smwc.core.model.HackDetail
import com.capivaraec.smwc.core.model.SearchPage
import com.capivaraec.smwc.core.model.SortField
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Client for SMW Central's undocumented JSON API.
 *
 * SMW Central rate-limits aggressively ("Too Many Attempts" after a few quick calls), so every
 * request goes through [RateLimiter], and details are cached in memory for the app's lifetime.
 */
class SmwcClient(
    private val http: HttpClient,
    private val rateLimiter: RateLimiter = RateLimiter(minIntervalMillis = 1_500),
    private val baseUrl: String = "https://www.smwcentral.net/ajax.php",
) {
    private val detailCache = LinkedHashMap<Long, HackDetail>()
    private val cacheLock = Mutex()

    suspend fun search(
        query: String,
        page: Int = 1,
        sort: SortField = SortField.DATE,
        descending: Boolean = true,
        /** Matches [query] against the authors' names instead of the hack names. */
        byAuthor: Boolean = false,
    ): SearchPage {
        val body = get {
            parameter("a", "getsectionlist")
            parameter("s", SECTION)
            parameter("n", page)
            parameter("o", sort.apiValue)
            parameter("d", if (descending) "desc" else "asc")
            if (query.isNotBlank()) parameter(if (byAuthor) "f[author]" else "f[name]", query.trim())
        }
        return SmwcParser.parseSearchPage(body)
    }

    suspend fun getHack(id: Long): HackDetail {
        cacheLock.withLock { detailCache[id] }?.let { return it }
        val body = get {
            parameter("a", "getfile")
            parameter("v", 2)
            parameter("id", id)
        }
        val detail = SmwcParser.parseHackDetail(body)
        cacheLock.withLock {
            detailCache[id] = detail
            if (detailCache.size > DETAIL_CACHE_SIZE) detailCache.remove(detailCache.keys.first())
        }
        return detail
    }

    private suspend fun get(params: io.ktor.client.request.HttpRequestBuilder.() -> Unit): String {
        rateLimiter.acquire()
        val response = http.get(baseUrl) {
            header("Accept", "application/json")
            params()
        }
        if (response.status == HttpStatusCode.TooManyRequests) throw SmwcRateLimitedException()
        val body = response.bodyAsText()
        if (body.contains("Too Many Attempts")) throw SmwcRateLimitedException()
        if (response.status.value !in 200..299) {
            throw SmwcApiException("SMW Central returned HTTP ${response.status.value}")
        }
        return body
    }

    companion object {
        const val SECTION = "smwhacks"
        private const val DETAIL_CACHE_SIZE = 100
    }
}

class SmwcRateLimitedException : Exception("SMW Central rate limit hit; wait before the next request")

/** Spaces calls at least [minIntervalMillis] apart. [now] is injectable for tests. */
class RateLimiter(
    private val minIntervalMillis: Long,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private var lastCall: Long? = null

    suspend fun acquire() = mutex.withLock {
        lastCall?.let { last ->
            val wait = last + minIntervalMillis - now()
            if (wait > 0) delay(wait)
        }
        lastCall = now()
    }
}
