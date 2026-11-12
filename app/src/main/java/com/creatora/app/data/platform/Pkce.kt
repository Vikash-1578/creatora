package com.creatora.app.data.platform

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Sign-in helpers: the random `state` that proves a redirect belongs to our request, and PKCE for TikTok. */
object Pkce {
    private val random = SecureRandom()
    private val url = Base64.getUrlEncoder().withoutPadding()

    fun randomToken(bytes: Int = 32): String = ByteArray(bytes).also(random::nextBytes).let(url::encodeToString)

    /** S256 challenge for [verifier], as TikTok and the OAuth PKCE standard define it. */
    fun challenge(verifier: String): String = url.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
}

/** The query values of a redirect URL, decoded. */
fun redirectParams(redirect: String): Map<String, String> {
    val query = redirect.substringAfter('?', "").substringBefore('#')
    if (query.isEmpty()) return emptyMap()
    return query.split('&').mapNotNull { pair ->
        val name = pair.substringBefore('=').takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        name to java.net.URLDecoder.decode(pair.substringAfter('=', ""), "UTF-8")
    }.toMap()
}

fun formEncode(values: Map<String, String>): String =
    values.entries.joinToString("&") { (k, v) -> "${java.net.URLEncoder.encode(k, "UTF-8")}=${java.net.URLEncoder.encode(v, "UTF-8")}" }
