package com.creatora.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.creatora.app.R
import com.creatora.app.domain.home.Snapshot
import com.creatora.app.domain.home.StreakUnit
import com.creatora.app.domain.home.UploadRow
import com.creatora.app.domain.home.UploadStatus
import com.creatora.app.ui.ai.message
import com.creatora.app.ui.components.ChoiceChip
import com.creatora.app.ui.components.MetricChip
import com.creatora.app.ui.components.MetricTile
import com.creatora.app.ui.components.SecondaryButton
import com.creatora.app.ui.components.StatusChip
import com.creatora.app.ui.components.Tone
import com.creatora.app.ui.components.VideoThumbnail
import com.creatora.app.ui.theme.CreatoraTheme
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** Subscribers, views, watch time and (when monetized) estimated earnings, each against the previous 28 days. */
@Composable
fun SnapshotSection(cards: ChannelCardsState, onHide: (Boolean) -> Unit, onRetry: () -> Unit) {
    HomeSection(
        titleRes = R.string.home_snapshot_title,
        icon = Icons.Outlined.Insights,
        info = if (cards.phase == CardsPhase.READY) stringResource(if (cards.accountCount > 1) R.string.home_snapshot_note_all else R.string.home_snapshot_note) else null,
        trailing = {
            if (cards.snapshot?.earningsUsd != null) {
                IconButton(onClick = { onHide(!cards.hideAmounts) }, modifier = Modifier.size(CreatoraTheme.dimens.minTouchTarget)) {
                    Icon(
                        if (cards.hideAmounts) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = stringResource(if (cards.hideAmounts) R.string.home_show_amounts else R.string.home_hide_amounts),
                        tint = CreatoraTheme.colors.textMuted,
                    )
                }
            }
        },
    ) {
        when (cards.phase) {
            CardsPhase.LOADING -> LoadingRow(R.string.home_loading)
            CardsPhase.NO_ACCOUNT -> Unit
            CardsPhase.UNSUPPORTED, CardsPhase.FAILED -> HomeCard {
                BodyText(cards.error?.message() ?: stringResource(R.string.home_error_not_available))
                if (cards.phase == CardsPhase.FAILED) SecondaryButton(stringResource(R.string.action_try_again), onRetry)
            }
            CardsPhase.READY -> SnapshotTiles(cards.snapshot, cards.hideAmounts, cards.metricsError?.message())
        }
    }
}

/** The figures as a grid of tiles, two across. A figure the platform does not give is left out, not shown as zero. */
@Composable
private fun SnapshotTiles(snapshot: Snapshot?, hideAmounts: Boolean, metricsError: String?) {
    val locale = Locale.getDefault()
    if (snapshot == null) {
        HomeCard { BodyText(stringResource(R.string.home_error_not_available)) }
        return
    }
    val none = stringResource(R.string.home_not_available)
    val chartText = stringResource(R.string.home_chart_views)
    val tiles = mutableListOf<@Composable (Modifier) -> Unit>()
    tiles.add { modifier ->
        MetricTile(
            label = stringResource(R.string.home_subscribers), value = snapshot.subscribers?.let { compactCount(it, locale) } ?: none,
            icon = Icons.Outlined.Group, modifier = modifier,
            change = { snapshot.subscribersNet?.let { ChangeText(it, { d -> signedCompact(d.toLong(), locale) }) } },
        )
    }
    tiles.add { modifier ->
        MetricTile(
            label = stringResource(R.string.home_views_28), value = snapshot.views?.let { compactCount(it.current.toLong(), locale) } ?: none,
            icon = Icons.Outlined.PlayCircleOutline, modifier = modifier, chart = snapshot.viewsSeries, chartDescription = chartText,
            change = { snapshot.views?.let { ChangeText(it, { d -> signedCompact(d.toLong(), locale) }) } },
        )
    }
    if (snapshot.watchHours != null || metricsError != null) {
        tiles.add { modifier ->
            MetricTile(
                label = stringResource(R.string.home_watch_time), value = snapshot.watchHours?.let { stringResource(R.string.home_hours, compactCount(it.current.toLong(), locale)) } ?: none,
                icon = Icons.Outlined.Schedule, modifier = modifier,
                change = { snapshot.watchHours?.let { ChangeText(it, { d -> stringResource(R.string.home_hours, signedCompact(d.toLong(), locale)) }) } },
            )
        }
    }
    tiles.add { modifier ->
        val money = NumberFormat.getCurrencyInstance(locale).apply { currency = Currency.getInstance("USD"); maximumFractionDigits = 0 }
        MetricTile(
            label = stringResource(R.string.home_earnings),
            value = snapshot.earningsUsd?.let { if (hideAmounts) stringResource(R.string.home_hidden_amount) else money.format(it.current) } ?: none,
            icon = Icons.Outlined.Payments, modifier = modifier,
            change = {
                val earnings = snapshot.earningsUsd
                if (earnings == null) {
                    Text(stringResource(R.string.home_earnings_note), style = CreatoraTheme.typography.caption, color = CreatoraTheme.colors.textMuted)
                } else if (!hideAmounts) {
                    ChangeText(earnings, { d -> (if (d >= 0) "+" else "-") + money.format(kotlin.math.abs(d)) })
                }
            },
        )
    }
    tiles.chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.md)) {
            pair.forEach { tile -> tile(Modifier.weight(1f).fillMaxHeight()) }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    metricsError?.let { StatusChip(it, Tone.WARN) }
}

private fun UploadStatus.tone() = when (this) {
    UploadStatus.GOOD -> Tone.GOOD
    UploadStatus.AVERAGE -> Tone.WARN
    UploadStatus.POOR -> Tone.BAD
    UploadStatus.TOO_NEW, UploadStatus.UNKNOWN -> Tone.NEUTRAL
}

private fun UploadStatus.labelRes() = when (this) {
    UploadStatus.GOOD -> R.string.home_status_good
    UploadStatus.AVERAGE -> R.string.home_status_average
    UploadStatus.POOR -> R.string.home_status_poor
    UploadStatus.TOO_NEW -> R.string.home_status_too_new
    UploadStatus.UNKNOWN -> R.string.home_status_unknown
}

/** The last five uploads: thumbnail, title, a status chip and the figures YouTube returned; tap one for the AI's explanation. */
@Composable
fun UploadsSection(cards: ChannelCardsState, onOpen: (UploadRow) -> Unit, onSeeAll: (() -> Unit)? = null) {
    if (cards.phase != CardsPhase.READY) return
    val locale = Locale.getDefault()
    HomeSection(
        R.string.home_uploads_title, icon = Icons.Outlined.VideoLibrary, onSeeAll = onSeeAll,
        info = if (cards.accountCount > 1) stringResource(R.string.home_uploads_pick_one) else null,
    ) {
        if (cards.uploads.isEmpty()) {
            HomeCard { BodyText(stringResource(R.string.home_uploads_empty)) }
            return@HomeSection
        }
        HomeCard {
            cards.uploads.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(color = CreatoraTheme.colors.border)
                UploadItem(row, cards.uploadMetrics[row.video.id], locale, onOpen)
            }
        }
    }
}

@Composable
private fun UploadItem(row: UploadRow, metrics: com.creatora.app.domain.account.VideoAnalytics?, locale: Locale, onOpen: (UploadRow) -> Unit) {
    Column(
        Modifier.fillMaxWidth().heightIn(min = CreatoraTheme.dimens.minTouchTarget).clickable(role = Role.Button, onClickLabel = stringResource(R.string.home_uploads_why)) { onOpen(row) },
        verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            VideoThumbnail(row.video.thumbnailUrl)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.xs)) {
                Text(row.video.title, style = CreatoraTheme.typography.cardTitle, color = CreatoraTheme.colors.text, maxLines = 2)
                StatusChip(stringResource(row.status.labelRes()), row.status.tone())
            }
        }
        // Only what the platform returned: a missing CTR or retention is left out, not written as "not available" on every row.
        Row(horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.lg)) {
            MetricChip(Icons.Outlined.PlayCircleOutline, stringResource(R.string.home_upload_views_only, compactCount(row.video.views, locale)))
            metrics?.ctrPercent?.let { MetricChip(Icons.Outlined.TouchApp, stringResource(R.string.home_upload_ctr, String.format(locale, "%.1f%%", it))) }
            metrics?.averageViewPercent?.let { MetricChip(Icons.Outlined.Timer, stringResource(R.string.home_upload_viewed, String.format(locale, "%.0f%%", it))) }
        }
    }
}

@Composable
fun WhyDialog(why: WhyState, onClose: () -> Unit) {
    val row = when (why) {
        WhyState.Closed -> return
        is WhyState.Loading -> why.row
        is WhyState.Ready -> why.row
        is WhyState.Failed -> why.row
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(row.video.title, style = CreatoraTheme.typography.sectionTitle, maxLines = 2) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
                when (why) {
                    is WhyState.Loading -> LoadingRow(R.string.home_why_loading)
                    is WhyState.Ready -> BodyText(why.text, muted = false)
                    is WhyState.Failed -> why.errors.forEach { Text(it.message(), style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.danger) }
                    WhyState.Closed -> Unit
                }
                BodyText(stringResource(R.string.home_why_note))
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.action_close)) } },
        containerColor = CreatoraTheme.colors.surface,
    )
}

/** Weeks (or days) in a row with an upload, the best streak, and a gentle reminder when it is about to break. */
@Composable
fun StreakSection(cards: ChannelCardsState, onUnit: (StreakUnit) -> Unit) {
    val streak = cards.streak ?: return
    if (cards.phase != CardsPhase.READY) return
    val unitRes = if (cards.streakUnit == StreakUnit.WEEKS) R.plurals.home_streak_weeks else R.plurals.home_streak_days
    HomeSection(R.string.home_streak_title, icon = Icons.Outlined.LocalFireDepartment, info = stringResource(R.string.home_streak_note)) {
        HomeCard {
            Text(
                androidx.compose.ui.res.pluralStringResource(unitRes, streak.current, streak.current),
                style = CreatoraTheme.typography.screenTitle, color = CreatoraTheme.colors.text,
            )
            StatusChip(androidx.compose.ui.res.pluralStringResource(unitRes, streak.best, streak.best).let { stringResource(R.string.home_streak_best, it) }, Tone.NEUTRAL)
            if (streak.atRisk) StatusChip(stringResource(if (cards.streakUnit == StreakUnit.WEEKS) R.string.home_streak_reminder_week else R.string.home_streak_reminder_day), Tone.WARN)
            Row(horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
                ChoiceChip(stringResource(R.string.home_streak_weeks_chip), cards.streakUnit == StreakUnit.WEEKS, { onUnit(StreakUnit.WEEKS) })
                ChoiceChip(stringResource(R.string.home_streak_days_chip), cards.streakUnit == StreakUnit.DAYS, { onUnit(StreakUnit.DAYS) })
            }
        }
    }
}
