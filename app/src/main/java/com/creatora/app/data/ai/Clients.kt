package com.creatora.app.data.ai

import com.creatora.app.domain.model.AiError
import com.creatora.app.domain.model.AiException
import com.creatora.app.domain.model.AiProvider
import com.creatora.app.domain.model.AiRequest
import com.creatora.app.domain.model.ChatRole
import com.creatora.app.domain.repository.AiClient
import javax.inject.Inject
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Google Gemini (generateContent). The key goes in a header, never in the URL. */
class GeminiClient @Inject constructor(private val http: HttpTransport) : AiClient {
    override val provider = AiProvider.GEMINI

    override suspend fun complete(request: AiRequest, apiKey: String, model: String): String {
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", request.system))))
            .put(
                "contents",
                JSONArray(
                    request.messages.map {
                        JSONObject()
                            .put("role", if (it.role == ChatRole.USER) "user" else "model")
                            .put("parts", JSONArray().put(JSONObject().put("text", it.text)))
                    },
                ),
            )
        val reply = http.postJson(
            provider,
            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent",
            mapOf("x-goog-api-key" to apiKey),
            body.toString(),
        )
        return readText(provider) {
            val parts = JSONObject(reply).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts")
            (0 until parts.length()).joinToString("") { parts.getJSONObject(it).optString("text") }
        }
    }
}

/** Services that speak the OpenAI chat-completions format (Groq, OpenRouter). */
open class OpenAiCompatibleClient(
    private val http: HttpTransport,
    override val provider: AiProvider,
    private val url: String,
) : AiClient {
    override suspend fun complete(request: AiRequest, apiKey: String, model: String): String {
        val messages = JSONArray().put(JSONObject().put("role", "system").put("content", request.system))
        request.messages.forEach {
            messages.put(JSONObject().put("role", if (it.role == ChatRole.USER) "user" else "assistant").put("content", it.text))
        }
        val reply = http.postJson(
            provider,
            url,
            mapOf("Authorization" to "Bearer $apiKey"),
            JSONObject().put("model", model).put("messages", messages).toString(),
        )
        return readText(provider) { JSONObject(reply).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content") }
    }
}

class GroqClient @Inject constructor(http: HttpTransport) : OpenAiCompatibleClient(
    http, AiProvider.GROQ, "https://api.groq.com/openai/v1/chat/completions",
)

class OpenRouterClient @Inject constructor(http: HttpTransport) : OpenAiCompatibleClient(
    http, AiProvider.OPENROUTER, "https://openrouter.ai/api/v1/chat/completions",
)

/** Reads the reply text; an empty or unexpected reply becomes [AiError.BadResponse]. */
private fun readText(provider: AiProvider, read: () -> String): String {
    val text = try {
        read()
    } catch (e: JSONException) {
        throw AiException(AiError.BadResponse(provider))
    }
    return text.trim().ifEmpty { throw AiException(AiError.BadResponse(provider)) }
}
