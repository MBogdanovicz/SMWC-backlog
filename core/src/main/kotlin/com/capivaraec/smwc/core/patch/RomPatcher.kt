package com.capivaraec.smwc.core.patch

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/** A patch file found in a hack's download. */
class PatchFile(val name: String, val format: PatchFormat, val bytes: ByteArray)

/**
 * A ROM made by applying [patchName] to the clean base ROM. [rom] has no copier header and
 * [md5] is its RetroAchievements hash. [certain] is false for IPS patches, which carry no
 * checksum, so the result is a best guess.
 */
class PatchedRom(val patchName: String, val rom: ByteArray, val md5: String, val certain: Boolean) {
    /** File name for saving, e.g. "Obstinate v1.0.sfc". */
    val fileName: String get() = patchName.substringBeforeLast('.') + ".sfc"
}

object PatchArchive {
    /** The BPS/IPS patches in a download: a zip (folders included) or a bare patch file. */
    fun extract(download: ByteArray, downloadName: String = "patch"): List<PatchFile> {
        PatchFormat.detect(download)?.let { return listOf(PatchFile(downloadName, it, download)) }
        val patches = mutableListOf<PatchFile>()
        ZipInputStream(ByteArrayInputStream(download)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory || entry.name.contains("__MACOSX")) continue
                val ext = entry.name.substringAfterLast('.', "").lowercase()
                if (ext != "bps" && ext != "ips") continue
                val bytes = zip.readBytes()
                PatchFormat.detect(bytes)?.let { patches += PatchFile(entry.name.substringAfterLast('/'), it, bytes) }
            }
        }
        if (patches.isEmpty()) throw PatchException("The download has no BPS or IPS patch")
        return patches.sortedBy { it.name.lowercase() }
    }
}

object RomPatcher {
    /**
     * Applies [patch] to [cleanRom] (no copier header). Returns the possible results, best first:
     * one for BPS; for IPS up to two, since old IPS patches often expect a headered ROM.
     */
    fun apply(cleanRom: ByteArray, patch: PatchFile): List<PatchedRom> = when (patch.format) {
        PatchFormat.BPS -> {
            val source = if (BpsPatcher.sourceCrc32(patch.bytes) == crc32(cleanRom)) cleanRom else withBlankHeader(cleanRom)
            val rom = SnesRom.withoutCopierHeader(BpsPatcher.apply(source, patch.bytes))
            listOf(PatchedRom(patch.name, rom, SnesRom.md5(rom), certain = true))
        }
        PatchFormat.IPS -> {
            val results = listOf(cleanRom, withBlankHeader(cleanRom))
                .map { SnesRom.withoutCopierHeader(IpsPatcher.apply(it, patch.bytes)) }
                .distinctBy { SnesRom.md5(it) }
                .sortedByDescending { SnesRom.hasValidLoRomChecksum(it) }
            results.map { PatchedRom(patch.name, it, SnesRom.md5(it), certain = false) }
        }
    }

    private fun withBlankHeader(rom: ByteArray) = ByteArray(SnesRom.COPIER_HEADER_SIZE) + rom
}
