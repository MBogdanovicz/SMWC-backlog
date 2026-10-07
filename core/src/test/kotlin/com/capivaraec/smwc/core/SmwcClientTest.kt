package com.capivaraec.smwc.core

import com.capivaraec.smwc.core.api.RateLimiter
import com.capivaraec.smwc.core.api.SmwcClient
import com.capivaraec.smwc.core.api.SmwcRateLimitedException
import com.capivaraec.smwc.core.model.SortField
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SmwcClientTest {
    private val searchBody = """{"current_page":2,"last_page":2,"data":[{"id":1,"name":"A"}]}"""
    private val detailBody = """{"id":7,"name":"Seven"}"""

    @Test
    fun searchSendsSectionListParameters() = runTest {
        val urls = mutableListOf<Url>()
        val client = SmwcClient(HttpClient(MockEngine { urls += it.url; respond(searchBody) }), RateLimiter(0))

        val page = client.search(" kaizo ", page = 2, sort = SortField.RATING, descending = false)

        assertEquals(1, page.items.size)
        val params = urls.single().parameters
        assertEquals("getsectionlist", params["a"])
        assertEquals("smwhacks", params["s"])
        assertEquals("2", params["n"])
        assertEquals("rating", params["o"])
        assertEquals("asc", params["d"])
        assertEquals("kaizo", params["f[name]"])
    }

    @Test
    fun hackDetailsAreCached() = runTest {
        var calls = 0
        val client = SmwcClient(HttpClient(MockEngine { calls++; respond(detailBody) }), RateLimiter(0))

        client.getHack(7)
        val again = client.getHack(7)

        assertEquals("Seven", again.summary.name)
        assertEquals(1, calls)
    }

    @Test
    fun tooManyAttemptsBecomesRateLimitedError() = runTest {
        val client = SmwcClient(
            HttpClient(MockEngine { respond("""{"message":"Too Many Attempts."}""", HttpStatusCode.TooManyRequests) }),
            RateLimiter(0),
        )
        assertFailsWith<SmwcRateLimitedException> { client.search("x") }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun rateLimiterSpacesCalls() = runTest {
        val limiter = RateLimiter(1_500) { testScheduler.currentTime }
        limiter.acquire()
        limiter.acquire()
        limiter.acquire()
        assertEquals(3_000, testScheduler.currentTime)
    }
}
