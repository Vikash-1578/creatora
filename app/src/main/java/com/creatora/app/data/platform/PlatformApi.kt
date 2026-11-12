package com.creatora.app.data.platform

<<<<<<< HEAD
import com.creatora.app.data.ai.HttpResponse
=======
>>>>>>> origin/main
import com.creatora.app.data.ai.HttpTransport
import com.creatora.app.domain.account.AdapterError
import com.creatora.app.domain.account.AdapterOutcome
import com.creatora.app.domain.repository.ErrorReporter
import java.io.IOException
import org.json.JSONObject

/** A non-success answer from a platform, kept as an exception so Crashlytics shows the real status and reason. */
class PlatformApiException(val platform: String, val code: Int, val path: String, body: String) :
    Exception("$platform API $code at $path: ${body.take(400)}")

/**
 * Sends one request to a platform's API and turns the answer into an [AdapterOutcome]: a readable reply, or an
 * [AdapterError] built from the platform's own error. Every non-success answer is also reported (without the token,
 * which sits in the query string and is cut off with it).
 */
class PlatformApi(private val http: HttpTransport, private val errors: ErrorReporter, private val platformName: String) {

    suspend fun <T> get(url: String, headers: Map<String, String> = emptyMap(), parse: (String) -> T): AdapterOutcome<T> =
        call(url) { http.get(url, headers + ("Accept" to "application/json")) }.then(parse)

    suspend fun <T> post(url: String, body: String, headers: Map<String, String> = emptyMap(), parse: (String) -> T): AdapterOutcome<T> =
        call(url) { http.post(url, headers + ("Accept" to "application/json"), body) }.then(parse)

    suspend fun <T> put(url: String, body: ByteArray, headers: Map<String, String>, parse: (String) -> T): AdapterOutcome<T> =
        call(url) { http.put(url, headers, body) }.then(parse)

<<<<<<< HEAD
    /** POST that also gives the response headers (a resumable upload hands out its upload address in `Location`). */
    suspend fun <T> postRaw(url: String, body: String, headers: Map<String, String> = emptyMap(), parse: (HttpResponse) -> T): AdapterOutcome<T> =
        callRaw(url) { http.post(url, headers + ("Accept" to "application/json"), body) }.thenRaw(parse)

    suspend fun <T> postBytes(url: String, body: ByteArray, headers: Map<String, String>, parse: (String) -> T): AdapterOutcome<T> =
        call(url) { http.postBytes(url, headers, body) }.then(parse)

    suspend fun <T> putFile(url: String, file: java.io.File, headers: Map<String, String>, parse: (String) -> T): AdapterOutcome<T> =
        call(url) { http.putFile(url, headers, file) }.then(parse)

    private suspend fun callRaw(url: String, send: suspend () -> HttpResponse): AdapterOutcome<HttpResponse> {
=======
    private suspend fun call(url: String, send: suspend () -> com.creatora.app.data.ai.HttpResponse): AdapterOutcome<String> {
>>>>>>> origin/main
        val response = try {
            send()
        } catch (e: IOException) {
            return AdapterOutcome.Failed(AdapterError.Network)
        }
<<<<<<< HEAD
        if (response.code in 200..299) return AdapterOutcome.Ok(response)
=======
        if (response.code in 200..299) return AdapterOutcome.Ok(response.body)
>>>>>>> origin/main
        errors.record(PlatformApiException(platformName, response.code, url.substringBefore('?').removePrefix("https://"), response.body))
        return AdapterOutcome.Failed(platformError(response.code, response.body))
    }

<<<<<<< HEAD
    private suspend fun call(url: String, send: suspend () -> HttpResponse): AdapterOutcome<String> =
        when (val r = callRaw(url, send)) {
            is AdapterOutcome.Ok -> AdapterOutcome.Ok(r.value.body)
            is AdapterOutcome.Failed -> r
        }

    private fun <T> AdapterOutcome<HttpResponse>.thenRaw(parse: (HttpResponse) -> T): AdapterOutcome<T> = when (this) {
        is AdapterOutcome.Ok -> runCatching { AdapterOutcome.Ok(parse(value)) }
            .getOrElse { AdapterOutcome.Failed(AdapterError.NotAvailable("The reply from $platformName could not be read")) }
        is AdapterOutcome.Failed -> this
    }

=======
>>>>>>> origin/main
    private fun <T> AdapterOutcome<String>.then(parse: (String) -> T): AdapterOutcome<T> = when (this) {
        is AdapterOutcome.Ok -> runCatching { AdapterOutcome.Ok(parse(value)) }
            .getOrElse { AdapterOutcome.Failed(AdapterError.NotAvailable("The reply from $platformName could not be read")) }
        is AdapterOutcome.Failed -> this
    }
}

/**
 * Maps an error answer to an [AdapterError]. Meta replies `{"error":{"message","code"}}` with a numeric code (190: token
 * expired or revoked, 10 and 200-299: a permission is missing, 4, 17, 32, 613: rate limit); TikTok replies
 * `{"error":{"code":"access_token_invalid","message"}}` with a text code.
 */
fun platformError(status: Int, body: String): AdapterError {
    val root = runCatching { JSONObject(body) }.getOrNull()
    val error = root?.optJSONObject("error")
    // Meta nests the message under "error"; Instagram's token endpoint and OAuth errors put it at the top.
    val message = listOf(error?.optString("message"), root?.optString("error_message"), root?.optString("error_description"))
        .firstOrNull { !it.isNullOrBlank() }
    val number = error?.optInt("code", -1) ?: -1
    val text = error?.optString("code").orEmpty()
    return when {
        status == 401 || number == 190 || text == "access_token_invalid" || text == "access_token_expired" -> AdapterError.NotAuthorized
        number == 10 || number in 200..299 || text == "scope_not_authorized" || text == "scope_permission_missed" -> AdapterError.PermissionMissing
        status == 429 || number in setOf(4, 17, 32, 613, 80002, 80006) || text == "rate_limit_exceeded" -> AdapterError.QuotaExceeded
        status == 404 -> AdapterError.NotFound
        else -> AdapterError.NotAvailable(message ?: "HTTP $status")
    }
}

/** The reason in words, for sign-in errors that are shown with the platform's name. */
fun AdapterError.reason(): String = when (this) {
    AdapterError.NotAuthorized -> "The platform did not accept the sign-in."
    AdapterError.PermissionMissing -> "A permission was not granted."
    AdapterError.Network -> "No connection."
    AdapterError.QuotaExceeded -> "The platform's request limit was reached."
    AdapterError.NotFound -> "The platform could not find that account."
    is AdapterError.NotAvailable -> reason
}
