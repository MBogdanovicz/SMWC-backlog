package com.capivaraec.smwc.app.data

import com.capivaraec.smwc.core.ra.CachedText
import com.capivaraec.smwc.core.ra.RaProgressCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Keeps the user's progress in each set as `ra_progress/<gameId>.json`; the file date is its age. */
class FileRaProgressCache(dir: File, name: String = "ra_progress") : RaProgressCache {
    private val folder = File(dir, name)

    private fun file(gameId: Long) = File(folder, "$gameId.json")

    override suspend fun read(gameId: Long): CachedText? = withContext(Dispatchers.IO) {
        val file = file(gameId)
        if (file.exists()) CachedText(savedAt = file.lastModified(), text = file.readText()) else null
    }

    override suspend fun write(gameId: Long, text: String) = withContext(Dispatchers.IO) {
        folder.mkdirs()
        file(gameId).writeText(text)
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        folder.deleteRecursively()
        Unit
    }
}
