package com.capivaraec.smwc.core.rom

import com.capivaraec.smwc.core.patch.PatchException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * RetroAchievements hashes of the ROMs a hack's download produces. The download URL changes
 * with each new version on SMW Central, so it tells when the hashes are outdated.
 */
@Serializable
data class HackRomHashes(
    val hackId: Long,
    val downloadUrl: String,
    val md5s: List<String> = emptyList(),
    /** Why patching failed (e.g. patch for another base ROM); not retried until the URL changes. */
    val error: String? = null,
) {
    companion object {
        private val codec = Json { ignoreUnknownKeys = true }
        private val listSerializer = ListSerializer(serializer())

        fun listToJson(items: List<HackRomHashes>): String = codec.encodeToString(listSerializer, items)
        fun listFromJson(json: String): List<HackRomHashes> = codec.decodeFromString(listSerializer, json)
    }
}

/** Where the computed hashes are kept between app launches. */
interface RomHashStore {
    suspend fun read(): List<HackRomHashes>
    suspend fun write(items: List<HackRomHashes>)
}

/** Computes (once per hack version) and remembers the ROM hashes of hacks. */
class RomHashRepository(private val store: RomHashStore, private val patcher: HackPatcher) {
    private val mutex = Mutex()
    private val _hashes = MutableStateFlow<Map<Long, HackRomHashes>>(emptyMap())
    val hashes: StateFlow<Map<Long, HackRomHashes>> = _hashes.asStateFlow()
    private var loaded = false

    suspend fun load() = mutex.withLock { ensureLoaded() }

    /** True if [hackId] has no hashes yet for this [downloadUrl] (a new hack or a new version). */
    suspend fun isOutdated(hackId: Long, downloadUrl: String): Boolean = mutex.withLock {
        ensureLoaded()
        _hashes.value[hackId]?.downloadUrl != downloadUrl
    }

    /**
     * Returns the hashes for this version of the hack, downloading and patching only when they
     * are not known yet. A patch failure is remembered; a missing clean ROM or a download
     * error is thrown and not remembered, so it is retried later.
     */
    suspend fun resolve(hackId: Long, downloadUrl: String): HackRomHashes = mutex.withLock {
        ensureLoaded()
        _hashes.value[hackId]?.takeIf { it.downloadUrl == downloadUrl }?.let { return it }
        val result = try {
            HackRomHashes(hackId, downloadUrl, md5s = patcher.patch(downloadUrl).map { it.md5 }.distinct())
        } catch (e: PatchException) {
            HackRomHashes(hackId, downloadUrl, error = e.message)
        }
        _hashes.value = _hashes.value + (hackId to result)
        store.write(_hashes.value.values.toList())
        result
    }

    private suspend fun ensureLoaded() {
        if (!loaded) {
            _hashes.value = store.read().associateBy { it.hackId }
            loaded = true
        }
    }
}
