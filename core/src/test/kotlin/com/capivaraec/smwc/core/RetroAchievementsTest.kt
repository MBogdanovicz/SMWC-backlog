package com.capivaraec.smwc.core

import com.capivaraec.smwc.core.ra.CachedText
import com.capivaraec.smwc.core.ra.RaApiException
import com.capivaraec.smwc.core.ra.RaCredentials
import com.capivaraec.smwc.core.ra.RaNotConfiguredException
import com.capivaraec.smwc.core.ra.RaGame
import com.capivaraec.smwc.core.ra.RaHackIndex
import com.capivaraec.smwc.core.ra.RaListCache
import com.capivaraec.smwc.core.ra.RaMatchKind
import com.capivaraec.smwc.core.ra.RaParser
import com.capivaraec.smwc.core.ra.RaProgressCache
import com.capivaraec.smwc.core.ra.RetroAchievementsClient
import com.capivaraec.smwc.core.ra.RetroAchievementsRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RetroAchievementsTest {
    private val gameList = """
        [
          {"Title":"Super Mario World","ID":228,"NumAchievements":96,"Points":875,"ImageIcon":"/Images/066503.png","Hashes":["CDD3C8C37322978CA8669B34BC89C804"]},
          {"Title":"~Hack~ Super Mario World: Learn 2 Kaizo","ID":6602,"NumAchievements":40,"Points":400,"ImageIcon":"/Images/1.png","Hashes":["aaaa"]},
          {"Title":"~Hack~ Obstinate","ID":30001,"NumAchievements":25,"Points":300,"Hashes":["bbbb","cccc"]},
          {"Title":"~Hack~ Obstinate [Subset - Bonus]","ID":30002,"NumAchievements":5,"Points":50,"Hashes":["dddd"]},
          {"Title":"~Hack~ Mario &amp; Luigi's Quest","ID":30003,"NumAchievements":10,"Points":100,"Hashes":[]}
        ]
    """.trimIndent()

    private val progressBody = """
        {"ID":30001,"Title":"~Hack~ Obstinate","NumAchievements":3,"Points":30,"ImageIcon":"/Images/2.png",
         "NumAwardedToUser":2,
         "Achievements":{
           "11":{"ID":11,"Title":"Second","Description":"d2","Points":10,"BadgeName":"222","DisplayOrder":2},
           "10":{"ID":10,"Title":"First","Points":5,"BadgeName":"111","DisplayOrder":1,"DateEarned":"2026-01-01 10:00:00","DateEarnedHardcore":"2026-01-01 10:00:00"},
           "12":{"ID":12,"Title":"Third","Points":15,"DisplayOrder":3,"DateEarned":"2026-01-02 10:00:00"}
         }}
    """.trimIndent()

    @Test
    fun gameListKeepsOnlyHacksWithoutSubsets() {
        val hacks = RaParser.parseHackList(gameList)

        assertEquals(listOf(6602L, 30001L, 30003L), hacks.map { it.id })
        val l2k = hacks.first()
        assertEquals(40, l2k.achievements)
        assertEquals(400, l2k.points)
        assertEquals("https://media.retroachievements.org/Images/1.png", l2k.iconUrl)
        assertEquals("https://retroachievements.org/game/6602", l2k.pageUrl)
        assertEquals(listOf("bbbb", "cccc"), hacks[1].hashes)
    }

    @Test
    fun camelCaseFieldsAreReadToo() {
        val game = RaParser.parseHackList("""[{"title":"~Hack~ Lower","id":7,"numAchievements":3,"points":30,"hashes":["ABC"]}]""").single()
        assertEquals(7, game.id)
        assertEquals(3, game.achievements)
        assertEquals(listOf("abc"), game.hashes)
    }

    @Test
    fun errorObjectInsteadOfListThrows() {
        assertFailsWith<RaApiException> { RaParser.parseHackList("""{"message":"Unauthenticated."}""") }
    }

    @Test
    fun progressIsParsedInDisplayOrder() {
        val progress = RaParser.parseProgress(progressBody)

        assertEquals(30001, progress.game.id)
        assertEquals(listOf("First", "Second", "Third"), progress.achievements.map { it.title })
        assertEquals(2, progress.earned)
        assertEquals(1, progress.earnedHardcore)
        assertEquals(20, progress.earnedPoints)
        assertEquals("https://media.retroachievements.org/Badge/111.png", progress.achievements[0].badgeUrl)
    }

    @Test
    fun awardsTellWhenTheGameIsFinished() {
        val beaten = RaParser.parseProgress(progressBody.replaceFirst("{", """{"HighestAwardKind":"beaten-hardcore","""))
        assertEquals("beaten-hardcore", beaten.highestAward)
        assertTrue(beaten.finished)
        assertFalse(RaParser.parseProgress(progressBody).finished) // 2 of 3, no award
    }

    @Test
    fun hashMatchWinsOverName() {
        val index = RaHackIndex(RaParser.parseHackList(gameList))

        // The ROM hash belongs to Learn 2 Kaizo, even though the name says Obstinate.
        val match = index.find("Obstinate", romMd5s = listOf("ffff", "AAAA"))!!
        assertEquals(6602, match.game.id)
        assertEquals(RaMatchKind.HASH, match.kind)
    }

    @Test
    fun unknownHashFallsBackToName() {
        val index = RaHackIndex(RaParser.parseHackList(gameList))

        val match = index.find("Obstinate", romMd5s = listOf("ffff"))!!
        assertEquals(30001, match.game.id)
        assertEquals(RaMatchKind.NAME, match.kind)
    }

    @Test
    fun namesMatchIgnoringCasePunctuationAndBaseGamePrefix() {
        val index = RaHackIndex(RaParser.parseHackList(gameList))

        assertEquals(6602, index.find("Learn 2 Kaizo")?.game?.id)
        assertEquals(6602, index.find("super mario world - learn 2 kaizo")?.game?.id)
        assertEquals(30003, index.find("Mario &amp; Luigi’s Quest")?.game?.id)
        assertEquals(30003, index.find("Mario and Luigis Quest")?.game?.id)
        assertNull(index.find("Obstinate 2"))
        assertNull(index.find("Super Mario World"))
    }

    @Test
    fun nameClashPicksTheBiggerSet() {
        val small = RaGame(1, "~Hack~ Same", achievements = 5, points = 0, iconUrl = null)
        val big = RaGame(2, "~Hack~ Same", achievements = 50, points = 0, iconUrl = null)
        assertEquals(2, RaHackIndex(listOf(small, big)).find("Same")?.game?.id)
    }

    @Test
    fun clientSendsKeyAndParameters() = runTest {
        val urls = mutableListOf<Url>()
        val client = RetroAchievementsClient(HttpClient(MockEngine { urls += it.url; respond(progressBody) }), { RaCredentials("me", "KEY") })

        client.progress(30001)

        val url = urls.single()
        assertEquals("/API/API_GetGameInfoAndUserProgress.php", url.encodedPath)
        assertEquals("KEY", url.parameters["y"])
        assertEquals("me", url.parameters["u"])
        assertEquals("30001", url.parameters["g"])
        assertEquals("1", url.parameters["a"])
    }

    @Test
    fun rejectedKeyBecomesApiError() = runTest {
        val client = RetroAchievementsClient(HttpClient(MockEngine { respond("{}", HttpStatusCode.Unauthorized) }), { RaCredentials("me", "BAD") })
        val error = assertFailsWith<RaApiException> { client.snesGameListJson() }
        assertTrue(error.message!!.contains("API key"))
    }

    @Test
    fun missingCredentialsFailBeforeAnyRequest() = runTest {
        var calls = 0
        val client = RetroAchievementsClient(HttpClient(MockEngine { calls++; respond("[]") }), { null })
        assertFailsWith<RaNotConfiguredException> { client.snesGameListJson() }
        assertEquals(0, calls)
    }

    @Test
    fun verifyReturnsTheUserNameAsSpelledByRa() = runTest {
        val urls = mutableListOf<Url>()
        val client = RetroAchievementsClient(HttpClient(MockEngine { urls += it.url; respond("""{"User":"Marcelo","ULID":"x"}""") }), { null })

        assertEquals("Marcelo", client.verify(RaCredentials("marcelo", "KEY")))
        assertEquals("/API/API_GetUserProfile.php", urls.single().encodedPath)
        assertEquals("KEY", urls.single().parameters["y"])
        assertEquals("marcelo", urls.single().parameters["u"])
    }

    @Test
    fun verifyFailsForUnknownUser() = runTest {
        val client = RetroAchievementsClient(HttpClient(MockEngine { respond("{}", HttpStatusCode.NotFound) }), { null })
        assertFailsWith<RaApiException> { client.verify(RaCredentials("nobody", "KEY")) }
    }

    private class MemoryCache(var stored: CachedText? = null) : RaListCache {
        override suspend fun read() = stored
        override suspend fun write(text: String) { stored = CachedText(savedAt = 0, text = text) }
    }

    @Test
    fun freshCacheSkipsTheNetwork() = runTest {
        var calls = 0
        val client = RetroAchievementsClient(HttpClient(MockEngine { calls++; respond("[]") }), { RaCredentials("me", "KEY") })
        val repo = RetroAchievementsRepository(client, MemoryCache(CachedText(savedAt = 1_000, text = gameList)), MemoryProgressCache(), now = { 2_000 })

        assertEquals(3, repo.index().size)
        assertEquals(0, calls)
    }

    @Test
    fun staleCacheIsRefreshedAndUsedWhenOffline() = runTest {
        val stale = CachedText(savedAt = 0, text = gameList)
        val offline = RetroAchievementsClient(HttpClient(MockEngine { error("offline") }), { RaCredentials("me", "KEY") })
        assertEquals(3, RetroAchievementsRepository(offline, MemoryCache(stale), MemoryProgressCache(), now = { Long.MAX_VALUE }).index().size)

        val cache = MemoryCache(stale)
        val online = RetroAchievementsClient(HttpClient(MockEngine { respond("[]") }), { RaCredentials("me", "KEY") })
        assertEquals(0, RetroAchievementsRepository(online, cache, MemoryProgressCache(), now = { Long.MAX_VALUE }).index().size)
        assertEquals("[]", cache.stored?.text)
    }

    private class MemoryProgressCache : RaProgressCache {
        val items = HashMap<Long, CachedText>()
        override suspend fun read(gameId: Long) = items[gameId]
        override suspend fun write(gameId: Long, text: String) { items[gameId] = CachedText(savedAt = clock, text = text) }
        override suspend fun clear() = items.clear()
        var clock = 0L
    }

    private val day = 24 * 60 * 60 * 1000L

    private fun progressRepo(disk: MemoryProgressCache, now: () -> Long, respond: () -> String): Pair<RetroAchievementsRepository, () -> Int> {
        var calls = 0
        val client = RetroAchievementsClient(HttpClient(MockEngine { calls++; respond(respond()) }), { RaCredentials("me", "KEY") })
        return RetroAchievementsRepository(client, MemoryCache(), disk, now = now) to { calls }
    }

    @Test
    fun progressFromDiskIsUsedForADay() = runTest {
        val disk = MemoryProgressCache().apply { items[30001] = CachedText(savedAt = 0, text = progressBody) }
        var time = day - 1
        val (repo, calls) = progressRepo(disk, { time }) { progressBody }

        assertEquals(2, repo.progress(30001).earned)
        assertEquals(0, calls())

        time = day + 1 // a day later the server is asked again
        repo.progress(30001)
        assertEquals(1, calls())
    }

    @Test
    fun progressIsSavedToDiskAndMemory() = runTest {
        val disk = MemoryProgressCache()
        val (repo, calls) = progressRepo(disk, { 0 }) { progressBody }

        repo.progress(30001)
        repo.progress(30001)
        assertEquals(1, calls())
        assertEquals(progressBody, disk.items[30001]?.text)
    }

    @Test
    fun refreshAlwaysAsksTheServer() = runTest {
        val disk = MemoryProgressCache().apply { items[30001] = CachedText(savedAt = 0, text = progressBody) }
        val (repo, calls) = progressRepo(disk, { 0 }) { progressBody }

        repo.progress(30001, refresh = true)
        assertEquals(1, calls())
    }

    @Test
    fun oldProgressIsUsedWhenOfflineButNotOnRefresh() = runTest {
        val disk = MemoryProgressCache().apply { items[30001] = CachedText(savedAt = 0, text = progressBody) }
        val (repo, _) = progressRepo(disk, { 10 * day }) { error("offline") }

        assertEquals(2, repo.progress(30001).earned)
        assertFailsWith<Exception> { repo.progress(30001, refresh = true) }
    }

    @Test
    fun clearingProgressEmptiesTheDiskCache() = runTest {
        val disk = MemoryProgressCache()
        val (repo, calls) = progressRepo(disk, { 0 }) { progressBody }

        repo.progress(30001)
        repo.clearProgress()
        assertTrue(disk.items.isEmpty())
        repo.progress(30001)
        assertEquals(2, calls())
    }
}
