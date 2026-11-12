package com.creatora.app.domain.publish

import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.account.ConnectedAccount

/** How a destination is reached: Creatora posts through the platform's API, or hands the video to the share sheet. */
enum class Route { API, SHARE_SHEET }

private val API_PLATFORMS = setOf(ConnectPlatform.YOUTUBE, ConnectPlatform.TIKTOK)

/**
 * YouTube and TikTok (connected by login) are posted through their APIs; Instagram and Facebook wait until their app
 * review and a place to host the video, and link-only platforms have no API at all, so they use the share sheet.
 * Only YouTube can hold a post for later; a later post to any other platform is a reminder that opens the share sheet.
 */
fun routeFor(account: ConnectedAccount, scheduled: Boolean): Route = when {
    account.link != null -> Route.SHARE_SHEET
    account.platform == ConnectPlatform.YOUTUBE -> Route.API
    account.platform in API_PLATFORMS && !scheduled -> Route.API
    else -> Route.SHARE_SHEET
}

data class PlannedPost(val post: ScheduledPost, val route: Route)

/** One [ScheduledPost] per destination, all sharing [postId]. [scheduledAt] is null for "now". */
fun planPosts(
    postId: String, projectId: Long, title: String, accounts: List<ConnectedAccount>, scheduledAt: Long?, makePublic: Boolean, now: Long,
): List<PlannedPost> = accounts.map { account ->
    val route = routeFor(account, scheduledAt != null)
    val status = if (route == Route.SHARE_SHEET && scheduledAt != null) PostStatus.SCHEDULED else PostStatus.QUEUED
    PlannedPost(
        ScheduledPost(
            id = "${postId}_${account.key}", postId = postId, projectId = projectId, accountKey = account.key, platform = account.platform,
            title = title, scheduledAt = scheduledAt ?: now, status = status, privacy = if (makePublic) "public" else "private", createdAt = now,
        ),
        route,
    )
}

/** What the screen says about the plan for one destination. */
sealed interface DestinationNote {
    data object ShareSheet : DestinationNote
    data object PrivateUntilApproved : DestinationNote
    data object ReminderOnly : DestinationNote
    data object None : DestinationNote
}

/** A note to show under a destination chip, so the share sheet and the private-until-approved rule are never hidden. */
fun noteFor(account: ConnectedAccount, scheduled: Boolean): DestinationNote = when {
    routeFor(account, scheduled) == Route.SHARE_SHEET && scheduled -> DestinationNote.ReminderOnly
    routeFor(account, scheduled) == Route.SHARE_SHEET -> DestinationNote.ShareSheet
    account.platform == ConnectPlatform.YOUTUBE || account.platform == ConnectPlatform.TIKTOK -> DestinationNote.PrivateUntilApproved
    else -> DestinationNote.None
}

private val TitleCue = Regex("[0-9?!]")

/** A title option's score out of 100 from things that can be measured; Creatora's check, not a prediction of views. */
data class TitleScore(val points: Int, val reasons: List<TitleNote>)

enum class TitleNote { GOOD_LENGTH, TOO_SHORT, TOO_LONG_FOR_SEARCH, NOT_SHOUTING, ALL_CAPS, HAS_CUE, HAS_KEYWORD, NO_KEYWORD }

fun scoreTitle(title: String, keyword: String?): TitleScore {
    val t = title.trim()
    val notes = mutableListOf<TitleNote>()
    var points = 0
    when {
        t.length < 20 -> notes += TitleNote.TOO_SHORT
        t.length > TITLE_SHOWN -> notes += TitleNote.TOO_LONG_FOR_SEARCH
        else -> { points += 40; notes += TitleNote.GOOD_LENGTH }
    }
    if (t.length > 5 && t == t.uppercase() && t.any { it.isLetter() }) notes += TitleNote.ALL_CAPS else { points += 20; notes += TitleNote.NOT_SHOUTING }
    if (TitleCue.containsMatchIn(t)) { points += 20; notes += TitleNote.HAS_CUE }
    if (!keyword.isNullOrBlank()) {
        if (t.contains(keyword, ignoreCase = true)) { points += 20; notes += TitleNote.HAS_KEYWORD } else notes += TitleNote.NO_KEYWORD
    } else {
        points += 20 // nothing to compare with: not held against the title
    }
    return TitleScore(points, notes)
}
