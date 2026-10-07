package com.capivaraec.smwc.core.rom

import com.capivaraec.smwc.core.patch.PatchArchive
import com.capivaraec.smwc.core.patch.PatchedRom
import com.capivaraec.smwc.core.patch.RomPatcher
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.decodeURLPart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The user's clean Super Mario World ROM, without copier header; null until they pick one. */
fun interface CleanRomSource {
    suspend fun read(): ByteArray?
}

class CleanRomMissingException : Exception("Choose a clean Super Mario World ROM first")

class PatchDownloadException(message: String) : Exception(message)

/**
 * Downloads a hack's patch from SMW Central and applies it to the clean ROM. Used to hash
 * hacks for RetroAchievements and to make a playable ROM.
 */
class HackPatcher(private val http: HttpClient, private val cleanRom: CleanRomSource) {
    /** Every ROM the download can produce (one per patch file, best guess first). */
    suspend fun patch(downloadUrl: String): List<PatchedRom> {
        val clean = cleanRom.read() ?: throw CleanRomMissingException()
        val response = http.get(downloadUrl)
        if (response.status.value !in 200..299) throw PatchDownloadException("Download failed (HTTP ${response.status.value})")
        val bytes = response.bodyAsBytes()
        return withContext(Dispatchers.Default) {
            // A download may also hold patches for other base ROMs; only fail if none applies.
            val results = PatchArchive.extract(bytes, downloadName = downloadUrl.substringAfterLast('/').decodeURLPart())
                .map { runCatching { RomPatcher.apply(clean, it) } }
            results.flatMap { it.getOrNull().orEmpty() }.ifEmpty { throw results.first().exceptionOrNull()!! }
        }
    }
}
