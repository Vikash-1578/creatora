package com.creatora.app.ui.calendar

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.creatora.app.R
import com.creatora.app.domain.publish.PostStatus
import com.creatora.app.domain.publish.ScheduledPost
import com.creatora.app.domain.publish.canReschedule
import com.creatora.app.ui.accounts.labelRes
import com.creatora.app.ui.components.ChoiceChip
import com.creatora.app.ui.components.StatusChip
import com.creatora.app.ui.components.SubScreenScaffold
import com.creatora.app.ui.components.Tone
import com.creatora.app.ui.home.BodyText
import com.creatora.app.ui.home.HomeCard
import com.creatora.app.ui.theme.CreatoraTheme
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** The content calendar: month or week, scheduled and published posts, move to another day, retry and cancel. */
@Composable
fun CalendarScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: CalendarViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CalendarContent(
        state, onBack, modifier,
        CalendarActions(viewModel::setMode, viewModel::select, viewModel::step, viewModel::startMove, viewModel::cancelMove, viewModel::moveTo, viewModel::cancel, viewModel::retry, viewModel::dismissProblem),
    )
}

class CalendarActions(
    val mode: (CalendarMode) -> Unit = {}, val select: (LocalDate) -> Unit = {}, val step: (Int) -> Unit = {}, val startMove: (ScheduledPost) -> Unit = {},
    val cancelMove: () -> Unit = {}, val moveTo: (LocalDate) -> Unit = {}, val cancel: (ScheduledPost) -> Unit = {}, val retry: (ScheduledPost) -> Unit = {},
    val dismissProblem: () -> Unit = {},
)

@Composable
fun CalendarContent(state: CalendarUiState, onBack: () -> Unit, actions: CalendarActions, modifier: Modifier = Modifier) {
    val spacing = CreatoraTheme.spacing
    SubScreenScaffold(title = stringResource(R.string.calendar_title), onBack = onBack, modifier = modifier) {
        LazyColumn(Modifier.padding(horizontal = spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
            item { Header(state, actions) }
            item { Grid(state, actions) }
            state.moving?.let { moving ->
                item {
                    HomeCard {
                        BodyText(stringResource(R.string.calendar_moving, moving.title))
                        TextButton(onClick = actions.cancelMove, modifier = Modifier.heightIn(min = CreatoraTheme.dimens.minTouchTarget)) { Text(stringResource(R.string.action_cancel)) }
                    }
                }
            }
            state.problem?.let { problem ->
                item { Text(stringResource(problem.textRes()), style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.danger, modifier = Modifier.clickable { actions.dismissProblem() }) }
            }
            val day = state.byDay[state.selected].orEmpty()
            item { Text(DateUtils.formatDateTime(LocalContext.current, state.selected.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(), DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY), style = CreatoraTheme.typography.sectionTitle, color = CreatoraTheme.colors.text) }
            if (day.isEmpty()) item { HomeCard { BodyText(stringResource(R.string.calendar_empty_day)) } }
            items(day, key = { it.id }) { post -> PostCard(post, actions) }
        }
    }
}

@Composable
private fun Header(state: CalendarUiState, actions: CalendarActions) {
    Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { actions.step(-1) }, modifier = Modifier.size(CreatoraTheme.dimens.minTouchTarget)) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(R.string.calendar_previous), tint = CreatoraTheme.colors.text)
            }
            Text(
                "${state.month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${state.month.year}", style = CreatoraTheme.typography.sectionTitle,
                color = CreatoraTheme.colors.text, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { actions.step(1) }, modifier = Modifier.size(CreatoraTheme.dimens.minTouchTarget)) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(R.string.calendar_next), tint = CreatoraTheme.colors.text)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
            ChoiceChip(stringResource(R.string.calendar_month), state.mode == CalendarMode.MONTH, { actions.mode(CalendarMode.MONTH) })
            ChoiceChip(stringResource(R.string.calendar_week), state.mode == CalendarMode.WEEK, { actions.mode(CalendarMode.WEEK) })
        }
    }
}

@Composable
private fun Grid(state: CalendarUiState, actions: CalendarActions) {
    val colors = CreatoraTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.xs)) {
        state.weeks.firstOrNull()?.let { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    Text(day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()), Modifier.weight(1f), style = CreatoraTheme.typography.caption, color = colors.textMuted, textAlign = TextAlign.Center)
                }
            }
        }
        state.weeks.forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.xs)) {
                week.forEach { day ->
                    val posts = state.byDay[day].orEmpty()
                    val selected = day == state.selected
                    val shape = CreatoraTheme.shapes.radius12
                    Column(
                        Modifier.weight(1f).heightIn(min = CreatoraTheme.dimens.minTouchTarget + CreatoraTheme.spacing.md).clip(shape)
                            .background(if (selected) colors.accentSoft else colors.surface)
                            .border(CreatoraTheme.dimens.borderWidth, if (day == state.today) colors.accent else colors.border, shape)
                            .clickable(role = Role.Button) { if (state.moving != null) actions.moveTo(day) else actions.select(day) }
                            .padding(CreatoraTheme.spacing.xs),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            day.dayOfMonth.toString(), style = CreatoraTheme.typography.caption,
                            color = if (day.month == state.month.month) colors.text else colors.textMuted,
                        )
                        if (posts.isNotEmpty()) {
                            Box(Modifier.size(CreatoraTheme.spacing.sm).clip(CreatoraTheme.shapes.radius8).background(colors.accent))
                            if (posts.size > 1) Text("+${posts.size - 1}", style = CreatoraTheme.typography.caption, color = colors.textMuted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PostCard(post: ScheduledPost, actions: CalendarActions) {
    val context = LocalContext.current
    HomeCard {
        Text(post.title.ifBlank { stringResource(R.string.home_untitled) }, style = CreatoraTheme.typography.cardTitle, color = CreatoraTheme.colors.text, maxLines = 2)
        BodyText(
            stringResource(
                R.string.home_upcoming_when, stringResource(post.platform.labelRes()),
                DateUtils.formatDateTime(context, post.scheduledAt ?: post.publishedAt ?: 0L, DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_ALL),
            ),
        )
        StatusChip(stringResource(post.status.textRes()), post.status.tone())
        post.error?.let { Text(stringResource(R.string.calendar_failed_reason, it), style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.danger) }
        if (post.status == PostStatus.AWAITING_PUBLIC) BodyText(stringResource(R.string.poststatus_awaiting_help))
        if (post.status == PostStatus.SHARED) BodyText(stringResource(R.string.poststatus_shared_help))
        post.views24h?.let { views ->
            BodyText(stringResource(R.string.calendar_views_so_far, views, DateUtils.formatDateTime(context, post.viewsAsOf ?: 0L, DateUtils.FORMAT_SHOW_TIME)))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
            post.remoteUrl?.let { url -> TextButton({ openUrl(context, url) }, Modifier.heightIn(min = CreatoraTheme.dimens.minTouchTarget)) { Text(stringResource(R.string.calendar_open)) } }
            if (post.status == PostStatus.AWAITING_PUBLIC && post.remoteId != null) {
                TextButton({ openUrl(context, "https://studio.youtube.com/video/${post.remoteId}/edit") }, Modifier.heightIn(min = CreatoraTheme.dimens.minTouchTarget)) { Text(stringResource(R.string.calendar_make_public)) }
            }
            if (canReschedule(post, System.currentTimeMillis())) {
                TextButton({ actions.startMove(post) }, Modifier.heightIn(min = CreatoraTheme.dimens.minTouchTarget)) { Text(stringResource(R.string.calendar_move)) }
            }
            if (post.status == PostStatus.FAILED) TextButton({ actions.retry(post) }, Modifier.heightIn(min = CreatoraTheme.dimens.minTouchTarget)) { Text(stringResource(R.string.action_try_again)) }
            if (post.status != PostStatus.LIVE && post.status != PostStatus.SHARED) {
                TextButton({ actions.cancel(post) }, Modifier.heightIn(min = CreatoraTheme.dimens.minTouchTarget)) { Text(stringResource(R.string.calendar_cancel)) }
            }
        }
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

fun PostStatus.textRes() = when (this) {
    PostStatus.QUEUED -> R.string.poststatus_queued
    PostStatus.SCHEDULED -> R.string.poststatus_scheduled
    PostStatus.UPLOADING -> R.string.poststatus_uploading
    PostStatus.PROCESSING -> R.string.poststatus_processing
    PostStatus.LIVE -> R.string.poststatus_live
    PostStatus.FAILED -> R.string.poststatus_failed
    PostStatus.AWAITING_PUBLIC -> R.string.poststatus_awaiting
    PostStatus.SHARED -> R.string.poststatus_shared
}

fun PostStatus.tone() = when (this) {
    PostStatus.LIVE -> Tone.GOOD
    PostStatus.FAILED -> Tone.BAD
    PostStatus.AWAITING_PUBLIC, PostStatus.SHARED -> Tone.WARN
    else -> Tone.NEUTRAL
}

private fun CalendarProblem.textRes() = when (this) {
    CalendarProblem.PAST -> R.string.calendar_problem_past
    CalendarProblem.CANNOT_MOVE -> R.string.calendar_problem_cannot_move
    CalendarProblem.PLATFORM_REFUSED -> R.string.calendar_problem_platform
    CalendarProblem.SAVE_FAILED -> R.string.calendar_problem_save
    CalendarProblem.OPEN_IN_YOUTUBE -> R.string.calendar_problem_open_youtube
}

@Preview(name = "Calendar light", showBackground = true, heightDp = 900)
@Composable
private fun CalendarLightPreview() {
    CreatoraTheme(darkTheme = false) { CalendarContent(CalendarUiState(weeks = com.creatora.app.domain.publish.monthGrid(LocalDate.now(), java.time.DayOfWeek.MONDAY)), {}, CalendarActions()) }
}

@Preview(name = "Calendar dark", showBackground = true, heightDp = 900)
@Composable
private fun CalendarDarkPreview() {
    CreatoraTheme(darkTheme = true) { CalendarContent(CalendarUiState(weeks = com.creatora.app.domain.publish.monthGrid(LocalDate.now(), java.time.DayOfWeek.MONDAY)), {}, CalendarActions()) }
}
