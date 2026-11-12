package com.creatora.app.data.create

import com.creatora.app.data.ai.HttpTransport
import com.creatora.app.domain.create.LinkReader
import com.creatora.app.domain.create.LinkResult
import com.creatora.app.domain.create.normalizeLink
import com.creatora.app.domain.create.parsePageMeta
import java.io.IOException
import javax.inject.Inject

/**
 * Reads the title, description and keywords from the head of a page with one plain GET (no login, no JavaScript). YouTube
 * links work the same way through their public page tags, so no API quota is used and no transcript is read.
 */
class HttpLinkReader @Inject constructor(private val http: HttpTransport) : LinkReader {
    override suspend fun read(url: String): LinkResult {
        val link = normalizeLink(url) ?: return LinkResult.Invalid
        val response = try {
            http.get(link, mapOf("User-Agent" to USER_AGENT, "Accept" to "text/html", "Accept-Language" to "en"))
        } catch (e: IOException) {
            return LinkResult.Unreadable
        }
        if (response.code !in 200..299) return LinkResult.Unreadable
        return parsePageMeta(link, response.body)?.let { LinkResult.Ok(it) } ?: LinkResult.Unreadable
    }

    private companion object {
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) Creatora/1.0"
    }
}
