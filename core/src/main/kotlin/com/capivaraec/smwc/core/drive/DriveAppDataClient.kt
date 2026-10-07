package com.capivaraec.smwc.core.drive

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Reads and writes one JSON file in the app's hidden Google Drive folder (`appDataFolder`),
 * using the Drive v3 REST API with the `drive.appdata` scope.
 *
 * [accessToken] supplies an OAuth token; on Android it comes from Google Identity's
 * AuthorizationClient.
 */
class DriveAppDataClient(
    private val http: HttpClient,
    private val accessToken: suspend () -> String,
    private val fileName: String = "backlog.json",
) {
    /** The file's content, or null if it was never uploaded. */
    suspend fun download(): String? {
        val fileId = findFileId() ?: return null
        return http.get("$FILES_URL/$fileId") {
            auth()
            parameter("alt", "media")
        }.successBody()
    }

    /** Creates or overwrites the file. */
    suspend fun upload(content: String) {
        val fileId = findFileId()
        if (fileId == null) {
            val metadata = buildJsonObject {
                put("name", fileName)
                put("parents", buildJsonArray { add(JsonPrimitive("appDataFolder")) })
            }
            val boundary = "smwc-${System.nanoTime()}"
            val body = buildString {
                append("--$boundary\r\n")
                append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
                append(metadata.toString()).append("\r\n")
                append("--$boundary\r\n")
                append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
                append(content).append("\r\n")
                append("--$boundary--\r\n")
            }
            http.post(UPLOAD_URL) {
                auth()
                parameter("uploadType", "multipart")
                contentType(ContentType.parse("multipart/related; boundary=$boundary"))
                setBody(body)
            }.successBody()
        } else {
            http.patch("$UPLOAD_URL/$fileId") {
                auth()
                parameter("uploadType", "media")
                contentType(ContentType.Application.Json)
                setBody(content)
            }.successBody()
        }
    }

    private suspend fun findFileId(): String? {
        val body = http.get(FILES_URL) {
            auth()
            parameter("spaces", "appDataFolder")
            parameter("q", "name = '$fileName' and trashed = false")
            parameter("fields", "files(id, modifiedTime)")
            parameter("orderBy", "modifiedTime desc")
        }.successBody()
        val files = Json.parseToJsonElement(body).jsonObject["files"] as? JsonArray
        return files?.firstOrNull()?.let { ((it as JsonObject)["id"] as? JsonPrimitive)?.contentOrNull }
    }

    private suspend fun HttpRequestBuilder.auth() = bearerAuth(accessToken())

    private suspend fun HttpResponse.successBody(): String {
        val text = bodyAsText()
        if (!status.isSuccess()) throw DriveException(status.value, text)
        return text
    }

    companion object {
        const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        private const val FILES_URL = "https://www.googleapis.com/drive/v3/files"
        private const val UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
    }
}

class DriveException(val httpStatus: Int, body: String) : Exception("Google Drive returned HTTP $httpStatus: $body")
