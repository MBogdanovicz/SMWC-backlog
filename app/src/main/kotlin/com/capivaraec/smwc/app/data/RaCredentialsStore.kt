package com.capivaraec.smwc.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import com.capivaraec.smwc.core.ra.RaCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keeps the RetroAchievements user name and API key on this device only, encrypted with an
 * AES key that lives in the Android Keystore (it cannot be read out of the phone). The file is
 * in the no-backup folder, so it is not copied by Android's cloud backup either.
 */
class RaCredentialsStore(context: Context) {
    private val file = File(context.noBackupFilesDir, "ra_credentials.bin")
    private val _credentials = MutableStateFlow(load())
    val credentials: StateFlow<RaCredentials?> = _credentials.asStateFlow()

    suspend fun save(credentials: RaCredentials) = withContext(Dispatchers.IO) {
        val plain = buildJsonObject {
            put("username", credentials.username)
            put("apiKey", credentials.apiKey)
        }.toString().toByteArray()
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        // File layout: IV length, IV, ciphertext (GCM includes the authentication tag).
        file.writeBytes(byteArrayOf(cipher.iv.size.toByte()) + cipher.iv + cipher.doFinal(plain))
        _credentials.value = credentials
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        file.delete()
        _credentials.value = null
    }

    private fun load(): RaCredentials? = runCatching {
        if (!file.exists()) return null
        val bytes = file.readBytes()
        val ivSize = bytes[0].toInt()
        val iv = bytes.copyOfRange(1, 1 + ivSize)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
        val stored = Json.parseToJsonElement(String(cipher.doFinal(bytes, 1 + ivSize, bytes.size - 1 - ivSize))).jsonObject
        RaCredentials(stored.getValue("username").jsonPrimitive.content, stored.getValue("apiKey").jsonPrimitive.content)
    }.onFailure {
        // E.g. the Keystore key was lost; the user just enters the credentials again.
        Log.w("SmwcRa", "Could not read the saved RetroAchievements credentials", it)
        file.delete()
    }.getOrNull()

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "smwc_ra_credentials"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
