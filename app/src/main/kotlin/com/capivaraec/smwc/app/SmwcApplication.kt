package com.capivaraec.smwc.app

import android.app.Application
import android.util.Log
import com.capivaraec.smwc.app.data.AppPreferences
import com.capivaraec.smwc.app.data.CleanRomStore
import com.capivaraec.smwc.app.data.DriveAuthorizer
import com.capivaraec.smwc.app.data.FileBacklogStore
import com.capivaraec.smwc.app.data.FileRaListCache
import com.capivaraec.smwc.app.data.FileRaProgressCache
import com.capivaraec.smwc.app.data.FileRomHashStore
import com.capivaraec.smwc.app.data.RaCredentialsStore
import com.capivaraec.smwc.core.api.SmwcClient
import com.capivaraec.smwc.core.backlog.BacklogRepository
import com.capivaraec.smwc.core.drive.DriveAppDataClient
import com.capivaraec.smwc.core.ra.RetroAchievementsClient
import com.capivaraec.smwc.core.ra.RetroAchievementsRepository
import com.capivaraec.smwc.core.rom.HackPatcher
import com.capivaraec.smwc.core.rom.RomHashRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Holds the app-wide singletons (manual DI; the app is small enough not to need Hilt). */
class SmwcApplication : Application() {
    val appScope = CoroutineScope(SupervisorJob())

    private val http by lazy {
        HttpClient(OkHttp) {
            // Long enough for a hack's patch download (usually under a few MB) on mobile data.
            install(HttpTimeout) { requestTimeoutMillis = 60_000 }
            defaultRequest { header("User-Agent", "SMWC-Backlog-Android/0.1 (personal use)") }
        }
    }

    val smwc by lazy { SmwcClient(http) }
    val backlog by lazy { BacklogRepository(FileBacklogStore(filesDir)) }
    val driveAuthorizer by lazy { DriveAuthorizer(this) }
    val drive by lazy { DriveAppDataClient(http, driveAuthorizer::accessToken) }

    /** RetroAchievements user name and API key, entered in the Achievements tab; null until then. */
    val raCredentials by lazy { RaCredentialsStore(this) }
    val raClient by lazy { RetroAchievementsClient(http, credentials = { raCredentials.credentials.value }) }
    val retroAchievements by lazy { RetroAchievementsRepository(raClient, FileRaListCache(cacheDir), FileRaProgressCache(cacheDir)) }

    val cleanRom by lazy { CleanRomStore(this) }

    /** Downloads and applies hack patches; shared by RetroAchievements matching and patching. */
    val hackPatcher by lazy { HackPatcher(http, cleanRom) }
    val romHashes by lazy { RomHashRepository(FileRomHashStore(filesDir), hackPatcher) }

    val preferences by lazy { AppPreferences(this) }

    override fun onCreate() {
        super.onCreate()
        appScope.launch { backlog.load() }
        appScope.launch { hashBacklogHacks() }
        appScope.launch { fillInBacklogDetails() }
    }

    /**
     * Reads the release date, exits, rating and latest version of backlog hacks from SMW Central
     * in the background: once for entries added before they were stored, then weekly, since
     * ratings change and new versions come out. Used for sorting, filtering and the "Updated" badge.
     */
    private suspend fun fillInBacklogDetails() {
        backlog.load()
        backlog.state
            .map { backlog.entriesNeedingDetails(maxAgeMillis = 7 * 24 * 60 * 60 * 1000L) }
            .distinctUntilChanged()
            .collectLatest { ids ->
                for (id in ids) {
                    runCatching { backlog.updateDetails(smwc.getHack(id)) }
                        .onFailure { Log.w("SmwcBacklog", "Could not get details of hack $id", it) }
                }
            }
    }

    /**
     * With RetroAchievements set up and a clean ROM chosen, hashes every backlog hack that has no
     * hashes yet (once per hack), so sets can be matched by ROM hash.
     */
    private suspend fun hashBacklogHacks() {
        romHashes.load()
        combine(backlog.state, cleanRom.available, raCredentials.credentials) { doc, hasRom, credentials ->
            // Hacks added by name are not on SMW Central, so there is no patch to hash.
            if (hasRom && credentials != null) doc.visible.filterNot { it.custom }.map { it.hackId } else emptyList()
        }
            .distinctUntilChanged()
            .collectLatest { ids ->
                for (id in ids.filterNot { it in romHashes.hashes.value }) {
                    runCatching {
                        val url = smwc.getHack(id).downloadUrl ?: return@runCatching
                        romHashes.resolve(id, url)
                    }.onFailure { Log.w("SmwcRom", "Could not hash hack $id", it) }
                }
            }
    }
}
