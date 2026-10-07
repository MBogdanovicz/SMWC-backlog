package com.capivaraec.smwc.app.ui

import com.capivaraec.smwc.core.api.HackSearch
import com.capivaraec.smwc.core.api.RateLimiter
import com.capivaraec.smwc.core.api.SmwcClient
import com.capivaraec.smwc.core.backlog.BacklogDocument
import com.capivaraec.smwc.core.backlog.BacklogRepository
import com.capivaraec.smwc.core.backlog.BacklogStatus
import com.capivaraec.smwc.core.backlog.LocalBacklogStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private class MemoryStore : LocalBacklogStore {
        var doc = BacklogDocument()
        override suspend fun read() = doc
        override suspend fun write(document: BacklogDocument) { doc = document }
    }

    /** A fake SMW Central: names match by substring, 2 per page; "" lists everything. */
    private fun searchOver(names: List<String>, status: HttpStatusCode = HttpStatusCode.OK, requests: MutableList<String> = mutableListOf()) =
        HackSearch(
            SmwcClient(
                HttpClient(MockEngine { request ->
                    val term = request.url.parameters["f[name]"].orEmpty()
                    val page = request.url.parameters["n"]!!.toInt()
                    requests += "$term#$page"
                    val hits = names.filter { it.contains(term, ignoreCase = true) }
                    val body = buildJsonObject {
                        put("current_page", page)
                        put("last_page", maxOf(1, (hits.size + 1) / 2))
                        put("data", buildJsonArray {
                            hits.drop((page - 1) * 2).take(2).forEach { name ->
                                add(buildJsonObject { put("id", names.indexOf(name) + 1); put("name", name) })
                            }
                        })
                    }
                    respond(body.toString(), status)
                }),
                RateLimiter(0),
            ),
        )

    private val names = listOf("Kaizo World", "Kaizo Island", "Mario Adventure")

    /** The fake server answers on another thread; waits until the search has finished. */
    private suspend fun SearchViewModel.settled(): SearchUiState = state.first { !it.loading }

    @Test
    fun startsWithTheLatestHacks() = runTest {
        val vm = SearchViewModel(searchOver(names), BacklogRepository(MemoryStore()))
        val state = vm.settled()
        assertEquals(listOf("Kaizo World", "Kaizo Island"), state.results.map { it.name })
        assertTrue(state.hasNext)
        assertEquals("", state.searchedQuery)
    }

    @Test
    fun searchAndLoadMore() = runTest {
        val vm = SearchViewModel(searchOver(names + "Kaizo Peak"), BacklogRepository(MemoryStore()))
        vm.settled()
        vm.onQueryChange(" kaizo ")
        vm.search()
        assertEquals(listOf("Kaizo World", "Kaizo Island"), vm.settled().results.map { it.name })
        assertEquals("kaizo", vm.state.value.searchedQuery)

        // Editing the box does not change what "load more" continues.
        vm.onQueryChange("mario")
        vm.loadMore()
        vm.settled()
        assertEquals(listOf("Kaizo World", "Kaizo Island", "Kaizo Peak"), vm.state.value.results.map { it.name })
        assertTrue(!vm.state.value.hasNext)
    }

    @Test
    fun clearGoesBackToTheLatestHacks() = runTest {
        val vm = SearchViewModel(searchOver(names), BacklogRepository(MemoryStore()))
        vm.settled()
        vm.onQueryChange("mario")
        vm.search()
        assertEquals(listOf("Mario Adventure"), vm.settled().results.map { it.name })

        vm.clear()
        assertEquals("", vm.state.value.query)
        assertEquals(2, vm.settled().results.size)
    }

    @Test
    fun addingByNameReportsDuplicates() = runTest {
        val store = MemoryStore()
        val vm = SearchViewModel(searchOver(names), BacklogRepository(store))
        vm.settled()
        vm.onQueryChange("Secret Hack")

        vm.addByName(BacklogStatus.PLAYING)
        vm.state.first { it.addedMessage != null }
        assertEquals("Added \"Secret Hack\" to your playing list", vm.state.value.addedMessage)
        assertEquals(BacklogStatus.PLAYING, store.doc.visible.single().status)
        assertTrue(store.doc.visible.single().custom)

        vm.addByName(BacklogStatus.BACKLOG)
        vm.state.first { it.addedMessage?.contains("already") == true }
        assertEquals("\"Secret Hack\" is already in your list", vm.state.value.addedMessage)

        // Typing clears the message.
        vm.onQueryChange("Secret Hack 2")
        assertNull(vm.state.value.addedMessage)
    }

    @Test
    fun rateLimitShowsAFriendlyError() = runTest {
        val vm = SearchViewModel(searchOver(names, status = HttpStatusCode.TooManyRequests), BacklogRepository(MemoryStore()))
        val error = assertNotNull(vm.settled().error)
        assertTrue("wait" in error, error)
    }
}
