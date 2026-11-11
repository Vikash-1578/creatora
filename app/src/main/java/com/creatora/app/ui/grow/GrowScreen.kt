package com.creatora.app.ui.grow

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.creatora.app.R
import com.creatora.app.domain.grow.AbTest
import com.creatora.app.domain.grow.CommentDraft
import com.creatora.app.domain.grow.Delta
import com.creatora.app.domain.grow.DraftStatus
import com.creatora.app.domain.grow.TestKind
import com.creatora.app.domain.grow.TestVariant
import com.creatora.app.domain.grow.dailyViewGains
import com.creatora.app.domain.grow.deltaOver
import com.creatora.app.domain.grow.pendingCount
import com.creatora.app.domain.model.ChannelSnapshot
import com.creatora.app.ui.ai.AiErrors
import com.creatora.app.ui.components.ChoiceChip
import com.creatora.app.ui.components.EmptyState
import com.creatora.app.ui.components.ListGroup
import com.creatora.app.ui.components.ListRowDivider
import com.creatora.app.ui.components.NavigationListRow
import com.creatora.app.ui.components.PrimaryButton
import com.creatora.app.ui.components.ScreenHeader
import com.creatora.app.ui.components.SecondaryButton
import com.creatora.app.ui.components.TabScreen
import com.creatora.app.ui.research.ErrorText
import com.creatora.app.ui.research.compactCount
import com.creatora.app.ui.theme.CreatoraTheme
import java.util.TimeZone

@Composable
fun GrowScreen(
    onOpenKeys: () -> Unit,
    onProjectStarted: (projectId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GrowViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.hasKey, state.channel?.id, state.snapshots.lastOrNull()?.capturedAt) { viewModel.captureIfDue() }
    GrowContent(
        state = state,
        onTab = viewModel::selectTab,
        onPeriod = viewModel::setPeriod,
        onConnect = viewModel::connect,
        onDisconnect = viewModel::disconnect,
        onRefresh = viewModel::refresh,
        onSuggestNext = viewModel::suggestNext,
        onStartNext = { viewModel.startSuggestion(onProjectStarted) },
        onDraftReply = viewModel::draftReply,
        onDraftStatus = viewModel::setDraftStatus,
        onDeleteDraft = viewModel::deleteDraft,
        onCreateTest = viewModel::createTest,
        onWinner = viewModel::setWinner,
        onDeleteTest = viewModel::deleteTest,
        onOpenKeys = onOpenKeys,
        modifier = modifier,
    )
}

@Composable
fun GrowContent(
    state: GrowUiState,
    onTab: (GrowTab) -> Unit,
    onPeriod: (Int) -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit,
    onSuggestNext: () -> Unit,
    onStartNext: () -> Unit,
    onDraftReply: (String, ReplyTone) -> Unit,
    onDraftStatus: (CommentDraft, DraftStatus) -> Unit,
    onDeleteDraft: (CommentDraft) -> Unit,
    onCreateTest: (String, TestKind, String, String, String) -> Unit,
    onWinner: (AbTest, TestVariant?) -> Unit,
    onDeleteTest: (AbTest) -> Unit,
    onOpenKeys: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = CreatoraTheme.spacing
    TabScreen(modifier = modifier) {
        ScreenHeader(stringResource(R.string.tab_grow), stringResource(R.string.grow_subtitle))
        Column(Modifier.padding(horizontal = spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                GrowTab.entries.forEach { tab -> ChoiceChip(stringResource(tab.labelRes()), state.tab == tab, { onTab(tab) }) }
            }
            when {
                state.tab == GrowTab.COMMENTS -> CommentsTab(state, onDraftReply, onDraftStatus, onDeleteDraft, onOpenKeys)
                state.tab == GrowTab.TESTS -> TestsTab(state, onCreateTest, onWinner, onDeleteTest)
                !state.hasKey -> EmptyState(
                    icon = Icons.Outlined.TrendingUp,
                    title = stringResource(R.string.grow_no_key_title),
                    body = stringResource(R.string.grow_no_key_body),
                    actionLabel = stringResource(R.string.research_add_key),
                    onAction = onOpenKeys,
                )
                state.channel == null -> ConnectChannel(state, onConnect, onOpenKeys)
                else -> OverviewTab(state, onPeriod, onRefresh, onDisconnect, onSuggestNext, onStartNext, onTab, onOpenKeys)
            }
        }
    }
}

private fun GrowTab.labelRes() = when (this) {
    GrowTab.OVERVIEW -> R.string.grow_tab_overview
    GrowTab.COMMENTS -> R.string.grow_tab_comments
    GrowTab.TESTS -> R.string.grow_tab_tests
}

@Composable
private fun ConnectChannel(state: GrowUiState, onConnect: (String) -> Unit, onOpenKeys: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    Text(stringResource(R.string.grow_connect_title), style = CreatoraTheme.typography.sectionTitle, color = CreatoraTheme.colors.text)
    Text(stringResource(R.string.grow_connect_body), style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.textMuted)
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        label = { Text(stringResource(R.string.research_search_channel)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    PrimaryButton(stringResource(R.string.grow_connect), { onConnect(query) }, enabled = query.isNotBlank() && !state.loading)
    if (state.loading) CircularProgressIndicator(color = CreatoraTheme.colors.accent)
    if (state.channelNotFound) Text(stringResource(R.string.research_channel_none), style = CreatoraTheme.typography.bodySemiBold, color = CreatoraTheme.colors.danger)
    state.error?.let { ErrorText(it, onOpenKeys) }
}

@Composable
private fun OverviewTab(
    state: GrowUiState,
    onPeriod: (Int) -> Unit,
    onRefresh: () -> Unit,
    onDisconnect: () -> Unit,
    onSuggestNext: () -> Unit,
    onStartNext: () -> Unit,
    onTab: (GrowTab) -> Unit,
    onOpenKeys: () -> Unit,
) {
    val colors = CreatoraTheme.colors
    val spacing = CreatoraTheme.spacing
    val days = state.periodDays

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(state.channel?.title.orEmpty(), style = CreatoraTheme.typography.sectionTitle, color = colors.text, modifier = Modifier.weight(1f))
        SecondaryButton(stringResource(R.string.grow_refresh), onRefresh)
    }
    if (state.loading) CircularProgressIndicator(color = colors.accent)
    state.error?.let { ErrorText(it, onOpenKeys) }
    state.snapshots.lastOrNull()?.let {
        Text(
            stringResource(R.string.grow_updated, DateUtils.getRelativeTimeSpanString(it.capturedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()),
            style = CreatoraTheme.typography.caption,
            color = colors.textMuted,
        )
    }

    NextCard(state.next, onSuggestNext, onStartNext, onOpenKeys)

    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
        listOf(7, 28, 90).forEach { option -> ChoiceChip(stringResource(R.string.grow_last_days, option), days == option, { onPeriod(option) }) }
    }

    val snapshots = state.snapshots
    val stats = listOf(
        Triple(R.string.grow_subscribers, deltaOver(snapshots, days) { it.subscriberCount.takeIf { count -> count > 0 } }, false),
        Triple(R.string.grow_views, deltaOver(snapshots, days) { it.viewCount }, false),
        Triple(R.string.grow_videos, deltaOver(snapshots, days) { it.videoCount }, false),
        Triple(R.string.grow_average_views, deltaOver(snapshots, days) { it.viewCount.takeIf { _ -> it.videoCount > 0 }?.div(it.videoCount) }, false),
    )
    stats.chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            pair.forEach { (label, delta, _) -> StatCard(stringResource(label), delta, days, Modifier.weight(1f)) }
        }
    }

    ChartCard(snapshots, days)

    ListGroup {
        NavigationListRow(
            title = stringResource(R.string.grow_reply_drafts),
            value = state.drafts.pendingCount().toString(),
            onClick = { onTab(GrowTab.COMMENTS) },
        )
        ListRowDivider()
        NavigationListRow(
            title = stringResource(R.string.grow_tests_running),
            value = state.tests.count { it.winner == null }.toString(),
            onClick = { onTab(GrowTab.TESTS) },
        )
    }
    SecondaryButton(stringResource(R.string.grow_disconnect), onDisconnect)
}

@Composable
private fun NextCard(next: NextSuggestion, onSuggest: () -> Unit, onStart: () -> Unit, onOpenKeys: () -> Unit) {
    val colors = CreatoraTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CreatoraTheme.shapes.radius16)
            .background(colors.accentSoft)
            .padding(CreatoraTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm),
    ) {
        Text(stringResource(R.string.grow_make_next), style = CreatoraTheme.typography.caption, color = colors.accentText)
        val topic = next.topic
        if (topic != null) {
            Text(topic.topic, style = CreatoraTheme.typography.cardTitle, color = colors.text)
            if (topic.reason.isNotBlank()) Text(topic.reason, style = CreatoraTheme.typography.body, color = colors.textMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
                PrimaryButton(stringResource(R.string.grow_start), onStart)
                SecondaryButton(stringResource(R.string.grow_another), onSuggest)
            }
        } else {
            Text(stringResource(R.string.grow_make_next_body), style = CreatoraTheme.typography.body, color = colors.textMuted)
            if (next.loading) CircularProgressIndicator(color = colors.accent) else PrimaryButton(stringResource(R.string.grow_suggest), onSuggest)
            next.youTubeError?.let { ErrorText(it, onOpenKeys) }
            next.aiErrors?.let { AiErrors(it, onOpenKeys) }
        }
    }
}

@Composable
internal fun StatCard(label: String, delta: Delta?, days: Int, modifier: Modifier = Modifier) {
    val colors = CreatoraTheme.colors
    val shape = CreatoraTheme.shapes.radius16
    Column(
        modifier = modifier
            .clip(shape)
            .background(colors.surface)
            .border(CreatoraTheme.dimens.borderWidth, colors.border, shape)
            .padding(CreatoraTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.xs),
    ) {
        Text(label, style = CreatoraTheme.typography.caption, color = colors.textMuted)
        Text(delta?.let { compactCount(it.current) } ?: "—", style = CreatoraTheme.typography.screenTitle, color = colors.text)
        val change = delta?.change
        val (text, color) = when {
            change == null -> stringResource(R.string.grow_no_history) to colors.textMuted
            change > 0 -> stringResource(R.string.grow_up, compactCount(change), days) to colors.success
            change < 0 -> stringResource(R.string.grow_down, compactCount(-change), days) to colors.danger
            else -> stringResource(R.string.grow_no_change, days) to colors.textMuted
        }
        Text(text, style = CreatoraTheme.typography.caption, color = color)
    }
}

@Composable
private fun ChartCard(snapshots: List<ChannelSnapshot>, days: Int) {
    val colors = CreatoraTheme.colors
    val shape = CreatoraTheme.shapes.radius16
    val context = LocalContext.current
    val gains = dailyViewGains(snapshots, days, System.currentTimeMillis(), TimeZone.getDefault())
    val flags = if (days <= 7) DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY else DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NO_YEAR or DateUtils.FORMAT_ABBREV_ALL
    val bars = gains.map { Bar(it.views, DateUtils.formatDateTime(context, it.dayStartMillis, flags).let { label -> if (days <= 7) label else label.substringBefore(' ').ifEmpty { label } }) }
    val title = stringResource(R.string.grow_views_gained)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(CreatoraTheme.dimens.borderWidth, colors.border, shape)
            .padding(CreatoraTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.md),
    ) {
        Text(title, style = CreatoraTheme.typography.cardTitle, color = colors.text)
        if (gains.all { it.views == null }) {
            Text(stringResource(R.string.grow_chart_needs_history), style = CreatoraTheme.typography.body, color = colors.textMuted)
        } else {
            BarChart(bars, chartDescription(title, bars))
        }
    }
}

@Composable
private fun CommentsTab(
    state: GrowUiState,
    onDraftReply: (String, ReplyTone) -> Unit,
    onDraftStatus: (CommentDraft, DraftStatus) -> Unit,
    onDelete: (CommentDraft) -> Unit,
    onOpenKeys: () -> Unit,
) {
    val colors = CreatoraTheme.colors
    val context = LocalContext.current
    var comment by rememberSaveable { mutableStateOf("") }
    var tone by rememberSaveable { mutableStateOf(ReplyTone.FRIENDLY) }
    Text(stringResource(R.string.grow_comments_intro), style = CreatoraTheme.typography.body, color = colors.textMuted)
    OutlinedTextField(
        value = comment,
        onValueChange = { comment = it },
        label = { Text(stringResource(R.string.grow_comment_label)) },
        minLines = 3,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
        ReplyTone.entries.forEach { ChoiceChip(stringResource(it.labelRes()), tone == it, { tone = it }) }
    }
    PrimaryButton(
        text = stringResource(if (state.replying) R.string.wizard_ai_working else R.string.grow_draft_reply),
        onClick = { onDraftReply(comment, tone); comment = "" },
        enabled = comment.isNotBlank() && !state.replying,
    )
    state.replyErrors?.let { AiErrors(it, onOpenKeys) }

    val pending = state.drafts.filter { it.status == DraftStatus.PENDING }
    if (pending.isEmpty() && state.drafts.isEmpty()) {
        EmptyState(Icons.Outlined.TrendingUp, stringResource(R.string.grow_drafts_empty_title), stringResource(R.string.grow_drafts_empty_body))
    }
    if (pending.isNotEmpty()) {
        Text(
            pluralStringResource(R.plurals.grow_drafts_waiting, pending.size, pending.size),
            style = CreatoraTheme.typography.sectionTitle,
            color = colors.text,
        )
    }
    pending.forEach { draft ->
        DraftCard(draft) {
            PrimaryButton(stringResource(R.string.grow_approve_copy), {
                copyToClipboard(context, draft.reply)
                onDraftStatus(draft, DraftStatus.APPROVED)
            })
            SecondaryButton(stringResource(R.string.grow_dismiss), { onDraftStatus(draft, DraftStatus.DISMISSED) })
        }
    }
    val done = state.drafts.filter { it.status != DraftStatus.PENDING }
    if (done.isNotEmpty()) {
        Text(stringResource(R.string.grow_drafts_done), style = CreatoraTheme.typography.sectionTitle, color = colors.text)
        done.forEach { draft ->
            DraftCard(draft) {
                Text(
                    stringResource(if (draft.status == DraftStatus.APPROVED) R.string.grow_approved else R.string.grow_dismissed),
                    style = CreatoraTheme.typography.caption,
                    color = if (draft.status == DraftStatus.APPROVED) colors.success else colors.textMuted,
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton(stringResource(R.string.action_delete), { onDelete(draft) })
            }
        }
    }
}

@Composable
private fun DraftCard(draft: CommentDraft, actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val colors = CreatoraTheme.colors
    val shape = CreatoraTheme.shapes.radius16
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(CreatoraTheme.dimens.borderWidth, colors.border, shape)
            .padding(CreatoraTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm),
    ) {
        Text(draft.comment, style = CreatoraTheme.typography.caption, color = colors.textMuted)
        Text(draft.reply, style = CreatoraTheme.typography.body, color = colors.text)
        Row(horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

private fun ReplyTone.labelRes() = when (this) {
    ReplyTone.FRIENDLY -> R.string.grow_tone_friendly
    ReplyTone.SHORT -> R.string.grow_tone_short
    ReplyTone.FUNNY -> R.string.grow_tone_funny
}

private fun copyToClipboard(context: Context, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("reply", text))
}

private val PreviewState = GrowUiState(hasKey = true)

@Composable
private fun GrowPreview(darkTheme: Boolean, state: GrowUiState) {
    CreatoraTheme(darkTheme = darkTheme) {
        GrowContent(state, {}, {}, {}, {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {}, { _, _, _, _, _ -> }, { _, _ -> }, {}, {})
    }
}

@Preview(name = "Connect, light", showBackground = true)
@Composable
private fun GrowConnectLightPreview() = GrowPreview(false, PreviewState)

@Preview(name = "No key, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GrowNoKeyDarkPreview() = GrowPreview(true, GrowUiState())
