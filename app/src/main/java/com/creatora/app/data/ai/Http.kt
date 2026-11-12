package com.creatora.app.data.ai

import com.creatora.app.domain.model.AiError
import com.creatora.app.domain.model.AiException
import com.creatora.app.domain.model.AiProvider
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [headers] are the response headers with lower-case names (empty where a fake does not set them). */
data class HttpResponse(val code: Int, val body: String, val headers: Map<String, String> = emptyMap())

/** Sends one HTTPS request. Throws [IOException] when the network fails. */
interface HttpTransport {
    suspend fun post(url: String, headers: Map<String, String>, body: String): HttpResponse
    suspend fun get(url: String, headers: Map<String, String>): HttpResponse
    suspend fun put(url: String, headers: Map<String, String>, body: ByteArray): HttpResponse
<<<<<<< HEAD

    /** POST with a binary body (an image upload). */
    suspend fun postBytes(url: String, headers: Map<String, String>, body: ByteArray): HttpResponse

    /** PUT of a whole file, streamed so a large video is never held in memory. */
    suspend fun putFile(url: String, headers: Map<String, String>, file: File): HttpResponse
=======
>>>>>>> origin/main
}

class UrlConnectionTransport @Inject constructor() : HttpTransport {

<<<<<<< HEAD
    private suspend fun execute(
        url: String,
        method: String,
        headers: Map<String, String>,
        readTimeout: Int = TIMEOUT_MS,
        length: Long? = null,
        write: ((OutputStream) -> Unit)? = null,
    ): HttpResponse = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = readTimeout
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            if (write != null) {
                connection.doOutput = true
                if (length != null) connection.setFixedLengthStreamingMode(length)
                connection.outputStream.use(write)
=======
    override suspend fun put(url: String, headers: Map<String, String>, body: ByteArray): HttpResponse =
        withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "PUT"
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = UPLOAD_TIMEOUT_MS
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(body.size)
                headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
                connection.outputStream.use { it.write(body) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                HttpResponse(code, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
            } finally {
                connection.disconnect()
            }
        }

    override suspend fun get(url: String, headers: Map<String, String>): HttpResponse =
        withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                HttpResponse(code, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
            } finally {
                connection.disconnect()
>>>>>>> origin/main
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val responseHeaders = connection.headerFields.entries.mapNotNull { (k, v) -> k?.let { it.lowercase() to v.firstOrNull().orEmpty() } }.toMap()
            HttpResponse(code, stream?.bufferedReader()?.use { it.readText() }.orEmpty(), responseHeaders)
        } finally {
            connection.disconnect()
        }
    }

    override suspend fun post(url: String, headers: Map<String, String>, body: String): HttpResponse =
        execute(url, "POST", headers) { it.write(body.toByteArray()) }

    override suspend fun get(url: String, headers: Map<String, String>): HttpResponse = execute(url, "GET", headers)

    override suspend fun put(url: String, headers: Map<String, String>, body: ByteArray): HttpResponse =
        execute(url, "PUT", headers, UPLOAD_TIMEOUT_MS, body.size.toLong()) { it.write(body) }

    override suspend fun postBytes(url: String, headers: Map<String, String>, body: ByteArray): HttpResponse =
        execute(url, "POST", headers, UPLOAD_TIMEOUT_MS, body.size.toLong()) { it.write(body) }

    override suspend fun putFile(url: String, headers: Map<String, String>, file: File): HttpResponse =
        execute(url, "PUT", headers, UPLOAD_TIMEOUT_MS, file.length()) { out -> file.inputStream().use { it.copyTo(out) } }

    private companion object {
        const val TIMEOUT_MS = 30_000
        const val UPLOAD_TIMEOUT_MS = 5 * 60_000
    }
}

/** Posts JSON and returns the body of a successful reply, or throws an [AiException] for [provider]. */
suspend fun HttpTransport.postJson(provider: AiProvider, url: String, headers: Map<String, String>, body: String): String {
    val response = try {
        post(url, headers + ("Content-Type" to "application/json"), body)
    } catch (e: IOException) {
        throw AiException(AiError.Network(provider))
    }
    val code = response.code
    throw AiException(
        when {
            code in 200..299 -> return response.body
            code == 401 || code == 403 -> AiError.InvalidKey(provider)
            code == 429 -> AiError.RateLimited(provider)
            code == 408 || code >= 500 -> AiError.Server(provider, code)
            else -> AiError.BadRequest(provider, response.body.take(ERROR_TEXT_LIMIT))
        },
    )
}

private const val ERROR_TEXT_LIMIT = 200
