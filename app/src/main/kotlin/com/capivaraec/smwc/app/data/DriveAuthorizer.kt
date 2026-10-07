package com.capivaraec.smwc.app.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.capivaraec.smwc.core.drive.DriveAppDataClient
import kotlinx.coroutines.tasks.await

/**
 * Gets an OAuth access token for the `drive.appdata` scope with Google Identity's
 * AuthorizationClient. The first time, Google needs the user's consent: [accessToken] then
 * throws [DriveConsentRequired], and the activity launches its [PendingIntent].
 */
class DriveAuthorizer(private val context: Context) {
    private val request = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(DriveAppDataClient.SCOPE)))
        .build()

    suspend fun accessToken(): String {
        val result = Identity.getAuthorizationClient(context).authorize(request).await()
        if (result.hasResolution()) throw DriveConsentRequired(result.pendingIntent!!)
        return result.accessToken ?: error("Google returned no access token")
    }

    /**
     * Call with the data returned by the consent screen. Returns null if access was granted,
     * otherwise a message saying why not (also logged under the `SmwcDrive` tag).
     */
    fun handleConsentResult(data: Intent?): String? {
        val error = try {
            val result = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
            if (result.accessToken != null) return null
            "Google returned no access token"
        } catch (e: ApiException) {
            when (e.statusCode) {
                CommonStatusCodes.CANCELED -> "Google Drive access was not granted"
                CommonStatusCodes.DEVELOPER_ERROR ->
                    "Google rejected the app (DEVELOPER_ERROR): check the OAuth client's package name and SHA-1"
                else -> "Google authorization failed: ${CommonStatusCodes.getStatusCodeString(e.statusCode)} (${e.statusCode})"
            }
        }
        Log.w(TAG, "Drive consent failed: $error")
        return error
    }

    private companion object {
        const val TAG = "SmwcDrive"
    }
}

class DriveConsentRequired(val pendingIntent: PendingIntent) : Exception("Google Drive authorization required")
