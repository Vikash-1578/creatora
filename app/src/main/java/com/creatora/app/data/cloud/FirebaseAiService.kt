package com.creatora.app.data.cloud

import com.creatora.app.domain.model.AiError
import com.creatora.app.domain.model.AiOutcome
import com.creatora.app.domain.model.AiProvider
import com.creatora.app.domain.model.AiRequest
import com.creatora.app.domain.model.ChatRole
import com.creatora.app.domain.repository.AiService
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/** Gemini through Firebase AI Logic: the API key stays on Google's servers and App Check guards the calls. */
class FirebaseAiService @Inject constructor(private val config: CloudConfig) : AiService {

    override suspend fun complete(request: AiRequest): AiOutcome<String> {
        if (!config.isConfigured) return AiOutcome.Failed(listOf(AiError.CloudUnavailable))
        return try {
            val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
                modelName = MODEL,
                systemInstruction = content { text(request.system) },
            )
            val turns = request.messages.map { message ->
                content(role = if (message.role == ChatRole.USER) "user" else "model") { text(message.text) }
            }
            val reply = model.generateContent(*turns.toTypedArray()).text?.trim().orEmpty()
            if (reply.isEmpty()) AiOutcome.Failed(listOf(AiError.BadResponse(AiProvider.GEMINI))) else AiOutcome.Ok(reply, AiProvider.GEMINI)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AiOutcome.Failed(listOf(e.toAiError()))
        }
    }

    private companion object {
        const val MODEL = "gemini-2.5-flash"
    }
}

/** Matched by class name so this stays correct across Firebase AI Logic versions. */
private fun Exception.toAiError(): AiError = when {
    this is IOException -> AiError.Network(AiProvider.GEMINI)
    this::class.simpleName == "QuotaExceededException" -> AiError.RateLimited(AiProvider.GEMINI)
    this::class.simpleName == "ServerException" -> AiError.Server(AiProvider.GEMINI, 500)
    this::class.simpleName == "RequestTimeoutException" -> AiError.Network(AiProvider.GEMINI)
    else -> AiError.CloudUnavailable
}
