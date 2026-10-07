package com.capivaraec.smwc.core

import com.capivaraec.smwc.core.backlog.BacklogDocument
import com.capivaraec.smwc.core.backlog.BacklogRepository
import com.capivaraec.smwc.core.backlog.LocalBacklogStore
import com.capivaraec.smwc.core.drive.DriveAppDataClient
import com.capivaraec.smwc.core.model.HackSummary
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A fake Drive that keeps one appDataFolder file in memory. */
private class FakeDrive {
    var content: String? = null
    val requests = mutableListOf<String>()

    val client = HttpClient(MockEngine { req ->
        assertEquals("Bearer token-123", req.headers["Authorization"])
        val path = req.url.encodedPath
        requests += "${req.method.value} $path"
        when {
            req.method == HttpMethod.Get && path == "/drive/v3/files" -> {
                assertEquals("appDataFolder", req.url.parameters["spaces"])
                respond(if (content == null) """{"files":[]}""" else """{"files":[{"id":"f1"}]}""")
            }
            req.method == HttpMethod.Get && path == "/drive/v3/files/f1" -> respond(content!!)
            req.method == HttpMethod.Post && path == "/upload/drive/v3/files" -> {
                val body = String(req.body.toByteArray())
                assertTrue(body.contains("\"parents\":[\"appDataFolder\"]"))
                content = body.substringAfter("{\n").let { "{\n$it" }.substringBeforeLast("\r\n--")
                respond("""{"id":"f1"}""")
            }
            req.method == HttpMethod.Patch && path == "/upload/drive/v3/files/f1" -> {
                content = String(req.body.toByteArray())
                respond("""{"id":"f1"}""")
            }
            else -> error("Unexpected request ${req.method.value} ${req.url}")
        }
    })
}

private class MemoryStore(var doc: BacklogDocument = BacklogDocument()) : LocalBacklogStore {
    override suspend fun read() = doc
    override suspend fun write(document: BacklogDocument) { doc = document }
}

class DriveSyncTest {
    private fun hack(id: Long) = HackSummary(id, "Hack $id", emptyList(), null, null, null, null, null)

    @Test
    fun firstSyncCreatesFileThenLaterSyncsPatchIt() = runTest {
        val fake = FakeDrive()
        val drive = DriveAppDataClient(fake.client, { "token-123" })
        var clock = 1L
        val repo = BacklogRepository(MemoryStore()) { clock++ }

        repo.add(hack(1))
        repo.sync(drive)
        assertTrue("POST /upload/drive/v3/files" in fake.requests)
        assertEquals(listOf(1L), BacklogDocument.fromJson(fake.content!!).visible.map { it.hackId })

        repo.markCompleted(1)
        repo.sync(drive)
        assertTrue("PATCH /upload/drive/v3/files/f1" in fake.requests)
        assertEquals(repo.state.value, BacklogDocument.fromJson(fake.content!!))
    }

    @Test
    fun syncPullsChangesFromAnotherDevice() = runTest {
        val fake = FakeDrive()
        val drive = DriveAppDataClient(fake.client, { "token-123" })
        fake.content = BacklogDocument().add(hack(9), now = 5).toJson()

        val repo = BacklogRepository(MemoryStore()) { 100 }
        repo.sync(drive)

        assertEquals(listOf(9L), repo.state.value.visible.map { it.hackId })
        // Nothing new locally, so nothing is uploaded.
        assertTrue(fake.requests.none { it.startsWith("POST") || it.startsWith("PATCH") })
    }
}
