package com.creatora.app.domain.create

import com.creatora.app.domain.account.Plan
import com.creatora.app.domain.account.PlanLimits
import com.creatora.app.domain.model.StepState
import com.creatora.app.domain.model.VideoFormat
import com.creatora.app.domain.model.WizardStep

/** How a format is offered on the Create tab. */
enum class FormatAccess { OPEN, PRO, COMING_LATER }

/** Avatar video is not built for anyone; formats outside the plan are locked behind Pro. */
fun formatAccess(plan: Plan, format: VideoFormat): FormatAccess = when {
    format == VideoFormat.AVATAR_VIDEO -> FormatAccess.COMING_LATER
    format in plan.limits.formats -> FormatAccess.OPEN
    else -> FormatAccess.PRO
}

/**
 * Records that [step] was changed (by the user, or by the AI when [byAi]). Later steps that were already done are marked
 * OUT_OF_DATE so the user can regenerate them; nothing is overwritten.
 */
fun stepChanged(states: Map<WizardStep, StepState>, step: WizardStep, byAi: Boolean): Map<WizardStep, StepState> {
    val result = states.toMutableMap()
    result[step] = if (byAi) StepState.AI_DRAFT else StepState.EDITED
    WizardStep.entries.filter { it.ordinal > step.ordinal }.forEach { later ->
        if (result[later] == StepState.APPROVED || result[later] == StepState.EDITED) result[later] = StepState.OUT_OF_DATE
    }
    return result
}

/** The user moves on from [step]: an AI draft or an empty step counts as approved, an edited one stays edited. */
fun stepApproved(states: Map<WizardStep, StepState>, step: WizardStep): Map<WizardStep, StepState> =
    if (states[step] == StepState.EDITED) states else states + (step to StepState.APPROVED)

fun encodeStates(states: Map<WizardStep, StepState>): String = states.entries.joinToString(",") { "${it.key.name}=${it.value.name}" }

fun decodeStates(text: String?): Map<WizardStep, StepState> =
    text.orEmpty().split(',').mapNotNull { part ->
        val (k, v) = part.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
        val step = WizardStep.entries.firstOrNull { it.name == k } ?: return@mapNotNull null
        val state = StepState.entries.firstOrNull { it.name == v } ?: return@mapNotNull null
        step to state
    }.toMap()

// ---------------------------------------------------------------- limits and cost

/** Why a render may not start. */
sealed interface RenderGate {
    data object Allowed : RenderGate
    data object FormatLocked : RenderGate
    data class TooLong(val seconds: Int, val max: Int) : RenderGate
    data class DailyLimit(val max: Int) : RenderGate
}

/**
 * Checks the plan before a render. A project that already counted today (a re-render) is not counted again, so only
 * its length is checked.
 */
fun renderGate(plan: Plan, format: VideoFormat?, seconds: Int, videosToday: Int, alreadyCounted: Boolean): RenderGate {
    val limits = plan.limits
    return when {
        format != null && format !in limits.formats -> RenderGate.FormatLocked
        seconds > limits.maxVideoSeconds -> RenderGate.TooLong(seconds, limits.maxVideoSeconds)
        !alreadyCounted && videosToday >= limits.videosPerDay -> RenderGate.DailyLimit(limits.videosPerDay)
        else -> RenderGate.Allowed
    }
}

sealed interface ImageGate {
    data object Allowed : ImageGate
    data class DailyLimit(val max: Int) : ImageGate
    data object CreditsUsedUp : ImageGate
}

/** AI images are limited per day by the plan and cost credits from the monthly meter. */
fun imageGate(plan: Plan, imagesToday: Int, creditsLeft: Int): ImageGate = when {
    imagesToday >= plan.limits.aiImagesPerDay -> ImageGate.DailyLimit(plan.limits.aiImagesPerDay)
    creditsLeft < PlanLimits.costs.image -> ImageGate.CreditsUsedUp
    else -> ImageGate.Allowed
}

/** What Auto mode will do and what it will cost, shown before the user confirms. */
data class AutoPlan(val textActions: Int, val images: Int, val credits: Int)

/** Brief and script are two text actions; images are as many as the day's allowance and credits leave room for, up to [wanted]. */
fun autoPlan(plan: Plan, imagesToday: Int, creditsLeft: Int, wanted: Int = AUTO_IMAGES): AutoPlan {
    val costs = PlanLimits.costs
    val roomForImages = (plan.limits.aiImagesPerDay - imagesToday).coerceAtLeast(0)
    val creditsForImages = ((creditsLeft - 2 * costs.text) / costs.image).coerceAtLeast(0)
    val images = minOf(wanted, roomForImages, creditsForImages)
    return AutoPlan(textActions = 2, images = images, credits = 2 * costs.text + images * costs.image)
}

const val AUTO_IMAGES = 3
