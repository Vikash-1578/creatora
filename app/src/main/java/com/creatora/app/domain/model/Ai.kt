package com.creatora.app.domain.model

/** The AI services Creatora can use, in the order it tries them. */
enum class AiProvider(val defaultModel: String) {
    GEMINI("gemini-2.5-flash"),
    GROQ("llama-3.3-70b-versatile"),
    OPENROUTER("meta-llama/llama-3.3-70b-instruct:free"),
}

enum class ChatRole { USER, ASSISTANT }

data class ChatMessage(val role: ChatRole, val text: String)

data class AiRequest(val system: String, val messages: List<ChatMessage>)

/** Why a call to a provider (or to all of them) failed. Never contains the API key. */
sealed interface AiError {
    /** No provider has a key yet. */
    data object NoKey : AiError
    data class InvalidKey(val provider: AiProvider) : AiError
    data class RateLimited(val provider: AiProvider) : AiError
    data class Network(val provider: AiProvider) : AiError
    data class Server(val provider: AiProvider, val code: Int) : AiError
    data class BadRequest(val provider: AiProvider, val message: String) : AiError
    data class BadResponse(val provider: AiProvider) : AiError
    /** The plan's monthly AI limit is used up. */
    data object LimitReached : AiError
    data object SignInRequired : AiError
    /** The cloud AI service could not be reached or is not set up in this build. */
    data object CloudUnavailable : AiError
}

class AiException(val error: AiError) : Exception()

/** The result of an AI call: a value and who produced it, or every error met on the way. */
sealed interface AiOutcome<out T> {
    data class Ok<T>(val value: T, val provider: AiProvider) : AiOutcome<T>
    data class Failed(val errors: List<AiError>) : AiOutcome<Nothing>
}

inline fun <T, R> AiOutcome<T>.map(transform: (T) -> R): AiOutcome<R> = when (this) {
    is AiOutcome.Ok -> AiOutcome.Ok(transform(value), provider)
    is AiOutcome.Failed -> this
}

/** What the settings screen shows for one provider. The key itself is never exposed. */
/** [model] is the name saved by the user, or the provider's default. */
data class AiProviderConfig(val provider: AiProvider, val hasKey: Boolean, val model: String)

data class ScriptDraft(val hook: String, val structure: String, val callToAction: String)

data class PublishText(
    val titles: List<String>,
    val description: String,
    val hashtags: List<String>,
    val chapters: List<String>,
)
