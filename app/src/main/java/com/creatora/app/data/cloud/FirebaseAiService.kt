package com.creatora.app.data.cloud

import com.creatora.app.domain.model.AiError
import com.creatora.app.domain.model.AiOutcome
import com.creatora.app.domain.model.AiProvider
import com.creatora.app.domain.model.AiRequest
import com.creatora.app.domain.model.ChatRole
import com.creatora.app.domain.repository.AiService
import com.creatora.app.domain.repository.ErrorReporter
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** Gemini through Firebase AI Logic: the API key stays on Google's servers and App Check guards the calls. */
class FirebaseAiService @Inject constructor(private val config: CloudConfig, private val errors: ErrorReporter) : AiService {

    override suspend fun complete(request: AiRequest): AiOutcome<String> {
        if (!config.isConfigured) return AiOutcome.Failed(listOf(AiError.CloudUnavailable))
        return try {
            // Server hiccups (a 500 from the AI backend) are tried again twice with a short wait before the user sees anything.
            retryTransient { generate(request) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The real cause (class and message) goes to Crashlytics so "error 500" can be traced to its reason.
            errors.record(e)
            AiOutcome.Failed(listOf(e.toAiError()))
        }
    }

    private suspend fun generate(request: AiRequest): AiOutcome<String> {
        val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = MODEL,
            systemInstruction = content { text(request.system) },
        )
        val turns = request.messages.map { message ->
            content(role = if (message.role == ChatRole.USER) "user" else "model") { text(message.text) }
        }
        val reply = model.generateContent(*turns.toTypedArray()).text?.trim().orEmpty()
        return if (reply.isEmpty()) AiOutcome.Failed(listOf(AiError.BadResponse(AiProvider.GEMINI))) else AiOutcome.Ok(reply, AiProvider.GEMINI)
    }

    private companion object {
        const val MODEL = "gemini-2.5-flash"
    }
}

/** Matched by class name so this stays correct across Firebase AI Logic versions. Server-side trouble reads as "try again in a moment". */
private fun Exception.toAiError(): AiError = when {
    this is IOException -> AiError.Network(AiProvider.GEMINI)
    this::class.simpleName == "QuotaExceededException" -> AiError.RateLimited(AiProvider.GEMINI)
    this::class.simpleName == "RequestTimeoutException" -> AiError.Network(AiProvider.GEMINI)
    else -> AiError.CloudUnavailable
}

private val TRANSIENT = setOf("ServerException", "RequestTimeoutException", "UnknownException")

/** Runs [block]; a transient failure (server error, timeout, no connection) is retried after [delaysMillis] each, then thrown. */
internal suspend fun <T> retryTransient(delaysMillis: List<Long> = listOf(1_000L, 3_000L), block: suspend () -> T): T {
    var attempt = 0
    while (true) {
        try {
            return block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val transient = e is IOException || e::class.simpleName in TRANSIENT
            if (!transient || attempt >= delaysMillis.size) throw e
            delay(delaysMillis[attempt++])
        }
    }
}
