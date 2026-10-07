package com.capivaraec.smwc.app.data

import com.capivaraec.smwc.core.ra.CachedText
import com.capivaraec.smwc.core.ra.RaListCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Keeps the RetroAchievements SNES game list in the app's cache folder; the file date is its age. */
class FileRaListCache(dir: File) : RaListCache {
    private val file = File(dir, "ra_snes_games.json")

    override suspend fun read(): CachedText? = withContext(Dispatchers.IO) {
        if (file.exists()) CachedText(savedAt = file.lastModified(), text = file.readText()) else null
    }

    override suspend fun write(text: String) = withContext(Dispatchers.IO) {
        file.writeText(text)
    }
}
