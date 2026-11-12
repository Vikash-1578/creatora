package com.creatora.app.domain.account

import com.creatora.app.domain.model.VideoFormat

/** What one plan allows. Every limit in the app is read from here; nothing else hard-codes a number. */
data class Limits(
    val aiActionsPerMonth: Int,
    val accounts: Int,
    val dailyIdeas: Int,
    val competitors: Int,
    val formats: Set<VideoFormat>,
    val videosPerDay: Int,
    val maxVideoSeconds: Int,
    val aiImagesPerDay: Int,
    val aiVideoClips: Boolean,
    val premiumVoices: Boolean,
    val publishesPerDay: Int,
    val platformsPerPost: Int,
)

/** What AI actions cost in credits (they count in the same usage meter as every AI action). */
data class CreditCosts(val text: Int, val image: Int, val voicePer1000Chars: Int, val videoClip: Int)

/**
 * The central plan limits. Enforced in the app for now; server-side enforcement comes later and reads the same table.
 * Free numbers are the manager's; Pro numbers and credit costs are placeholders to confirm.
 */
object PlanLimits {
    val costs = CreditCosts(text = 1, image = 3, voicePer1000Chars = 2, videoClip = 20)

    private val free = Limits(
        aiActionsPerMonth = 50, accounts = 1, dailyIdeas = 3, competitors = 3,
        formats = setOf(VideoFormat.SHORTS_REELS), videosPerDay = 1, maxVideoSeconds = 30,
        aiImagesPerDay = 5, aiVideoClips = false, premiumVoices = false,
        publishesPerDay = 1, platformsPerPost = 1,
    )

    private val pro = Limits(
        aiActionsPerMonth = 1000, accounts = 5, dailyIdeas = 10, competitors = 20,
        formats = VideoFormat.entries.filter { it != VideoFormat.AVATAR_VIDEO }.toSet(), videosPerDay = 10, maxVideoSeconds = 600,
        aiImagesPerDay = 50, aiVideoClips = true, premiumVoices = true,
        publishesPerDay = 10, platformsPerPost = 5,
    )

    fun of(plan: Plan): Limits = if (plan == Plan.PRO) pro else free
}
