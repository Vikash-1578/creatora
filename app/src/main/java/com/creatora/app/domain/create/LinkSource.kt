package com.creatora.app.domain.create

import com.creatora.app.domain.ai.AiTasks
import com.creatora.app.domain.ai.parseSections
import com.creatora.app.domain.model.AiError
import com.creatora.app.domain.model.AiOutcome
import java.net.URI
import javax.inject.Inject

/** What was read from a pasted link. Only these fields are kept; the page text is never stored. */
data class SourceInfo(val url: String, val title: String, val description: String, val tags: List<String>)

sealed interface LinkResult {
    data class Ok(val info: SourceInfo) : LinkResult
    /** Not an http or https link. */
    data object Invalid : LinkResult
    /** The page could not be fetched or had no title. */
    data object Unreadable : LinkResult
}

interface LinkReader {
    suspend fun read(url: String): LinkResult
}

/** The link as an absolute http or https URL (https is added when missing); null for anything else. */
fun normalizeLink(text: String): String? {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
    val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
    val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
    val ok = (uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrEmpty() && uri.host.contains('.')
    return if (ok) withScheme else null
}

private fun decodeEntities(text: String) = text.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&#x27;", "'")
    .replace("&lt;", "<").replace("&gt;", ">").trim()

private fun metaContent(html: String, attribute: String, name: String): String? {
    val tag = Regex("<meta\\s[^>]*$attribute\\s*=\\s*[\"']$name[\"'][^>]*>", RegexOption.IGNORE_CASE).find(html)?.value ?: return null
    return Regex("content\\s*=\\s*\"([^\"]*)\"|content\\s*=\\s*'([^']*)'", RegexOption.IGNORE_CASE).find(tag)
        ?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }?.let(::decodeEntities)?.takeIf { it.isNotEmpty() }
}

/** Title, description and keywords from the page head (Open Graph first); null when the page has no title. */
fun parsePageMeta(url: String, html: String): SourceInfo? {
    val head = html.take(MAX_HEAD)
    val title = metaContent(head, "property", "og:title")
        ?: Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(head)?.groupValues?.get(1)?.let(::decodeEntities)?.takeIf { it.isNotEmpty() }
        ?: return null
    val description = metaContent(head, "property", "og:description") ?: metaContent(head, "name", "description").orEmpty()
    val tags = metaContent(head, "name", "keywords").orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.take(20)
    return SourceInfo(url, title, description, tags)
}

private const val MAX_HEAD = 200_000

/** The idea of another video in new words. [concept] is what the project keeps; the source's own words are not. */
data class Concept(val concept: String, val angle: String, val hook: String)

sealed interface ConceptOutcome {
    data class Ok(val concept: Concept) : ConceptOutcome
    data class Failed(val errors: List<AiError>) : ConceptOutcome
    /** Still too close to the original after a second try: nothing is used. */
    data object TooClose : ConceptOutcome
}

fun parseConcept(text: String): Concept? {
    val sections = parseSections(text, listOf("CONCEPT", "ANGLE", "HOOK"))
    val concept = sections["CONCEPT"].orEmpty().trim()
    return if (concept.isEmpty()) null else Concept(concept, sections["ANGLE"].orEmpty().trim(), sections["HOOK"].orEmpty().trim())
}

private fun words(text: String) = text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 2 }.toSet()

/** The share of the shorter text's words that also appear in the other, from 0 to 1. */
fun sharedWordFraction(a: String, b: String): Double {
    val wa = words(a)
    val wb = words(b)
    val smaller = minOf(wa.size, wb.size)
    return if (smaller == 0) 0.0 else wa.intersect(wb).size.toDouble() / smaller
}

/** More than this share of words in common with the source title counts as copying. */
const val COPY_LIMIT = 0.6

/** Extracts a concept from [info] and refuses one that is too close to the source: it asks once more, strictly, then gives up. */
class ConceptExtractor @Inject constructor(private val ai: AiTasks) {
    suspend fun extract(info: SourceInfo, topic: String): ConceptOutcome {
        repeat(2) { attempt ->
            when (val outcome = ai.extractConcept(info.title, info.description, info.tags, topic, strict = attempt > 0)) {
                is AiOutcome.Failed -> return ConceptOutcome.Failed(outcome.errors)
                is AiOutcome.Ok -> {
                    val concept = parseConcept(outcome.value) ?: return ConceptOutcome.Failed(emptyList())
                    val tooClose = listOf(concept.concept, concept.hook).any { it.isNotBlank() && sharedWordFraction(it, info.title) > COPY_LIMIT }
                    if (!tooClose) return ConceptOutcome.Ok(concept)
                }
            }
        }
        return ConceptOutcome.TooClose
    }
}
