package com.creatora.app.domain.platform

import com.creatora.app.domain.account.AdapterOutcome
import com.creatora.app.domain.account.ChannelPreview
import com.creatora.app.domain.account.ConnectPlatform

/** The settings a platform's developer app gives. [secret] stays out of the repository (local.properties or CI secrets). */
data class PlatformKeys(val appId: String, val secret: String, val configId: String = "")

/** The build setting names for each platform: the app id (or client key) first, then the secret. */
fun settingNames(platform: ConnectPlatform): Pair<String, String>? = when (platform) {
    ConnectPlatform.INSTAGRAM -> "INSTAGRAM_APP_ID" to "INSTAGRAM_APP_SECRET"
    ConnectPlatform.FACEBOOK -> "FACEBOOK_APP_ID" to "FACEBOOK_APP_SECRET"
    ConnectPlatform.TIKTOK -> "TIKTOK_CLIENT_KEY" to "TIKTOK_CLIENT_SECRET"
    else -> null
}

/** The names of the settings that are empty, including the shared redirect page. Empty list means the platform can sign in. */
fun missingSettings(platform: ConnectPlatform, keys: PlatformKeys, redirectUrl: String): List<String> {
    val (idName, secretName) = settingNames(platform) ?: return emptyList()
    return buildList {
        if (keys.appId.isBlank()) add(idName)
        if (keys.secret.isBlank()) add(secretName)
        if (redirectUrl.isBlank()) add("OAUTH_REDIRECT_URL")
    }
}

/** Where the keys come from, and the HTTPS page the platforms send the user back to. Blank values mean "not set up". */
interface PlatformKeyProvider {
    /** The https redirect page (see docs/platform-setup.md); it hands the result to the app. */
    val redirectUrl: String
    fun keys(platform: ConnectPlatform): PlatformKeys

    /** The names of the build settings that are still empty for [platform], so the error can say exactly what to add. */
    fun missing(platform: ConnectPlatform): List<String>
}

/** What signing in to a platform gave. [Failed.reason] is the platform's own words where it gave any. */
sealed interface LoginOutcome {
    data class Signed(val accounts: List<ChannelPreview>) : LoginOutcome
    data class NotConfigured(val missing: List<String>) : LoginOutcome
    data object Cancelled : LoginOutcome
    data class Failed(val reason: String) : LoginOutcome
}

interface PlatformLogin {
    val platform: ConnectPlatform

    /** Shows the platform's own sign-in, then saves the tokens and returns the accounts the user gave access to. */
    suspend fun signIn(): LoginOutcome
}

/** Opens the platform's sign-in page and waits for the redirect. Returns the full redirect URL, or null when the user came back without finishing. */
fun interface OAuthBrowser {
    suspend fun authorize(url: String): String?
}

/** A saved sign-in for one account. [expiresAt] is epoch milliseconds, 0 when the token does not expire. */
data class StoredToken(val access: String, val refresh: String? = null, val expiresAt: Long = 0L, val issuedAt: Long = 0L)

/** Keeps sign-in tokens on this device only, encrypted. Never sent to Creatora's servers. */
interface TokenStore {
    fun get(platform: ConnectPlatform, accountId: String): StoredToken?
    fun put(platform: ConnectPlatform, accountId: String, token: StoredToken)
    fun remove(platform: ConnectPlatform, accountId: String)
}

/** Finds the sign-in for a platform. */
class LoginRegistry(private val logins: List<PlatformLogin>) {
    fun login(platform: ConnectPlatform): PlatformLogin? = logins.firstOrNull { it.platform == platform }
}

// ---------------------------------------------------------------- publishing

/** Where the video comes from. Instagram and Facebook fetch a public https URL; TikTok takes the file itself. */
sealed interface PublishSource {
    data class RemoteUrl(val url: String) : PublishSource
    data class LocalFile(val file: java.io.File) : PublishSource
}

/**
 * One post to one account. [scheduledAtMillis] is only used by platforms that schedule natively (Facebook Pages).
 * [makePublic] is false unless the user chose it; platforms that have not approved Creatora keep the post private anyway.
 */
data class PublishRequest(
    val accountId: String,
    val caption: String,
    val source: PublishSource? = null,
    val scheduledAtMillis: Long? = null,
    val makePublic: Boolean = false,
)

sealed interface PublishStatus {
    data class Processing(val detail: String? = null) : PublishStatus
    data class Live(val url: String?) : PublishStatus
    /** Posted, but the platform keeps it private until its review of Creatora is done. */
    data class PrivateUntilApproved(val detail: String) : PublishStatus
    data class Failed(val reason: String) : PublishStatus
}

/** Posts through a platform's API. The post only goes out when the user taps Publish; nothing here runs on its own. */
interface PlatformPublisher {
    val platform: ConnectPlatform

    /** Starts the post and returns the platform's id for it. */
    suspend fun publish(request: PublishRequest): AdapterOutcome<String>

    suspend fun status(accountId: String, remoteId: String): AdapterOutcome<PublishStatus>
}

class PublisherRegistry(private val publishers: List<PlatformPublisher>) {
    fun publisher(platform: ConnectPlatform): PlatformPublisher? = publishers.firstOrNull { it.platform == platform }
}
