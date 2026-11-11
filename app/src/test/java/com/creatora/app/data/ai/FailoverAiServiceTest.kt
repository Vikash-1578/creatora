package com.creatora.app.data.ai

import com.creatora.app.domain.model.AiError
import com.creatora.app.domain.model.AiException
import com.creatora.app.domain.model.AiOutcome
import com.creatora.app.domain.model.AiProvider
import com.creatora.app.domain.model.AiRequest
import com.creatora.app.domain.repository.AiClient
import com.creatora.app.testutil.FakeAiProviderStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private class StubClient(override val provider: AiProvider, private val result: Result<String>) : AiClient {
    val calls = mutableListOf<Pair<String, String>>()
    override suspend fun complete(request: AiRequest, apiKey: String, model: String): String {
        calls += apiKey to model
        return result.getOrElse { throw it }
    }
}

class FailoverAiServiceTest {
    private val store = FakeAiProviderStore()
    private val request = AiRequest("s", emptyList())

    private fun ok(provider: AiProvider, text: String) = StubClient(provider, Result.success(text))
    private fun failing(provider: AiProvider, error: AiError) = StubClient(provider, Result.failure(AiException(error)))

    @Test
    fun withoutAnyKey_reportsNoKeyAndCallsNobody() = runTest {
        val gemini = ok(AiProvider.GEMINI, "x")

        val outcome = FailoverAiService(listOf(gemini), store).complete(request)

        assertEquals(AiOutcome.Failed(listOf(AiError.NoKey)), outcome)
        assertEquals(0, gemini.calls.size)
    }

    @Test
    fun usesTheFirstProviderThatHasAKey() = runTest {
        store.setKey(AiProvider.GROQ, "groq-key")
        val gemini = ok(AiProvider.GEMINI, "from gemini")
        val groq = ok(AiProvider.GROQ, "from groq")

        val outcome = FailoverAiService(listOf(gemini, groq), store).complete(request)

        assertEquals(AiOutcome.Ok("from groq", AiProvider.GROQ), outcome)
        assertEquals(0, gemini.calls.size)
        assertEquals(listOf("groq-key" to AiProvider.GROQ.defaultModel), groq.calls)
    }

    @Test
    fun movesOnToTheNextProviderWhenOneFails() = runTest {
        store.setKey(AiProvider.GEMINI, "g")
        store.setKey(AiProvider.GROQ, "q")
        val gemini = failing(AiProvider.GEMINI, AiError.RateLimited(AiProvider.GEMINI))
        val groq = ok(AiProvider.GROQ, "answer")

        val outcome = FailoverAiService(listOf(gemini, groq), store).complete(request)

        assertEquals(AiOutcome.Ok("answer", AiProvider.GROQ), outcome)
        assertEquals(1, gemini.calls.size)
    }

    @Test
    fun whenEveryProviderFails_reportsEachError() = runTest {
        store.setKey(AiProvider.GEMINI, "g")
        store.setKey(AiProvider.OPENROUTER, "o")
        val clients = listOf(
            failing(AiProvider.GEMINI, AiError.InvalidKey(AiProvider.GEMINI)),
            failing(AiProvider.GROQ, AiError.Network(AiProvider.GROQ)), // has no key, so it is skipped
            failing(AiProvider.OPENROUTER, AiError.Server(AiProvider.OPENROUTER, 500)),
        )

        val outcome = FailoverAiService(clients, store).complete(request)

        assertEquals(
            AiOutcome.Failed(listOf(AiError.InvalidKey(AiProvider.GEMINI), AiError.Server(AiProvider.OPENROUTER, 500))),
            outcome,
        )
    }

    @Test
    fun usesTheModelTheUserSaved() = runTest {
        store.setKey(AiProvider.GEMINI, "g")
        store.setModel(AiProvider.GEMINI, "my-model")
        val gemini = ok(AiProvider.GEMINI, "x")

        FailoverAiService(listOf(gemini), store).complete(request)

        assertEquals("my-model", gemini.calls.single().second)
    }
}
