package com.capivaraec.smwc.core

import com.capivaraec.smwc.core.patch.BpsPatcher
import com.capivaraec.smwc.core.patch.IpsPatcher
import com.capivaraec.smwc.core.patch.PatchArchive
import com.capivaraec.smwc.core.patch.PatchException
import com.capivaraec.smwc.core.patch.PatchFile
import com.capivaraec.smwc.core.patch.PatchFormat
import com.capivaraec.smwc.core.patch.RomPatcher
import com.capivaraec.smwc.core.patch.SnesRom
import com.capivaraec.smwc.core.rom.CleanRomMissingException
import com.capivaraec.smwc.core.rom.HackPatcher
import com.capivaraec.smwc.core.rom.HackRomHashes
import com.capivaraec.smwc.core.rom.RomHashRepository
import com.capivaraec.smwc.core.rom.RomHashStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PatchTest {
    private val source = "ABCDEFGHIJKLMNOP".toByteArray()

    /** Writes BPS patches by hand, one action at a time. */
    private class BpsBuilder(private val source: ByteArray, targetSize: Int) {
        private val out = ByteArrayOutputStream()

        init {
            out.write("BPS1".toByteArray())
            number(source.size.toLong()); number(targetSize.toLong()); number(0)
        }

        private fun number(value: Long) {
            var data = value
            while (true) {
                val x = (data and 0x7F).toInt()
                data = data shr 7
                if (data == 0L) { out.write(0x80 or x); return }
                out.write(x)
                data--
            }
        }

        private fun action(command: Int, length: Int) = number(((length - 1).toLong() shl 2) or command.toLong())
        private fun offset(delta: Int) = number((kotlin.math.abs(delta).toLong() shl 1) or (if (delta < 0) 1L else 0L))

        fun sourceRead(length: Int) = apply { action(0, length) }
        fun targetRead(bytes: String) = apply { action(1, bytes.length); out.write(bytes.toByteArray()) }
        fun sourceCopy(length: Int, delta: Int) = apply { action(2, length); offset(delta) }
        fun targetCopy(length: Int, delta: Int) = apply { action(3, length); offset(delta) }

        fun build(target: ByteArray): ByteArray {
            fun crc(b: ByteArray) = CRC32().apply { update(b) }.value
            fun le(v: Long) = ByteArray(4) { ((v shr (8 * it)) and 0xFF).toByte() }
            out.write(le(crc(source))); out.write(le(crc(target)))
            val body = out.toByteArray()
            return body + le(crc(body))
        }
    }

    @Test
    fun bpsAppliesAllFourCommands() {
        val target = "ABCDxyzMNOPOPOPO".toByteArray()
        val patch = BpsBuilder(source, target.size)
            .sourceRead(4) // ABCD
            .targetRead("xyz") // xyz (target 4..6)
            .sourceCopy(4, delta = 12) // MNOP from source 12
            .targetCopy(5, delta = 9) // OPOPO from target 9, overlapping its own output
            .build(target)

        assertContentEquals(target, BpsPatcher.apply(source, patch))
        assertEquals(PatchFormat.BPS, PatchFormat.detect(patch))
    }

    @Test
    fun bpsRejectsAnotherBaseRom() {
        val target = "ABCD".toByteArray()
        val patch = BpsBuilder(source, target.size).sourceRead(4).build(target)
        val other = "abcdefghijklmnop".toByteArray()
        assertFailsWith<PatchException> { BpsPatcher.apply(other, patch) }
    }

    @Test
    fun bpsRejectsCorruptPatch() {
        val target = "ABCD".toByteArray()
        val patch = BpsBuilder(source, target.size).sourceRead(4).build(target)
        patch[6] = (patch[6] + 1).toByte()
        assertFailsWith<PatchException> { BpsPatcher.apply(source, patch) }
    }

    private fun ips(vararg records: ByteArray, truncateTo: Int? = null): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("PATCH".toByteArray())
        records.forEach { out.write(it) }
        out.write("EOF".toByteArray())
        truncateTo?.let { out.write(byteArrayOf((it shr 16).toByte(), (it shr 8).toByte(), it.toByte())) }
        return out.toByteArray()
    }

    private fun record(offset: Int, data: String) =
        byteArrayOf((offset shr 16).toByte(), (offset shr 8).toByte(), offset.toByte(), 0, data.length.toByte()) +
            data.toByteArray(Charsets.ISO_8859_1)

    private fun rle(offset: Int, count: Int, value: Char) =
        byteArrayOf((offset shr 16).toByte(), (offset shr 8).toByte(), offset.toByte(), 0, 0, (count shr 8).toByte(), count.toByte(), value.code.toByte())

    @Test
    fun ipsWritesRecordsRunsAndGrowsTheRom() {
        val patched = IpsPatcher.apply(source, ips(record(1, "xy"), rle(4, 3, 'z'), record(18, "!!")))
        assertEquals("AxyDzzzHIJKLMNOP\u0000\u0000!!", String(patched))
    }

    @Test
    fun ipsCanTruncate() {
        assertEquals("ABCD", String(IpsPatcher.apply(source, ips(truncateTo = 4))))
    }

    @Test
    fun ipsRejectsTruncatedPatch() {
        assertFailsWith<PatchException> { IpsPatcher.apply(source, ips(record(1, "xy")).copyOf(9)) }
    }

    @Test
    fun copierHeaderIsDetectedAndIgnoredByTheHash() {
        val rom = ByteArray(1024) { it.toByte() }
        val headered = ByteArray(512) + rom
        assertTrue(SnesRom.hasCopierHeader(headered))
        assertFalse(SnesRom.hasCopierHeader(rom))
        assertEquals(SnesRom.raHash(rom), SnesRom.raHash(headered))
        assertEquals(SnesRom.md5(rom), SnesRom.raHash(headered))
    }

    @Test
    fun anyRomThatIsNotCleanSmwIsRejected() {
        assertFailsWith<PatchException> { SnesRom.requireCleanSmw(ByteArray(512 * 1024)) }
    }

    /** Sets a valid LoROM checksum, as Lunar Magic does when saving. */
    private fun withChecksum(rom: ByteArray): ByteArray = rom.copyOf().also {
        // Complement + checksum always add 0x1FE to the sum, so compute it with 0xFFFF/0x0000 in place.
        it[0x7FDC] = 0xFF.toByte(); it[0x7FDD] = 0xFF.toByte(); it[0x7FDE] = 0; it[0x7FDF] = 0
        val checksum = SnesRom.computeChecksum(it)
        val complement = checksum xor 0xFFFF
        it[0x7FDC] = complement.toByte(); it[0x7FDD] = (complement shr 8).toByte()
        it[0x7FDE] = checksum.toByte(); it[0x7FDF] = (checksum shr 8).toByte()
    }

    @Test
    fun ipsForAHeaderedRomIsDetectedByTheInternalChecksum() {
        val rom = withChecksum(ByteArray(0x10000) { (it * 7).toByte() })
        val expected = withChecksum(rom.copyOf().also { "hack".toByteArray().copyInto(it, 0x100) })
        // Made for a headered ROM: every offset is 0x200 further than in the bare ROM.
        val patch = PatchFile(
            "hack.ips",
            PatchFormat.IPS,
            ips(record(0x200 + 0x100, "hack"), record(0x200 + 0x7FDC, String(expected.copyOfRange(0x7FDC, 0x7FE0), Charsets.ISO_8859_1))),
        )
        val results = RomPatcher.apply(rom, patch)

        assertTrue(SnesRom.hasValidLoRomChecksum(rom))
        assertEquals(2, results.size)
        assertContentEquals(expected, results.first().rom)
        assertFalse(SnesRom.hasValidLoRomChecksum(results.last().rom))
        assertFalse(results.first().certain)
        assertEquals("hack.sfc", results.first().fileName)
    }

    @Test
    fun bpsMadeForTheHeaderlessRomIsCertain() {
        val target = "ABCDxyz".toByteArray()
        val patch = PatchFile("a.bps", PatchFormat.BPS, BpsBuilder(source, target.size).sourceRead(4).targetRead("xyz").build(target))
        val result = RomPatcher.apply(source, patch).single()
        assertTrue(result.certain)
        assertEquals(SnesRom.md5(target), result.md5)
    }

    private fun zip(vararg files: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, bytes) in files) {
                zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test
    fun archiveFindsPatchesInFoldersAndSkipsOtherFiles() {
        val download = zip(
            "readme.txt" to "hi".toByteArray(),
            "patches/B Hack.bps" to BpsBuilder(source, 4).sourceRead(4).build("ABCD".toByteArray()),
            "A Hack.ips" to ips(),
            "__MACOSX/._A Hack.ips" to "junk".toByteArray(),
        )
        val patches = PatchArchive.extract(download)
        assertEquals(listOf("A Hack.ips", "B Hack.bps"), patches.map { it.name })
        assertEquals(listOf(PatchFormat.IPS, PatchFormat.BPS), patches.map { it.format })
    }

    @Test
    fun archiveWithoutPatchesFails() {
        assertFailsWith<PatchException> { PatchArchive.extract(zip("readme.txt" to "hi".toByteArray())) }
    }

    private class MemoryStore : RomHashStore {
        var items = emptyList<HackRomHashes>()
        override suspend fun read() = items
        override suspend fun write(items: List<HackRomHashes>) { this.items = items }
    }

    @Test
    fun hashesAreComputedOncePerVersionAndFailuresRemembered() = runTest {
        val target = "ABCDxyz".toByteArray()
        val good = BpsBuilder(source, target.size).sourceRead(4).targetRead("xyz").build(target)
        var downloads = 0
        val http = HttpClient(MockEngine { request ->
            downloads++
            respond(if (request.url.encodedPath.contains("bad")) zip("x.ips" to "PATCH".toByteArray()) else zip("h.bps" to good))
        })
        val store = MemoryStore()
        val repo = RomHashRepository(store, HackPatcher(http) { source })

        val first = repo.resolve(1, "https://dl.example/1/v1.zip")
        repo.resolve(1, "https://dl.example/1/v1.zip")
        assertEquals(listOf(SnesRom.md5(target)), first.md5s)
        assertEquals(1, downloads)

        repo.resolve(1, "https://dl.example/1/v2.zip") // new version on SMW Central
        assertEquals(2, downloads)

        val bad = repo.resolve(2, "https://dl.example/2/bad.zip")
        assertTrue(bad.md5s.isEmpty())
        assertTrue(bad.error!!.isNotEmpty())
        repo.resolve(2, "https://dl.example/2/bad.zip")
        assertEquals(3, downloads)
        assertEquals(2, store.items.size)
    }

    @Test
    fun missingCleanRomIsNotRemembered() = runTest {
        val repo = RomHashRepository(MemoryStore(), HackPatcher(HttpClient(MockEngine { respond("") })) { null })
        assertFailsWith<CleanRomMissingException> { repo.resolve(1, "https://dl.example/1.zip") }
        assertNull(repo.hashes.value[1])
    }
}
