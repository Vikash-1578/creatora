package com.creatora.app.ui.publish

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.creatora.app.R
import com.creatora.app.domain.publish.Aspect
import com.creatora.app.domain.publish.BestBasis
import com.creatora.app.domain.publish.BestTime
import com.creatora.app.domain.publish.DayPart
import com.creatora.app.domain.publish.Field
import com.creatora.app.domain.publish.Fit
import com.creatora.app.domain.publish.PubCheck
import com.creatora.app.domain.publish.PubItem
import com.creatora.app.domain.publish.PubReason
import com.creatora.app.domain.score.CheckStatus
import com.creatora.app.ui.components.Tone
import java.time.format.TextStyle
import java.util.Locale

fun PubCheck.labelRes() = when (this) {
    PubCheck.RENDERED -> R.string.pubcheck_rendered
    PubCheck.TITLE -> R.string.pubcheck_title
    PubCheck.THUMBNAIL -> R.string.pubcheck_thumbnail
    PubCheck.DESCRIPTION -> R.string.pubcheck_description
    PubCheck.TAGS -> R.string.pubcheck_tags
    PubCheck.AI_DISCLOSURE -> R.string.pubcheck_ai
    PubCheck.MADE_FOR_KIDS -> R.string.pubcheck_kids
    PubCheck.LICENCES -> R.string.pubcheck_licences
}

fun PubItem.statusText() = when (status) {
    CheckStatus.PASS -> R.string.score_pass
    CheckStatus.WARN -> R.string.score_warn
    CheckStatus.FAIL -> R.string.score_fail
    CheckStatus.NOT_CHECKED -> R.string.score_not_checked
}

fun PubItem.statusTone() = when (status) {
    CheckStatus.PASS -> Tone.GOOD
    CheckStatus.WARN -> Tone.WARN
    CheckStatus.FAIL -> Tone.BAD
    CheckStatus.NOT_CHECKED -> Tone.NEUTRAL
}

/** Why a check came out as it did, in a sentence with the numbers. */
@Composable
fun PubItem.reasonText(): String = when (val r = reason) {
    PubReason.NoVideo -> stringResource(R.string.pubreason_no_video)
    PubReason.TitleEmpty -> stringResource(R.string.pubreason_title_empty)
    is PubReason.TitleLength -> stringResource(R.string.pubreason_title_length, r.length, r.max)
    is PubReason.TitleShown -> stringResource(R.string.pubreason_title_shown, r.length, r.shown)
    PubReason.TitleOk -> stringResource(R.string.pubreason_ok)
    PubReason.ThumbnailMissing -> stringResource(R.string.pubreason_thumbnail_missing)
    PubReason.ThumbnailOk -> stringResource(R.string.pubreason_thumbnail_ok)
    PubReason.DescriptionEmpty -> stringResource(R.string.pubreason_description_empty)
    is PubReason.DescriptionShort -> stringResource(R.string.pubreason_description_short, r.chars)
    PubReason.DescriptionOk -> stringResource(R.string.pubreason_ok)
    PubReason.TagsNone -> stringResource(R.string.pubreason_tags_none)
    is PubReason.TagsStuffed -> stringResource(R.string.pubreason_tags_stuffed, r.count, r.chars)
    PubReason.TagsOk -> stringResource(R.string.pubreason_ok)
    is PubReason.Unanswered -> stringResource(if (r.ai) R.string.pubreason_ai_unanswered else R.string.pubreason_kids_unanswered)
    is PubReason.Answered -> stringResource(if (r.yes) R.string.pubreason_answered_yes else R.string.pubreason_answered_no)
    is PubReason.LicencesMissing -> stringResource(R.string.pubreason_licences_missing, r.missing)
    PubReason.LicencesOk -> stringResource(R.string.pubreason_ok)
}

@Composable
private fun Aspect.label() = stringResource(
    when (this) {
        Aspect.PORTRAIT -> R.string.aspect_portrait
        Aspect.LANDSCAPE -> R.string.aspect_landscape
        Aspect.SQUARE -> R.string.aspect_square
    },
)

/** What does not fit a platform, with its limit and name. Nothing is cut: the user trims, splits or changes the shape. */
@Composable
fun fitText(platform: String, fit: Fit): String = when (fit) {
    is Fit.TooLong -> stringResource(R.string.fit_too_long, platform, fit.seconds, fit.max)
    is Fit.WrongShape -> stringResource(R.string.fit_wrong_shape, platform, fit.have.label(), fit.wanted.label())
    is Fit.TextTooLong -> stringResource(
        when (fit.field) {
            Field.TITLE -> R.string.fit_title
            Field.CAPTION -> R.string.fit_caption
            Field.HASHTAGS -> R.string.fit_hashtags
            Field.TAGS -> R.string.fit_tags
        },
        platform, fit.used, fit.max,
    )
}

@Composable
private fun DayPart.label() = stringResource(
    when (this) {
        DayPart.MORNING -> R.string.daypart_morning
        DayPart.AFTERNOON -> R.string.daypart_afternoon
        DayPart.EVENING -> R.string.daypart_evening
        DayPart.NIGHT -> R.string.daypart_night
    },
)

/** The best-time suggestion with its reason; general guidance is labelled as not coming from the creator's data. */
@Composable
fun bestText(best: BestTime): String {
    val day = best.day.getDisplayName(TextStyle.FULL, Locale.getDefault())
    return when (val basis = best.basis) {
        is BestBasis.OwnData -> stringResource(if (basis.usedWeekdayViews) R.string.best_own_weekday else R.string.best_own, day, best.part.label(), basis.videos)
        BestBasis.Guidance -> stringResource(R.string.best_guidance, day, best.part.label())
    }
}
