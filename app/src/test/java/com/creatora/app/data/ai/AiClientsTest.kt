package com.creatora.app.data.ai

import android.app.Application
import com.creatora.app.domain.model.AiError
import com.creatora.app.domain.model.AiException
import com.creatora.app.domain.model.AiProvider
import com.creatora.app.domain.model.AiRequest
import com.creatora.app.domain.model.ChatMessage
import com.creatora.app.domain.model.ChatRole
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Remembers the last request and answers with [response], or fails like a lost connection. */
class FakeTransport(var response: HttpResponse = HttpResponse(200, ""), var offline: Boolean = false) : HttpTransport {
    var url = ""
    var headers = emptyMap<String, String>()
    var body = ""

    override suspend fun post(url: String, headers: Map<String, String>, body: String): HttpResponse {
        if (offline) throw IOException("offline")
        this.url = url
        this.headers = headers
        this.body = body
        return response
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AiClientsTest {
    private val request = AiRequest(
        system = "Be brief",
        messages = listOf(ChatMessage(ChatRole.USER, "Hi"), ChatMessage(ChatRole.ASSISTANT, "Hello"), ChatMessage(ChatRole.USER, "Tip?")),
    )

    private suspend fun errorOf(block: suspend () -> Unit): AiError {
        try {
            block()
        } catch (e: AiException) {
            return e.error
        }
        fail("expected an AiException")
        throw IllegalStateException()
    }

    @Test
    fun gemini_sendsTheKeyInAHeaderAndReadsTheReply() = runTest {
        val transport = FakeTransport(HttpResponse(200, """{"candidates":[{"content":{"parts":[{"text":"Post "},{"text":"weekly."}]}}]}"""))

        val text = GeminiClient(transport).complete(request, "SECRET", "gemini-test")

        assertEquals("Post weekly.", text)
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-test:generateContent", transport.url)
        assertEquals("SECRET", transport.headers["x-goog-api-key"])
        assertTrue("SECRET" !in transport.url)
        val body = JSONObject(transport.body)
        assertEquals("Be brief", body.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text"))
        val roles = body.getJSONArray("contents").let { c -> (0 until c.length()).map { c.getJSONObject(it).getString("role") } }
        assertEquals(listOf("user", "model", "user"), roles)
    }

    @Test
    fun groqAndOpenRouter_useTheOpenAiFormatWithABearerKey() = runTest {
        val reply = """{"choices":[{"message":{"content":" Hello there "}}]}"""
        for ((client, url) in listOf(
            GroqClient(FakeTransport(HttpResponse(200, reply))) to "https://api.groq.com/openai/v1/chat/completions",
            OpenRouterClient(FakeTransport(HttpResponse(200, reply))) to "https://openrouter.ai/api/v1/chat/completions",
        )) {
            assertEquals("Hello there", client.complete(request, "SECRET", "some-model"))
            assertTrue(url.startsWith("https://"))
        }
        val transport = FakeTransport(HttpResponse(200, reply))
        GroqClient(transport).complete(request, "SECRET", "some-model")
        assertEquals("https://api.groq.com/openai/v1/chat/completions", transport.url)
        assertEquals("Bearer SECRET", transport.headers["Authorization"])
        assertEquals("application/json", transport.headers["Content-Type"])
        val body = JSONObject(transport.body)
        assertEquals("some-model", body.getString("model"))
        val messages = body.getJSONArray("messages")
        assertEquals("system", messages.getJSONObject(0).getString("role"))
        assertEquals("assistant", messages.getJSONObject(2).getString("role"))
        assertEquals(4, messages.length())
    }

    @Test
    fun httpStatusesBecomeTypedErrors() = runTest {
        val cases = mapOf(
            401 to AiError.InvalidKey(AiProvider.GROQ),
            403 to AiError.InvalidKey(AiProvider.GROQ),
            429 to AiError.RateLimited(AiProvider.GROQ),
            500 to AiError.Server(AiProvider.GROQ, 500),
            503 to AiError.Server(AiProvider.GROQ, 503),
            400 to AiError.BadRequest(AiProvider.GROQ, "bad model"),
        )
        for ((code, expected) in cases) {
            val client = GroqClient(FakeTransport(HttpResponse(code, "bad model")))
            assertEquals("status $code", expected, errorOf { client.complete(request, "k", "m") })
        }
    }

    @Test
    fun aLostConnectionIsANetworkError() = runTest {
        val client = GeminiClient(FakeTransport(offline = true))
        assertEquals(AiError.Network(AiProvider.GEMINI), errorOf { client.complete(request, "k", "m") })
    }

    @Test
    fun unreadableOrEmptyRepliesAreBadResponses() = runTest {
        for (body in listOf("not json", "{}", """{"choices":[{"message":{"content":"  "}}]}""")) {
            val client = GroqClient(FakeTransport(HttpResponse(200, body)))
            assertEquals(AiError.BadResponse(AiProvider.GROQ), errorOf { client.complete(request, "k", "m") })
        }
    }

    @Test
    fun errorTextIsCappedAndNeverContainsTheKey() = runTest {
        val client = GroqClient(FakeTransport(HttpResponse(400, "x".repeat(1000))))
        val error = errorOf { client.complete(request, "SECRET", "m") } as AiError.BadRequest
        assertEquals(200, error.message.length)
        assertTrue("SECRET" !in error.toString())
    }
}
