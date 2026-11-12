package com.creatora.app.data.platform

import com.creatora.app.domain.platform.OAuthBrowser

sealed interface AuthResult {
    data class Code(val code: String) : AuthResult
    /** The user closed the browser or pressed Cancel on the platform's page. */
    data object Cancelled : AuthResult
    /** The platform sent the user back with an error, in its own words. */
    data class Denied(val reason: String) : AuthResult
}

/** Builds the sign-in URL with a fresh `state`, opens it and reads the redirect: the code, or why there is none. */
class OAuthFlow(private val browser: OAuthBrowser) {
    suspend fun authorize(buildUrl: (state: String) -> String): AuthResult {
        val state = Pkce.randomToken(16)
        val redirect = browser.authorize(buildUrl(state)) ?: return AuthResult.Cancelled
        val params = redirectParams(redirect)
        params["error"]?.let { error ->
            val reason = params["error_description"] ?: params["error_reason"] ?: error
            return if (error == "access_denied" || error == "user_cancelled") AuthResult.Cancelled else AuthResult.Denied(reason)
        }
        if (params["state"] != state) return AuthResult.Denied("The answer did not match this sign-in request. Please try again.")
        // Instagram appends "#_" to the code.
        val code = params["code"]?.removeSuffix("#_")?.takeIf { it.isNotBlank() } ?: return AuthResult.Denied("The platform sent no sign-in code.")
        return AuthResult.Code(code)
    }
}
