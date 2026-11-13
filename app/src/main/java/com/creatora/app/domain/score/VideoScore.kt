package com.creatora.app.domain.score

import com.creatora.app.domain.account.Plan
import com.creatora.app.domain.model.Project
import com.creatora.app.domain.model.VideoFormat
import com.creatora.app.domain.model.WizardStep
import com.creatora.app.domain.model.readingSeconds
import com.creatora.app.domain.model.wordCount
import com.creatora.app.domain.render.splitSentences
import kotlin.math.roundToInt

enum class CheckId { TITLE, HOOK, LENGTH, KEYWORD, LICENCES, THUMBNAIL }

enum class CheckStatus { PASS, WARN, FAIL, NOT_CHECKED }

/** Why a check came out as it did, as numbers the screen can put in a sentence. */
sealed interface Reason {
    data class TitleLength(val length: Int) : Reason
    data object TitleCaps : Reason
    data object TitleOk : Reason
    data class HookLong(val words: Int) : Reason
    data object HookNoPromise : Reason
    data object HookOk : Reason
    data object NoScript : Reason
    data class LengthOverPlan(val seconds: Int, val max: Int) : Reason
    data class LengthOutsideGuide(val seconds: Int, val min: Int, val max: Int) : Reason
    data class LengthOk(val seconds: Int) : Reason
    data class KeywordMissing(val keyword: String) : Reason
    data class KeywordFound(val keyword: String) : Reason
    data object KeywordUnknown : Reason
    data class LicencesMissing(val missing: Int) : Reason
    data object LicencesOk : Reason
    data object ThumbnailNotChecked : Reason
}

/** Where "Fix it" takes the user: the step that holds the problem (null when there is nothing to fix). */
data class Check(val id: CheckId, val status: CheckStatus, val reason: Reason, val fix: WizardStep?)

/** [percent] is the weighted share of checks passed, from the checks that could run. It is Creatora's checks, not a prediction of views. */
data class VideoScore(val checks: List<Check>, val percent: Int?)

/** The length a format aims for in seconds: a guideline, not a rule. */
fun lengthGuide(format: VideoFormat?): IntRange = when (format) {
    VideoFormat.SHORTS_REELS -> 15..60
    VideoFormat.VIDEO_AD -> 15..60
    VideoFormat.LONG_VIDEO -> 180..900
    VideoFormat.CARTOON_STORY -> 60..600
    else -> 1..Int.MAX_VALUE
}

private const val TITLE_MIN = 10
private const val TITLE_SHOWN = 70
private const val TITLE_MAX = 100
private const val HOOK_WORDS = 25

private val STOP = setOf("the", "and", "for", "with", "how", "what", "this", "that", "your", "from")

/** The longest word of the title that is not a filler word: the main word the video should be about. */
fun mainWord(title: String): String? = title.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 4 && it !in STOP }.maxByOrNull { it.length }

/**
 * Rule-based checks of a project. [importedAssets] and [licensedAssets] come from the licence log. The thumbnail is not
 * part of a project yet, so it is reported as not checked instead of guessed.
 */
fun videoScore(project: Project, plan: Plan, importedAssets: Int, licensedAssets: Int): VideoScore {
    val title = project.title.trim()
    val titleCheck = when {
        title.length < TITLE_MIN || title.length > TITLE_MAX -> Check(CheckId.TITLE, CheckStatus.FAIL, Reason.TitleLength(title.length), WizardStep.BRIEF)
        title.length > TITLE_SHOWN -> Check(CheckId.TITLE, CheckStatus.WARN, Reason.TitleLength(title.length), WizardStep.BRIEF)
        title.length > 6 && title == title.uppercase() && title.any { it.isLetter() } -> Check(CheckId.TITLE, CheckStatus.WARN, Reason.TitleCaps, WizardStep.BRIEF)
        else -> Check(CheckId.TITLE, CheckStatus.PASS, Reason.TitleOk, null)
    }

    val script = project.script
    val sentences = splitSentences(script)
    val hookWords = sentences.take(2).sumOf { wordCount(it) }
    val firstTwo = sentences.take(2).joinToString(" ")
    val hookCheck = when {
        sentences.isEmpty() -> Check(CheckId.HOOK, CheckStatus.NOT_CHECKED, Reason.NoScript, WizardStep.SCRIPT)
        hookWords > HOOK_WORDS -> Check(CheckId.HOOK, CheckStatus.WARN, Reason.HookLong(hookWords), WizardStep.SCRIPT)
        !(firstTwo.contains('?') || firstTwo.any { it.isDigit() } || firstTwo.contains('!')) -> Check(CheckId.HOOK, CheckStatus.WARN, Reason.HookNoPromise, WizardStep.SCRIPT)
        else -> Check(CheckId.HOOK, CheckStatus.PASS, Reason.HookOk, null)
    }

    val seconds = readingSeconds(wordCount(script))
    val guide = lengthGuide(project.format)
    val max = plan.limits.maxVideoSeconds
    val lengthCheck = when {
        script.isBlank() -> Check(CheckId.LENGTH, CheckStatus.NOT_CHECKED, Reason.NoScript, WizardStep.SCRIPT)
        seconds > max -> Check(CheckId.LENGTH, CheckStatus.FAIL, Reason.LengthOverPlan(seconds, max), WizardStep.SCRIPT)
        seconds !in guide -> Check(CheckId.LENGTH, CheckStatus.WARN, Reason.LengthOutsideGuide(seconds, guide.first, guide.last), WizardStep.SCRIPT)
        else -> Check(CheckId.LENGTH, CheckStatus.PASS, Reason.LengthOk(seconds), null)
    }

    val keyword = mainWord(title)
    val keywordCheck = when {
        keyword == null || sentences.isEmpty() -> Check(CheckId.KEYWORD, CheckStatus.NOT_CHECKED, Reason.KeywordUnknown, null)
        firstTwo.lowercase().contains(keyword) -> Check(CheckId.KEYWORD, CheckStatus.PASS, Reason.KeywordFound(keyword), null)
        else -> Check(CheckId.KEYWORD, CheckStatus.WARN, Reason.KeywordMissing(keyword), WizardStep.SCRIPT)
    }

    val missing = importedAssets - licensedAssets
    val licenceCheck = if (project.licensedFootageOnly && missing > 0) Check(CheckId.LICENCES, CheckStatus.WARN, Reason.LicencesMissing(missing), WizardStep.FOOTAGE)
    else Check(CheckId.LICENCES, CheckStatus.PASS, Reason.LicencesOk, null)

    val thumbnail = Check(CheckId.THUMBNAIL, CheckStatus.NOT_CHECKED, Reason.ThumbnailNotChecked, null)

    val checks = listOf(titleCheck, hookCheck, lengthCheck, keywordCheck, licenceCheck, thumbnail)
    val counted = checks.filter { it.status != CheckStatus.NOT_CHECKED }
    val percent = if (counted.isEmpty()) null else
        (counted.sumOf { if (it.status == CheckStatus.PASS) 1.0 else if (it.status == CheckStatus.WARN) 0.5 else 0.0 } * 100 / counted.size).roundToInt()
    return VideoScore(checks, percent)
}
