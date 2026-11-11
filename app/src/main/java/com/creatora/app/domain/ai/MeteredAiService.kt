package com.creatora.app.domain.ai

import com.creatora.app.domain.account.AppLanguage
import com.creatora.app.domain.account.CreatorProfile
import com.creatora.app.domain.account.Goal
import com.creatora.app.domain.account.aimText
import com.creatora.app.domain.account.Plan
import com.creatora.app.domain.account.UsageLevel
import com.creatora.app.domain.account.monthKey
import com.creatora.app.domain.account.usageStatus
import com.creatora.app.domain.model.AiError
import com.creatora.app.domain.model.AiOutcome
import com.creatora.app.domain.model.AiRequest
import com.creatora.app.domain.repository.AiHistoryRepository
import com.creatora.app.domain.repository.AiService
import com.creatora.app.domain.repository.AiTone
import com.creatora.app.domain.repository.AuthService
import com.creatora.app.domain.repository.BillingRepository
import com.creatora.app.domain.repository.GoalRepository
import com.creatora.app.domain.repository.SettingsRepository
import com.creatora.app.domain.repository.UsageRepository
import com.creatora.app.domain.repository.UserRepository
import kotlinx.coroutines.flow.first

/**
 * Wraps the real AI service with the plan's monthly limit, the user's language, tone and creator profile, and the
 * history log. At the limit the call is refused before it reaches the AI. Only successful calls are counted.
 */
class MeteredAiService(
    private val delegate: AiService,
    private val auth: AuthService,
    private val users: UserRepository,
    private val usage: UsageRepository,
    private val history: AiHistoryRepository,
    private val settings: SettingsRepository,
    private val billing: BillingRepository,
    private val goals: GoalRepository,
    private val now: () -> Long = System::currentTimeMillis,
) : AiService {

    override suspend fun complete(request: AiRequest): AiOutcome<String> {
        val user = auth.user.first() ?: return AiOutcome.Failed(listOf(AiError.SignInRequired))
        val month = monthKey(now())
        val doc = users.observe(user.uid).first()
        val plan = if (billing.state.first().hasPro) Plan.PRO else doc?.plan ?: Plan.FREE
        if (usageStatus(usage.observe(user.uid, month).first(), plan.aiLimit).level == UsageLevel.BLOCKED) {
            return AiOutcome.Failed(listOf(AiError.LimitReached))
        }

        val personalize = settings.personalization.first()
        val persona = personaInstructions(
            language = settings.language.first() ?: AppLanguage.deviceDefault(),
            tone = settings.aiTone.first(),
            profile = if (personalize) doc?.creatorProfile else null,
            goal = if (personalize) goals.observe(user.uid).first().firstOrNull { it.primary && it.reachedAt == null } else null,
        )
        val outcome = delegate.complete(request.copy(system = request.system + "\n\n" + persona))
        if (outcome is AiOutcome.Ok) {
            usage.increment(user.uid, month)
            val prompt = request.messages.lastOrNull()?.text.orEmpty().take(HISTORY_PROMPT_LENGTH)
            runCatching { history.add(user.uid, prompt, now()) }
        }
        return outcome
    }

    private companion object {
        const val HISTORY_PROMPT_LENGTH = 120
    }
}

/** Extra instructions added to every request: reply language, tone and (when allowed) what the creator makes. */
fun personaInstructions(language: AppLanguage, tone: AiTone, profile: CreatorProfile?, goal: Goal? = null): String = buildString {
    append(
        when (language) {
            AppLanguage.ENGLISH -> "Reply in English."
            AppLanguage.HINDI -> "Reply in Hindi (Devanagari script)."
            AppLanguage.HINGLISH -> "Reply in Hinglish: Hindi written in Latin letters, the way people text."
        },
    )
    append(" Keep any marker words such as HOOK:, TITLES: or WHY: exactly as asked, in English.")
    append(
        when (tone) {
            AiTone.FRIENDLY -> " Sound friendly and encouraging."
            AiTone.PROFESSIONAL -> " Sound professional and concise."
            AiTone.FUNNY -> " Sound light and funny, without being silly about facts."
        },
    )
    append(" Never invent numbers or facts; if something is not known, say so.")
    append(" Never promise virality or guaranteed results; talk about aims, plans and progress.")
    if (profile != null) {
        append(" About this creator: niche ${profile.niche}")
        if (profile.formats.isNotEmpty()) append(", formats ${profile.formats.joinToString()}")
        if (profile.summary.isNotBlank()) append(". ${profile.summary}")
        append('.')
    }
    if (goal != null) {
        append(" The creator's aim for the next 30 days: ${goal.aimText()}.")
        if (goal.desire.isNotBlank()) append(" In their own words: \"${goal.desire}\".")
        goal.uploadsPerWeek?.let { append(" They would like to upload $it times a week.") }
        append(" Steer suggestions toward this aim.")
    }
}
