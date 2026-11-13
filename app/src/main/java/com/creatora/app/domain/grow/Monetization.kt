package com.creatora.app.domain.grow

/**
 * The YouTube Partner Program thresholds, in one place with the date they were last checked. YouTube changes these, so
 * everything built on them is labelled an estimate and points to YouTube Studio for the official status.
 */
object PartnerProgram {
    const val LAST_CHECKED = "2026-11, from the program's public rules as known when this was built; check YouTube Studio"

    /** Tier 1: early access to some features. */
    val tier1 = TierRule(subscribers = 500, publicUploads90 = 3, watchHours365 = 3_000, shortsViews90 = 3_000_000)

    /** Tier 2: ad revenue. */
    val tier2 = TierRule(subscribers = 1_000, publicUploads90 = null, watchHours365 = 4_000, shortsViews90 = 10_000_000)
}

data class TierRule(val subscribers: Int, val publicUploads90: Int?, val watchHours365: Int, val shortsViews90: Long)

enum class Requirement { SUBSCRIBERS, UPLOADS_90, WATCH_HOURS_365, SHORTS_VIEWS_90 }

/** What the account has now. A value the API did not give is null and counts as unknown, never zero. */
data class MonetizationInputs(val subscribers: Long?, val publicUploads90: Int?, val watchHours365: Double?, val shortsViews90: Long?)

data class Progress(val requirement: Requirement, val have: Double?, val need: Double) {
    val met: Boolean get() = have != null && have >= need
    val remaining: Double? get() = have?.let { (need - it).coerceAtLeast(0.0) }

    /** 0 to 1 for a progress bar; null when unknown. */
    val fraction: Double? get() = have?.let { (it / need).coerceIn(0.0, 1.0) }
}

enum class PathKind { WATCH_HOURS, SHORTS }

/** One way to reach a tier: every requirement of the path must be met. */
data class PathProgress(val kind: PathKind, val items: List<Progress>) {
    val met: Boolean get() = items.all { it.met }

    /**
     * How far along this path is, from its own requirements (subscribers are shared by both paths, so they do not tell
     * the paths apart). Null when none of them is known, so an unknown path is never called the closest.
     */
    val score: Double? get() = items.filter { it.requirement != Requirement.SUBSCRIBERS }.mapNotNull { it.fraction }.takeIf { it.isNotEmpty() }?.average()
}

data class TierProgress(val tier: Int, val paths: List<PathProgress>) {
    val met: Boolean get() = paths.any { it.met }

    /** The path the account is nearest to finishing: the answer to "you are closest through ...". */
    val closest: PathProgress? get() = paths.filter { it.score != null }.maxByOrNull { it.score!! }
}

fun tierProgress(tier: Int, rule: TierRule, inputs: MonetizationInputs): TierProgress {
    val subs = Progress(Requirement.SUBSCRIBERS, inputs.subscribers?.toDouble(), rule.subscribers.toDouble())
    val hours = buildList {
        add(subs)
        rule.publicUploads90?.let { add(Progress(Requirement.UPLOADS_90, inputs.publicUploads90?.toDouble(), it.toDouble())) }
        add(Progress(Requirement.WATCH_HOURS_365, inputs.watchHours365, rule.watchHours365.toDouble()))
    }
    val shorts = listOf(subs, Progress(Requirement.SHORTS_VIEWS_90, inputs.shortsViews90?.toDouble(), rule.shortsViews90.toDouble()))
    return TierProgress(tier, listOf(PathProgress(PathKind.WATCH_HOURS, hours), PathProgress(PathKind.SHORTS, shorts)))
}

/** Both tiers for an account. */
fun monetizationJourney(inputs: MonetizationInputs): List<TierProgress> = listOf(
    tierProgress(1, PartnerProgram.tier1, inputs),
    tierProgress(2, PartnerProgram.tier2, inputs),
)
