package com.capivaraec.smwc.app.data

import android.content.Context
import android.net.Uri
import com.capivaraec.smwc.core.patch.SnesRom
import com.capivaraec.smwc.core.rom.CleanRomSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The user's clean Super Mario World (USA) ROM, copied (without copier header) into the app's
 * private storage once the user picks it. It never leaves the device.
 */
class CleanRomStore(private val context: Context) : CleanRomSource {
    private val file = File(context.filesDir, "clean_smw.sfc")
    private val _available = MutableStateFlow(file.exists())
    val available: StateFlow<Boolean> = _available.asStateFlow()

    override suspend fun read(): ByteArray? = withContext(Dispatchers.IO) {
        if (file.exists()) file.readBytes() else null
    }

    /** Checks and stores the ROM at [uri]; throws `PatchException` if it is not a clean SMW (USA). */
    suspend fun import(uri: Uri) = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Could not read the selected file")
        val clean = SnesRom.requireCleanSmw(bytes)
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeBytes(clean)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
        _available.value = true
    }
}
