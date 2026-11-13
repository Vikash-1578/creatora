package com.creatora.app.domain.publish

import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.model.VideoFormat

enum class Aspect { PORTRAIT, LANDSCAPE, SQUARE }

/**
 * What a platform accepts for one post. These numbers come from the platforms' public documentation and they change;
 * [checkedOn] says how sure we are. A limit is a warning with the platform's name on it, never a silent cut.
 */
data class PlatformSpec(
    val platform: ConnectPlatform,
    /** Preferred first. */
    val aspects: List<Aspect>,
    val maxSeconds: Int,
    /** Null when the platform has no separate title. */
    val titleMax: Int?,
    /** The caption, or the description on YouTube. */
    val captionMax: Int,
    val hashtagsMax: Int,
    /** YouTube only: the tags field holds this many characters. */
    val tagsMaxChars: Int? = null,
    val checkedOn: String = UNVERIFIED,
)

const val UNVERIFIED = "not re-checked against the platform's current documentation"

object PlatformSpecs {
    private val all = listOf(
        PlatformSpec(ConnectPlatform.YOUTUBE, listOf(Aspect.LANDSCAPE, Aspect.PORTRAIT), maxSeconds = 12 * 3600, titleMax = 100, captionMax = 5000, hashtagsMax = 15, tagsMaxChars = 500),
        PlatformSpec(ConnectPlatform.INSTAGRAM, listOf(Aspect.PORTRAIT, Aspect.SQUARE), maxSeconds = 900, titleMax = null, captionMax = 2200, hashtagsMax = 30),
        PlatformSpec(ConnectPlatform.FACEBOOK, listOf(Aspect.LANDSCAPE, Aspect.PORTRAIT, Aspect.SQUARE), maxSeconds = 4 * 3600, titleMax = null, captionMax = 63_206, hashtagsMax = 30),
        PlatformSpec(ConnectPlatform.TIKTOK, listOf(Aspect.PORTRAIT), maxSeconds = 600, titleMax = null, captionMax = 2200, hashtagsMax = 30),
    )

    /** Null for platforms Creatora knows no limits for (they take the share sheet and nothing is checked). */
    fun of(platform: ConnectPlatform): PlatformSpec? = all.firstOrNull { it.platform == platform }
}

/** The picture shape a format is rendered in. */
fun aspectOf(format: VideoFormat?): Aspect = when (format) {
    VideoFormat.SHORTS_REELS, VideoFormat.CARTOON_STORY, VideoFormat.AVATAR_VIDEO -> Aspect.PORTRAIT
    VideoFormat.POST_CAROUSEL -> Aspect.SQUARE
    else -> Aspect.LANDSCAPE
}

/** Something the finished video or its text does not fit on a platform. */
sealed interface Fit {
    data class TooLong(val seconds: Int, val max: Int) : Fit
    data class WrongShape(val have: Aspect, val wanted: Aspect) : Fit
    data class TextTooLong(val field: Field, val used: Int, val max: Int) : Fit
}

enum class Field { TITLE, CAPTION, HASHTAGS, TAGS }

/** Used and allowed characters, for the counter under a field. */
data class Count(val used: Int, val max: Int) {
    val over: Boolean get() = used > max
}

/** Everything that does not fit [spec]. An empty list means it fits. Nothing is changed: the user decides to trim, split or re-shape. */
fun fitIssues(spec: PlatformSpec, seconds: Int, aspect: Aspect, title: String, caption: String, hashtags: List<String>, tags: List<String> = emptyList()): List<Fit> = buildList {
    if (seconds > spec.maxSeconds) add(Fit.TooLong(seconds, spec.maxSeconds))
    if (aspect !in spec.aspects) add(Fit.WrongShape(aspect, spec.aspects.first()))
    if (spec.titleMax != null && title.length > spec.titleMax) add(Fit.TextTooLong(Field.TITLE, title.length, spec.titleMax))
    if (caption.length > spec.captionMax) add(Fit.TextTooLong(Field.CAPTION, caption.length, spec.captionMax))
    if (hashtags.size > spec.hashtagsMax) add(Fit.TextTooLong(Field.HASHTAGS, hashtags.size, spec.hashtagsMax))
    val tagChars = tags.sumOf { it.length + if (' ' in it) 2 else 0 } + (tags.size - 1).coerceAtLeast(0)
    if (spec.tagsMaxChars != null && tagChars > spec.tagsMaxChars) add(Fit.TextTooLong(Field.TAGS, tagChars, spec.tagsMaxChars))
}

/** The first [max] hashtags, for AI suggestions that must fit. The user's own text is never cut. */
fun fitSuggestion(hashtags: List<String>, max: Int): List<String> = hashtags.take(max)

/** The text of a post: description first, then the hashtags on their own line. */
fun captionWithTags(caption: String, hashtags: List<String>): String =
    listOf(caption.trim(), hashtags.joinToString(" ") { if (it.startsWith("#")) it else "#$it" }).filter { it.isNotBlank() }.joinToString("\n\n")
