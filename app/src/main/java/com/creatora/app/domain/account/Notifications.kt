package com.creatora.app.domain.account

enum class NotificationCategory(val channelId: String) {
    DAILY_IDEAS("daily_ideas"),
    RENDER_DONE("render_done"),
    NEW_COMMENTS("new_comments"),
    VIEW_MILESTONES("view_milestones"),
    EARNINGS("earnings"),
    PLAN_USAGE("plan_usage"),
}

data class NotificationPrefs(
    val enabled: Set<NotificationCategory> = NotificationCategory.entries.toSet(),
    /** A "views" notification is sent each time the channel's total passes a multiple of this. */
    val milestoneViews: Long = DEFAULT_MILESTONE,
) {
    fun isOn(category: NotificationCategory) = category in enabled

    companion object {
        const val DEFAULT_MILESTONE = 1_000L
    }
}

/** The milestone (a multiple of [step]) that the total passed going from [previous] to [current], or null. */
fun crossedMilestone(previous: Long, current: Long, step: Long): Long? {
    if (step <= 0 || previous < 0 || current <= previous) return null
    val reached = current / step
    return if (reached > previous / step) reached * step else null
}
