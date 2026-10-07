package com.capivaraec.smwc.app.data

import com.capivaraec.smwc.core.rom.HackRomHashes
import com.capivaraec.smwc.core.rom.RomHashStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Keeps the computed ROM hashes in `rom_hashes.json`, so each hack version is patched only once. */
class FileRomHashStore(dir: File) : RomHashStore {
    private val file = File(dir, "rom_hashes.json")

    override suspend fun read(): List<HackRomHashes> = withContext(Dispatchers.IO) {
        if (file.exists()) runCatching { HackRomHashes.listFromJson(file.readText()) }.getOrDefault(emptyList()) else emptyList()
    }

    override suspend fun write(items: List<HackRomHashes>) = withContext(Dispatchers.IO) {
        file.writeText(HackRomHashes.listToJson(items))
    }
}
