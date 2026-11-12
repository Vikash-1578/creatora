package com.creatora.app.domain.publish

import com.creatora.app.domain.score.CheckStatus

enum class PubCheck { RENDERED, TITLE, THUMBNAIL, DESCRIPTION, TAGS, AI_DISCLOSURE, MADE_FOR_KIDS, LICENCES }

/** Where "Fix it" takes the user. */
enum class PubFix { RENDER, TITLE, THUMBNAIL, DESCRIPTION, TAGS, AI_QUESTION, KIDS_QUESTION, LICENCES }

sealed interface PubReason {
    data object NoVideo : PubReason
    data object TitleEmpty : PubReason
    data class TitleLength(val length: Int, val max: Int) : PubReason
    data class TitleShown(val length: Int, val shown: Int) : PubReason
    data object TitleOk : PubReason
    data object ThumbnailMissing : PubReason
    data object ThumbnailOk : PubReason
    data object DescriptionEmpty : PubReason
    data class DescriptionShort(val chars: Int) : PubReason
    data object DescriptionOk : PubReason
    data object TagsNone : PubReason
    data class TagsStuffed(val count: Int, val chars: Int) : PubReason
    data object TagsOk : PubReason
    data class Unanswered(val ai: Boolean) : PubReason
    data class Answered(val yes: Boolean) : PubReason
    data class LicencesMissing(val missing: Int) : PubReason
    data object LicencesOk : PubReason
}

data class PubItem(val check: PubCheck, val status: CheckStatus, val reason: PubReason, val fix: PubFix?)

/** What the checks look at. [aiContent] and [madeForKids] are null until the user answers; they are never defaulted. */
data class PublishDraft(
    val rendered: Boolean,
    val title: String,
    val description: String,
    val tags: List<String>,
    val hasThumbnail: Boolean,
    val aiContent: Boolean?,
    val madeForKids: Boolean?,
    val unlicensedAssets: Int,
    /** True when the project used AI pictures, voices or clips: the AI-content question is pre-suggested as "yes". */
    val usedAi: Boolean = false,
)

const val TITLE_SHOWN = 70
const val TITLE_MAX = 100
private const val DESCRIPTION_SEEN = 100
private const val TAGS_MAX_COUNT = 15
private const val TAGS_MAX_CHARS = 500

/** Creatora's checks before posting. A WARN or FAIL has a reason and a [PubFix]; the two questions and the video itself are required. */
fun publishChecks(d: PublishDraft): List<PubItem> = listOf(
    if (d.rendered) PubItem(PubCheck.RENDERED, CheckStatus.PASS, PubReason.TitleOk, null) else PubItem(PubCheck.RENDERED, CheckStatus.FAIL, PubReason.NoVideo, PubFix.RENDER),
    when {
        d.title.isBlank() -> PubItem(PubCheck.TITLE, CheckStatus.FAIL, PubReason.TitleEmpty, PubFix.TITLE)
        d.title.length > TITLE_MAX -> PubItem(PubCheck.TITLE, CheckStatus.FAIL, PubReason.TitleLength(d.title.length, TITLE_MAX), PubFix.TITLE)
        d.title.length > TITLE_SHOWN -> PubItem(PubCheck.TITLE, CheckStatus.WARN, PubReason.TitleShown(d.title.length, TITLE_SHOWN), PubFix.TITLE)
        else -> PubItem(PubCheck.TITLE, CheckStatus.PASS, PubReason.TitleOk, null)
    },
    // Thumbnail text readability is not measured here (no pixels are read), so only its presence is checked.
    if (d.hasThumbnail) PubItem(PubCheck.THUMBNAIL, CheckStatus.PASS, PubReason.ThumbnailOk, null) else PubItem(PubCheck.THUMBNAIL, CheckStatus.WARN, PubReason.ThumbnailMissing, PubFix.THUMBNAIL),
    firstTwoLines(d.description).let { seen ->
        when {
            d.description.isBlank() -> PubItem(PubCheck.DESCRIPTION, CheckStatus.WARN, PubReason.DescriptionEmpty, PubFix.DESCRIPTION)
            seen.length < DESCRIPTION_SEEN / 2 -> PubItem(PubCheck.DESCRIPTION, CheckStatus.WARN, PubReason.DescriptionShort(seen.length), PubFix.DESCRIPTION)
            else -> PubItem(PubCheck.DESCRIPTION, CheckStatus.PASS, PubReason.DescriptionOk, null)
        }
    },
    when {
        d.tags.isEmpty() -> PubItem(PubCheck.TAGS, CheckStatus.WARN, PubReason.TagsNone, PubFix.TAGS)
        d.tags.size > TAGS_MAX_COUNT || d.tags.sumOf { it.length } > TAGS_MAX_CHARS -> PubItem(PubCheck.TAGS, CheckStatus.WARN, PubReason.TagsStuffed(d.tags.size, d.tags.sumOf { it.length }), PubFix.TAGS)
        else -> PubItem(PubCheck.TAGS, CheckStatus.PASS, PubReason.TagsOk, null)
    },
    d.aiContent?.let { PubItem(PubCheck.AI_DISCLOSURE, CheckStatus.PASS, PubReason.Answered(it), null) }
        ?: PubItem(PubCheck.AI_DISCLOSURE, CheckStatus.FAIL, PubReason.Unanswered(ai = true), PubFix.AI_QUESTION),
    d.madeForKids?.let { PubItem(PubCheck.MADE_FOR_KIDS, CheckStatus.PASS, PubReason.Answered(it), null) }
        ?: PubItem(PubCheck.MADE_FOR_KIDS, CheckStatus.FAIL, PubReason.Unanswered(ai = false), PubFix.KIDS_QUESTION),
    if (d.unlicensedAssets > 0) PubItem(PubCheck.LICENCES, CheckStatus.WARN, PubReason.LicencesMissing(d.unlicensedAssets), PubFix.LICENCES) else PubItem(PubCheck.LICENCES, CheckStatus.PASS, PubReason.LicencesOk, null),
)

/** The first two lines of a description: the part people see before "show more". */
fun firstTwoLines(description: String): String = description.lines().filter { it.isNotBlank() }.take(2).joinToString("\n").trim()

/** Posting is allowed with warnings, but never without a rendered video or with either question unanswered. */
fun canPublish(items: List<PubItem>): Boolean =
    items.none { it.status == CheckStatus.FAIL && it.check in setOf(PubCheck.RENDERED, PubCheck.AI_DISCLOSURE, PubCheck.MADE_FOR_KIDS, PubCheck.TITLE) }

/** The answer to suggest for the AI-content question: yes when AI pictures, voices or clips were used. The user still answers. */
fun suggestedAiAnswer(d: PublishDraft): Boolean = d.usedAi
