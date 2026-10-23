package com.example.cli

import kotlinx.serialization.json.*
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID

/**
 * What the server answered: the status, the parsed JSON body (null when it had none) and, for a page of a list, the size
 * of the whole list from the X-Total-Count header.
 */
class ApiResponse(val status: Int, val body: JsonElement?, val total: Int? = null) {
    val ok get() = status in 200..299

    /** The `message` of the server's error body `{"error": "...", "message": "..."}`, null when there is none. */
    val errorText: String? get() = (body as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
}

/** The only door to the server. Menus use this interface, so tests can swap in a fake. */
interface Api {
    /** The bearer token that is sent with every request while it is set. */
    var token: String?

    /** Throws [ServerUnreachable] when there is no answer at all; every HTTP status is returned as a response. */
    fun call(method: String, path: String, query: Map<String, String?> = emptyMap(), body: JsonElement? = null): ApiResponse

    /** Posts one file as the multipart part `file`. Same rules as [call]. */
    fun upload(path: String, fileName: String, contentType: String, bytes: ByteArray): ApiResponse
}

class ServerUnreachable(message: String) : Exception(message)

class HttpApi(private val baseUrl: String) : Api {
    override var token: String? = null
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    override fun call(method: String, path: String, query: Map<String, String?>, body: JsonElement?): ApiResponse {
        val queryString = query.filterValues { it != null }.entries
            .joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, Charsets.UTF_8)}" }
        val request = try {
            HttpRequest.newBuilder(URI.create(baseUrl.trimEnd('/') + path + if (queryString.isEmpty()) "" else "?$queryString")).timeout(Duration.ofSeconds(30))
            .method(method, if (body == null) HttpRequest.BodyPublishers.noBody() else HttpRequest.BodyPublishers.ofString(body.toString()))
            .apply {
                if (body != null) header("Content-Type", "application/json")
                token?.let { header("Authorization", "Bearer $it") }
            }
            .build()
        } catch (e: IllegalArgumentException) {
            throw ServerUnreachable("Invalid server address or input: ${e.message}")
        }
        return send(request)
    }

    override fun upload(path: String, fileName: String, contentType: String, bytes: ByteArray): ApiResponse {
        val boundary = "----rentacar" + UUID.randomUUID().toString().replace("-", "")
        val head = "--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"$fileName\"\r\nContent-Type: $contentType\r\n\r\n"
        val body = head.toByteArray() + bytes + "\r\n--$boundary--\r\n".toByteArray()
        val request = try {
            HttpRequest.newBuilder(URI.create(baseUrl.trimEnd('/') + path)).timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .header("Content-Type", "multipart/form-data; boundary=$boundary")
                .apply { token?.let { header("Authorization", "Bearer $it") } }
                .build()
        } catch (e: IllegalArgumentException) {
            throw ServerUnreachable("Invalid server address or input: ${e.message}")
        }
        return send(request)
    }

    private fun send(request: HttpRequest): ApiResponse {
        try {
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            val text = response.body()
            // Error bodies are not always JSON; a body that does not parse is treated as no body.
            val json = if (text.isBlank()) null else runCatching { Json.parseToJsonElement(text) }.getOrNull()
            return ApiResponse(response.statusCode(), json, response.headers().firstValue("X-Total-Count").orElse(null)?.toIntOrNull())
        } catch (e: IOException) {
            throw ServerUnreachable("Cannot reach the server at $baseUrl (${e.message ?: e::class.simpleName}). Is it running?")
        }
    }
}

/**
 * What to tell the user about a failed call: the action's hint for the status code, else the server's own message
 * (every error has one), else one friendly sentence per status code for answers that are not from our server.
 */
fun errorMessage(status: Int, hints: Map<Int, String> = emptyMap(), serverMessage: String? = null): String = hints[status] ?: serverMessage ?: when (status) {
    400 -> "The server did not accept that input. Check the values and try again."
    401 -> "You are not logged in, or your login has expired. Log in again."
    403 -> "You are not allowed to do that."
    404 -> "Not found. Check the id."
    409 -> "That is not possible in the current state (already taken, already done, or not allowed anymore)."
    413 -> "That is too large."
    429 -> "Too many failed login attempts for that email. Wait a few minutes and try again."
    in 500..599 -> "The server had a problem (HTTP $status). Try again later."
    else -> "Unexpected answer from the server (HTTP $status)."
}
