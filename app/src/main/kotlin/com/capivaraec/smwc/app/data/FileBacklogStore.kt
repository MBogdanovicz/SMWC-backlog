package com.capivaraec.smwc.app.data

import com.capivaraec.smwc.core.backlog.BacklogDocument
import com.capivaraec.smwc.core.backlog.LocalBacklogStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Keeps the backlog as `backlog.json` in the app's private storage: the same document that is
 * synced to Google Drive, so no database mapping is needed.
 */
class FileBacklogStore(dir: File) : LocalBacklogStore {
    private val file = File(dir, "backlog.json")

    override suspend fun read(): BacklogDocument = withContext(Dispatchers.IO) {
        if (file.exists()) BacklogDocument.fromJson(file.readText()) else BacklogDocument()
    }

    override suspend fun write(document: BacklogDocument) = withContext(Dispatchers.IO) {
        // Write then rename so a crash mid-write never leaves a half-written backlog.
        val tmp = File(file.parentFile, "backlog.json.tmp")
        tmp.writeText(document.toJson())
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
        Unit
    }
}
