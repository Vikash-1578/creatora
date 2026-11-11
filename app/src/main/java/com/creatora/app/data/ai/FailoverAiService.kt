package com.creatora.app.data.ai

import com.creatora.app.domain.model.AiError
import com.creatora.app.domain.model.AiException
import com.creatora.app.domain.model.AiOutcome
import com.creatora.app.domain.model.AiRequest
import com.creatora.app.domain.repository.AiClient
import com.creatora.app.domain.repository.AiProviderStore
import com.creatora.app.domain.repository.AiService
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** Tries each provider that has a key, in order, and moves on to the next when one fails. */
class FailoverAiService(
    private val clients: List<AiClient>,
    private val store: AiProviderStore,
) : AiService {

    @Inject
    constructor(gemini: GeminiClient, groq: GroqClient, openRouter: OpenRouterClient, store: AiProviderStore) :
        this(listOf(gemini, groq, openRouter), store)

    override suspend fun complete(request: AiRequest): AiOutcome<String> {
        val models = store.configs.first().associate { it.provider to it.model }
        val errors = mutableListOf<AiError>()
        for (client in clients) {
            val key = store.key(client.provider) ?: continue
            try {
                return AiOutcome.Ok(client.complete(request, key, models.getValue(client.provider)), client.provider)
            } catch (e: AiException) {
                errors += e.error
            }
        }
        return AiOutcome.Failed(errors.ifEmpty { listOf(AiError.NoKey) })
    }
}
