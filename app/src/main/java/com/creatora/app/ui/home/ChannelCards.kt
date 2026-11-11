package com.creatora.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
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
import com.creatora.app.ui.components.SecondaryButton
import com.creatora.app.ui.theme.CreatoraTheme
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

@Composable
private fun Tile(label: String, value: String, change: @Composable () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.xs)) {
        Text(label, style = CreatoraTheme.typography.caption, color = CreatoraTheme.colors.textMuted)
        Text(value, style = CreatoraTheme.typography.sectionTitle, color = CreatoraTheme.colors.text)
        change()
    }
}

/** Subscribers, views, watch time and (when monetized) estimated earnings, each against the previous 28 days. */
@Composable
fun SnapshotSection(cards: ChannelCardsState, onHide: (Boolean) -> Unit, onRetry: () -> Unit) {
    HomeSection(
        titleRes = R.string.home_snapshot_title,
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
            CardsPhase.READY -> HomeCard { SnapshotTiles(cards.snapshot, cards.hideAmounts, cards.accountCount > 1, cards.metricsError?.message()) }
        }
    }
}

@Composable
private fun SnapshotTiles(snapshot: Snapshot?, hideAmounts: Boolean, summed: Boolean, metricsError: String?) {
    val locale = Locale.getDefault()
    if (snapshot == null) {
        BodyText(stringResource(R.string.home_error_not_available))
        return
    }
    val none = stringResource(R.string.home_not_available)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.lg)) {
        Tile(
            stringResource(R.string.home_subscribers), snapshot.subscribers?.let { compactCount(it, locale) } ?: none,
            { snapshot.subscribersNet?.let { ChangeText(it, { d -> signedCompact(d.toLong(), locale) }) } },
            Modifier.weight(1f),
        )
        Tile(
            stringResource(R.string.home_views_28), snapshot.views?.let { compactCount(it.current.toLong(), locale) } ?: none,
            { snapshot.views?.let { ChangeText(it, { d -> signedCompact(d.toLong(), locale) }) } },
            Modifier.weight(1f),
        )
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.lg)) {
        Tile(
            stringResource(R.string.home_watch_time), snapshot.watchHours?.let { stringResource(R.string.home_hours, compactCount(it.current.toLong(), locale)) } ?: none,
            { snapshot.watchHours?.let { ChangeText(it, { d -> stringResource(R.string.home_hours, signedCompact(d.toLong(), locale)) }) } },
            Modifier.weight(1f),
        )
        snapshot.earningsUsd?.let { earnings ->
            val money = NumberFormat.getCurrencyInstance(locale).apply { currency = Currency.getInstance("USD"); maximumFractionDigits = 0 }
            Tile(
                stringResource(R.string.home_earnings),
                if (hideAmounts) stringResource(R.string.home_hidden_amount) else money.format(earnings.current),
                { if (!hideAmounts) ChangeText(earnings, { d -> (if (d >= 0) "+" else "-") + money.format(kotlin.math.abs(d)) }) },
                Modifier.weight(1f),
            )
        } ?: Tile(stringResource(R.string.home_earnings), none, { Text(stringResource(R.string.home_earnings_note), style = CreatoraTheme.typography.caption, color = CreatoraTheme.colors.textMuted) }, Modifier.weight(1f))
    }
    BodyText(stringResource(if (summed) R.string.home_snapshot_note_all else R.string.home_snapshot_note))
    metricsError?.let { BodyText(it) }
}

private fun UploadStatus.dot() = when (this) {
    UploadStatus.GOOD -> "🟢"
    UploadStatus.AVERAGE -> "🟡"
    UploadStatus.POOR -> "🔴"
    UploadStatus.TOO_NEW, UploadStatus.UNKNOWN -> "⚪"
}

private fun UploadStatus.labelRes() = when (this) {
    UploadStatus.GOOD -> R.string.home_status_good
    UploadStatus.AVERAGE -> R.string.home_status_average
    UploadStatus.POOR -> R.string.home_status_poor
    UploadStatus.TOO_NEW -> R.string.home_status_too_new
    UploadStatus.UNKNOWN -> R.string.home_status_unknown
}

/** The last five uploads with views, CTR, retention and a status; tap one for the AI's explanation. */
@Composable
fun UploadsSection(cards: ChannelCardsState, onOpen: (UploadRow) -> Unit) {
    if (cards.phase != CardsPhase.READY) return
    val locale = Locale.getDefault()
    val none = stringResource(R.string.home_not_available)
    HomeSection(R.string.home_uploads_title) {
        if (cards.uploads.isEmpty()) {
            HomeCard { BodyText(stringResource(R.string.home_uploads_empty)) }
            return@HomeSection
        }
        HomeCard {
            cards.uploads.forEach { row ->
                val metrics = cards.uploadMetrics[row.video.id]
                Column(
                    Modifier.fillMaxWidth().heightIn(min = CreatoraTheme.dimens.minTouchTarget).clickable(role = Role.Button, onClickLabel = stringResource(R.string.home_uploads_why)) { onOpen(row) },
                    verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.xs),
                ) {
                    Text(row.video.title, style = CreatoraTheme.typography.cardTitle, color = CreatoraTheme.colors.text, maxLines = 1)
                    Text(
                        stringResource(
                            R.string.home_upload_numbers, row.status.dot(), compactCount(row.video.views, locale),
                            metrics?.ctrPercent?.let { String.format(locale, "%.1f%%", it) } ?: none,
                            metrics?.averageViewPercent?.let { String.format(locale, "%.0f%%", it) } ?: none,
                        ),
                        style = CreatoraTheme.typography.caption, color = CreatoraTheme.colors.textMuted,
                    )
                    Text(stringResource(row.status.labelRes()), style = CreatoraTheme.typography.caption, color = CreatoraTheme.colors.textMuted)
                }
            }
            if (cards.accountCount > 1) BodyText(stringResource(R.string.home_uploads_pick_one))
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
    HomeSection(R.string.home_streak_title) {
        HomeCard {
            Text(
                androidx.compose.ui.res.pluralStringResource(unitRes, streak.current, streak.current),
                style = CreatoraTheme.typography.sectionTitle, color = CreatoraTheme.colors.text,
            )
            BodyText(androidx.compose.ui.res.pluralStringResource(unitRes, streak.best, streak.best).let { stringResource(R.string.home_streak_best, it) })
            if (streak.atRisk) BodyText(stringResource(if (cards.streakUnit == StreakUnit.WEEKS) R.string.home_streak_reminder_week else R.string.home_streak_reminder_day))
            Row(horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
                ChoiceChip(stringResource(R.string.home_streak_weeks_chip), cards.streakUnit == StreakUnit.WEEKS, { onUnit(StreakUnit.WEEKS) })
                ChoiceChip(stringResource(R.string.home_streak_days_chip), cards.streakUnit == StreakUnit.DAYS, { onUnit(StreakUnit.DAYS) })
            }
            BodyText(stringResource(R.string.home_streak_note))
        }
    }
}
