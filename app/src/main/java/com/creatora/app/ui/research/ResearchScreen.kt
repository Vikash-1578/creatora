package com.creatora.app.ui.research

import android.content.res.Configuration
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.creatora.app.R
import com.creatora.app.domain.research.Competition
import com.creatora.app.domain.research.Competitor
import com.creatora.app.domain.research.Outlier
import com.creatora.app.domain.research.YtChannel
import com.creatora.app.domain.research.YtError
import com.creatora.app.domain.research.YtVideo
import com.creatora.app.ui.components.ChoiceChip
import com.creatora.app.ui.components.EmptyState
import com.creatora.app.ui.components.ListGroup
import com.creatora.app.ui.components.ListRowDivider
import com.creatora.app.ui.components.NavigationListRow
import com.creatora.app.ui.components.PrimaryButton
import com.creatora.app.ui.components.ScreenHeader
import com.creatora.app.ui.components.SecondaryButton
import com.creatora.app.ui.components.TabScreen
import com.creatora.app.ui.theme.CreatoraTheme

@Composable
fun ResearchScreen(
    onOpenKeys: () -> Unit,
    onOpenStudy: () -> Unit,
    onOpenSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ResearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    state.notice?.let { notice ->
        val text = stringResource(
            when (notice) {
                ResearchNotice.SAVED_TO_SWIPE -> R.string.research_notice_swipe
                ResearchNotice.SAVED_AS_IDEA -> R.string.research_notice_idea
                ResearchNotice.ALREADY_TRACKED -> R.string.research_notice_tracked
            },
        )
        LaunchedEffect(notice) {
            snackbar.showSnackbar(text)
            viewModel.noticeShown()
        }
    }
    Box(modifier) {
        ResearchContent(
            state = state,
            onQuery = viewModel::setQuery,
            onSearch = viewModel::search,
            onTab = viewModel::selectTab,
            onTrack = viewModel::track,
            onUntrack = viewModel::untrack,
            onRefresh = viewModel::refreshCompetitors,
            onSwipe = viewModel::saveToSwipeFile,
            onIdea = viewModel::saveAsIdea,
            onOpenKeys = onOpenKeys,
            onOpenStudy = onOpenStudy,
            onOpenSaved = onOpenSaved,
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
fun ResearchContent(
    state: ResearchUiState,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onTab: (ResearchTab) -> Unit,
    onTrack: (YtChannel) -> Unit,
    onUntrack: (Competitor) -> Unit,
    onRefresh: () -> Unit,
    onSwipe: (YtVideo) -> Unit,
    onIdea: (YtVideo) -> Unit,
    onOpenKeys: () -> Unit,
    onOpenStudy: () -> Unit,
    onOpenSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = CreatoraTheme.spacing
    TabScreen(modifier = modifier) {
        ScreenHeader(stringResource(R.string.tab_research), stringResource(R.string.research_subtitle))
        Column(Modifier.padding(horizontal = spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            if (!state.hasKey) {
                EmptyState(
                    icon = Icons.Outlined.Search,
                    title = stringResource(R.string.research_no_key_title),
                    body = stringResource(R.string.research_no_key_body),
                    actionLabel = stringResource(R.string.research_add_key),
                    onAction = onOpenKeys,
                )
            } else {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQuery,
                    placeholder = { Text(stringResource(state.tab.hintRes())) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    ResearchTab.entries.forEach { tab -> ChoiceChip(stringResource(tab.labelRes()), state.tab == tab, { onTab(tab) }) }
                }
                if (state.loading) CircularProgressIndicator(color = CreatoraTheme.colors.accent)
                state.error?.let { ErrorText(it, onOpenKeys) }
                when (state.tab) {
                    ResearchTab.KEYWORDS -> KeywordsTab(state, onSwipe, onIdea)
                    ResearchTab.OUTLIERS -> OutliersTab(state, onSwipe, onIdea)
                    ResearchTab.RIVALS -> RivalsTab(state, onTrack, onUntrack, onRefresh)
                    ResearchTab.TRENDS -> TrendsTab(state, onSwipe, onIdea)
                }
            }
            ListGroup(Modifier.padding(bottom = spacing.lg)) {
                NavigationListRow(stringResource(R.string.research_study), onOpenStudy)
                ListRowDivider()
                NavigationListRow(stringResource(R.string.research_saved), onOpenSaved)
            }
        }
    }
}

@Composable
private fun KeywordsTab(state: ResearchUiState, onSwipe: (YtVideo) -> Unit, onIdea: (YtVideo) -> Unit) {
    val result = state.keywords
    if (result == null) {
        Hint(stringResource(R.string.research_keywords_hint))
        return
    }
    ListGroup {
        Column(Modifier.padding(CreatoraTheme.spacing.lg), verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(result.query, style = CreatoraTheme.typography.cardTitle, color = CreatoraTheme.colors.text, modifier = Modifier.weight(1f))
                CompetitionTag(result.competition)
            }
            Text(
                stringResource(R.string.research_match_count, compactCount(result.totalResults)),
                style = CreatoraTheme.typography.body,
                color = CreatoraTheme.colors.textMuted,
            )
            Text(stringResource(R.string.research_competition_note), style = CreatoraTheme.typography.caption, color = CreatoraTheme.colors.textMuted)
        }
    }
    result.videos.forEach { video -> VideoRow(video, videoActions(video, { onSwipe(video) }, { onIdea(video) })) }
}

@Composable
private fun OutliersTab(state: ResearchUiState, onSwipe: (YtVideo) -> Unit, onIdea: (YtVideo) -> Unit) {
    val outliers = state.outliers
    when {
        outliers == null -> Hint(stringResource(R.string.research_outliers_hint))
        outliers.isEmpty() -> Hint(stringResource(R.string.research_outliers_none))
        else -> outliers.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.md)) {
                pair.forEach { outlier: Outlier ->
                    OutlierCard(outlier, videoActions(outlier.video, { onSwipe(outlier.video) }, { onIdea(outlier.video) }), Modifier.weight(1f))
                }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RivalsTab(state: ResearchUiState, onTrack: (YtChannel) -> Unit, onUntrack: (Competitor) -> Unit, onRefresh: () -> Unit) {
    val colors = CreatoraTheme.colors
    state.foundChannel?.let { channel ->
        ListGroup {
            Column(Modifier.padding(CreatoraTheme.spacing.lg), verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
                Text(channel.title, style = CreatoraTheme.typography.cardTitle, color = colors.text)
                Text(channelStats(channel.subscribers, channel.videos, channel.averageViews), style = CreatoraTheme.typography.body, color = colors.textMuted)
                PrimaryButton(stringResource(R.string.research_track), { onTrack(channel) })
            }
        }
    }
    if (state.channelSearched && state.foundChannel == null) Hint(stringResource(R.string.research_channel_none))
    if (!state.channelSearched && state.competitors.isEmpty()) Hint(stringResource(R.string.research_rivals_hint))
    if (state.competitors.isNotEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.research_tracked), style = CreatoraTheme.typography.sectionTitle, color = colors.text, modifier = Modifier.weight(1f))
            SecondaryButton(stringResource(R.string.research_refresh), onRefresh)
        }
        ListGroup {
            state.competitors.forEachIndexed { index, competitor ->
                if (index > 0) ListRowDivider()
                Row(Modifier.padding(start = CreatoraTheme.spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(vertical = CreatoraTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.xs)) {
                        Text(competitor.title, style = CreatoraTheme.typography.bodyBold, color = colors.text)
                        Text(
                            channelStats(competitor.subscribers, competitor.videos, if (competitor.videos > 0) competitor.views / competitor.videos else 0),
                            style = CreatoraTheme.typography.caption,
                            color = colors.textMuted,
                        )
                    }
                    SecondaryButton(stringResource(R.string.action_remove), { onUntrack(competitor) }, Modifier.padding(CreatoraTheme.spacing.sm))
                }
            }
        }
    }
}

@Composable
private fun TrendsTab(state: ResearchUiState, onSwipe: (YtVideo) -> Unit, onIdea: (YtVideo) -> Unit) {
    val trending = state.trending
    if (trending == null) {
        if (!state.loading && state.error == null) Hint(stringResource(R.string.research_trends_hint))
        return
    }
    Text(stringResource(R.string.research_trends_title), style = CreatoraTheme.typography.sectionTitle, color = CreatoraTheme.colors.text)
    trending.forEach { video -> VideoRow(video, videoActions(video, { onSwipe(video) }, { onIdea(video) })) }
}

@Composable
private fun channelStats(subscribers: Long?, videos: Long, average: Long): String = stringResource(
    R.string.research_channel_stats,
    subscribers?.let { compactCount(it) } ?: stringResource(R.string.research_hidden),
    compactCount(videos),
    compactCount(average),
)

@Composable
private fun Hint(text: String) {
    Text(text, style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.textMuted)
}

@Composable
fun ErrorText(error: YtError, onOpenKeys: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
        Text(stringResource(error.messageRes()), style = CreatoraTheme.typography.bodySemiBold, color = CreatoraTheme.colors.danger)
        if (error == YtError.NoKey || error == YtError.InvalidKey) SecondaryButton(stringResource(R.string.research_add_key), onOpenKeys)
    }
}

fun YtError.messageRes(): Int = when (this) {
    YtError.NoKey -> R.string.yt_error_no_key
    YtError.InvalidKey -> R.string.yt_error_invalid_key
    YtError.QuotaExceeded -> R.string.yt_error_quota
    YtError.Network -> R.string.yt_error_network
    is YtError.Server -> R.string.yt_error_server
    is YtError.BadRequest -> R.string.yt_error_bad_request
    YtError.BadResponse -> R.string.yt_error_bad_response
}

private fun ResearchTab.labelRes() = when (this) {
    ResearchTab.KEYWORDS -> R.string.research_tab_keywords
    ResearchTab.OUTLIERS -> R.string.research_tab_outliers
    ResearchTab.RIVALS -> R.string.research_tab_rivals
    ResearchTab.TRENDS -> R.string.research_tab_trends
}

private fun ResearchTab.hintRes() = when (this) {
    ResearchTab.RIVALS -> R.string.research_search_channel
    else -> R.string.research_search_keyword
}

private val PreviewVideo = YtVideo("abc", "A video title", "c1", "A channel", 120_000, null)

@Composable
private fun ResearchPreview(darkTheme: Boolean, state: ResearchUiState) {
    CreatoraTheme(darkTheme = darkTheme) { ResearchContent(state, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}) }
}

@Preview(name = "No key, light", showBackground = true)
@Composable
private fun ResearchNoKeyLightPreview() = ResearchPreview(false, ResearchUiState())

@Preview(name = "Keywords, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ResearchKeywordsDarkPreview() = ResearchPreview(
    true,
    ResearchUiState(hasKey = true, keywords = KeywordResult("camera tips", 54_000, Competition.MEDIUM, listOf(PreviewVideo))),
)
