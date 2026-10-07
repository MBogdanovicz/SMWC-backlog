package com.capivaraec.smwc.core

import com.capivaraec.smwc.core.api.SmwcApiException
import com.capivaraec.smwc.core.api.SmwcParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

// The fixtures mirror the response shape reported for SMW Central's ajax.php; it could not be
// fetched live from the build machine, so confirm against a real response on the device.
class SmwcParserTest {
    private fun fixture(name: String) = javaClass.getResource("/$name")!!.readText()

    @Test
    fun parsesSearchPage() {
        val page = SmwcParser.parseSearchPage(fixture("search_page.json"))

        assertEquals(1, page.page)
        assertEquals(42, page.lastPage)
        assertEquals(2087, page.total)
        assertTrue(page.hasNext)
        assertEquals(2, page.items.size)

        val first = page.items[0]
        assertEquals(38044, first.id)
        assertEquals(listOf("Alice", "Bob"), first.authors)
        assertEquals("Standard: Easy", first.difficulty)
        assertEquals("12 exit(s)", first.length)
        assertEquals(4.6, first.rating)
        assertEquals(1234, first.downloads)

        val second = page.items[1]
        assertEquals(listOf("Carol"), second.authors)
        assertEquals("Kaizo: Hard", second.difficulty)
        assertNull(second.rating)
        assertEquals(10, second.downloads)
        assertEquals(1753000000, second.submittedAt)
    }

    @Test
    fun parsesHackDetail() {
        val detail = SmwcParser.parseHackDetail(fixture("hack_detail.json"))

        assertEquals("Super Example World", detail.summary.name)
        assertEquals("<p>Uma hack <b>legal</b>.</p>", detail.descriptionHtml)
        assertEquals(listOf("vanilla", "short"), detail.tags)
        assertEquals("https://dl.smwcentral.net/38044/sew.zip", detail.downloadUrl)
        assertEquals(512000, detail.sizeBytes)
        assertEquals(
            listOf("https://www.smwcentral.net/image/38044_1.png", "https://example.com/2.png"),
            detail.screenshotUrls,
        )
        assertEquals("https://www.smwcentral.net/?p=section&a=details&id=38044", detail.pageUrl)
    }

    @Test
    fun surfacesApiErrorMessage() {
        val error = assertFailsWith<SmwcApiException> { SmwcParser.parseHackDetail("""{"message":"File not found."}""") }
        assertEquals("File not found.", error.message)
    }
}

class DifficultyTest {
    @Test
    fun knownIdsGetTheirNamesAndOthersPassThrough() {
        assertEquals("Newcomer", com.capivaraec.smwc.core.model.Difficulty.displayName("diff_1"))
        assertEquals("Expert", com.capivaraec.smwc.core.model.Difficulty.displayName("diff_5"))
        assertEquals("Grandmaster", com.capivaraec.smwc.core.model.Difficulty.displayName("diff_7"))
        assertEquals("Kaizo: Hard", com.capivaraec.smwc.core.model.Difficulty.displayName("Kaizo: Hard"))
        assertNull(com.capivaraec.smwc.core.model.Difficulty.displayName(null))
    }
}

class LengthDisplayTest {
    @Test
    fun bareNumbersBecomeExits() {
        assertEquals("1 exit", com.capivaraec.smwc.core.model.lengthDisplay("1"))
        assertEquals("20 exits", com.capivaraec.smwc.core.model.lengthDisplay(" 20 "))
        assertEquals("0 exits", com.capivaraec.smwc.core.model.lengthDisplay("0"))
        assertEquals("12 exit(s)", com.capivaraec.smwc.core.model.lengthDisplay("12 exit(s)"))
        assertNull(com.capivaraec.smwc.core.model.lengthDisplay(""))
        assertNull(com.capivaraec.smwc.core.model.lengthDisplay(null))
    }
}

class LatestVersionTest {
    @Test
    fun obsoleteFilePointsToTheCurrentVersion() {
        val obsolete = """{"id":42825,"name":"Toad's World","obsoleted_by":42900,
            "versions":[{"id":43075,"name":"Toad's World","obsolete":false},{"id":42825,"name":"Toad's World","obsolete":true}]}"""
        assertEquals(43075, com.capivaraec.smwc.core.api.SmwcParser.parseHackDetail(obsolete).latestVersionId)

        val noList = """{"id":42825,"name":"Toad's World","obsoleted_by":43075}"""
        assertEquals(43075, com.capivaraec.smwc.core.api.SmwcParser.parseHackDetail(noList).latestVersionId)

        val current = """{"id":43075,"name":"Toad's World","obsoleted_by":null,"versions":[{"id":43075,"obsolete":false}]}"""
        assertNull(com.capivaraec.smwc.core.api.SmwcParser.parseHackDetail(current).latestVersionId)
    }
}

class ThumbnailTest {
    @Test
    fun firstImageIsTheThumbnail() {
        val body = """{"current_page":1,"last_page":1,"data":[{"id":1,"name":"A","images":["//dl.smwcentral.net/image/1.png","//dl.smwcentral.net/image/2.png"]},{"id":2,"name":"B"}]}"""
        val items = com.capivaraec.smwc.core.api.SmwcParser.parseSearchPage(body).items
        assertEquals("https://dl.smwcentral.net/image/1.png", items[0].thumbnailUrl)
        assertNull(items[1].thumbnailUrl)
    }
}
