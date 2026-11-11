package com.creatora.app.data.ai

import com.creatora.app.domain.model.AiError
import com.creatora.app.domain.model.AiException
import com.creatora.app.domain.model.AiProvider
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class HttpResponse(val code: Int, val body: String)

/** Sends one HTTPS request. Throws [IOException] when the network fails. */
interface HttpTransport {
    suspend fun post(url: String, headers: Map<String, String>, body: String): HttpResponse
    suspend fun get(url: String, headers: Map<String, String>): HttpResponse
}

class UrlConnectionTransport @Inject constructor() : HttpTransport {
    override suspend fun post(url: String, headers: Map<String, String>, body: String): HttpResponse = send(url, headers, body)

    override suspend fun get(url: String, headers: Map<String, String>): HttpResponse = send(url, headers, null)

    private suspend fun send(url: String, headers: Map<String, String>, body: String?): HttpResponse =
        withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = if (body == null) "GET" else "POST"
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
                if (body != null) {
                    connection.doOutput = true
                    connection.outputStream.use { it.write(body.toByteArray()) }
                }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                HttpResponse(code, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
            } finally {
                connection.disconnect()
            }
        }

    private companion object {
        const val TIMEOUT_MS = 30_000
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
