package com.creatora.app.ui.research

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.creatora.app.R
import com.creatora.app.domain.account.VideoStats
import com.creatora.app.domain.home.ParsedIdea
import com.creatora.app.ui.components.SecondaryButton
import com.creatora.app.ui.home.BodyText
import com.creatora.app.ui.home.HomeCard
import com.creatora.app.ui.home.LoadingRow
import com.creatora.app.ui.home.TrendingSection
import com.creatora.app.ui.home.TrendingState
import com.creatora.app.ui.home.TrendingViewModel
import com.creatora.app.ui.theme.CreatoraTheme

class TrendsActions(
    val open: (String) -> Unit = {},
    val useAsIdea: (VideoStats) -> Unit = {},
    val refreshTrending: () -> Unit = {},
    val suggest: () -> Unit = {},
    val save: (ParsedIdea) -> Unit = {},
    val create: (ParsedIdea) -> Unit = {},
)

@Composable
fun TrendsTab(
    onOpenProject: (Long) -> Unit,
    viewModel: TrendsViewModel = hiltViewModel(),
    trendingViewModel: TrendingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    TrendsContent(
        trending = trendingViewModel.state.collectAsStateWithLifecycle().value,
        ui = viewModel.ui.collectAsStateWithLifecycle().value,
        actions = TrendsActions(
            open = { openVideo(context, it) }, useAsIdea = trendingViewModel::useAsIdea, refreshTrending = trendingViewModel::refresh,
            suggest = viewModel::suggest, save = viewModel::save, create = { viewModel.create(it, onOpenProject) },
        ),
    )
}

@Composable
fun TrendsContent(trending: TrendingState, ui: TrendsUi, actions: TrendsActions) {
    Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.md)) {
        TrendingSection(trending, { actions.open(it.id) }, actions.useAsIdea, actions.refreshTrending)

        SectionLabel(R.string.research_rising)
        MessageCard(R.string.research_rising_empty)

        SectionLabel(R.string.research_competitor_outliers)
        HomeCard {
            val outliers = ui.outliers
            when {
                outliers == null -> LoadingRow(R.string.research_loading)
                !ui.hasCompetitors -> BodyText(stringResource(R.string.research_outliers_no_competitors))
                outliers.isEmpty() -> BodyText(stringResource(R.string.research_outliers_none))
                else -> {
                    outliers.take(MAX_OUTLIERS).forEach { item ->
                        VideoLine(item.video.copy(channelTitle = item.competitor)) { actions.open(item.video.id) }
                    }
                    Tag(R.string.research_estimate)
                }
            }
            BodyText(stringResource(R.string.research_outliers_note))
        }

        SectionLabel(R.string.research_next)
        HomeCard {
            when (val next = ui.next) {
                NextState.Idle -> {
                    BodyText(stringResource(R.string.research_next_body))
                    SecondaryButton(stringResource(R.string.research_next_button), actions.suggest)
                }
                NextState.Loading -> LoadingRow(R.string.research_thinking)
                NextState.NoSources -> BodyText(stringResource(R.string.research_next_no_sources))
                is NextState.Failed -> {
                    AiProblem(next.errors)
                    SecondaryButton(stringResource(R.string.action_try_again), actions.suggest)
                }
                is NextState.Ready -> next.ideas.forEach { idea -> IdeaRow(idea, idea.title in ui.saved, { actions.save(idea) }, { actions.create(idea) }) }
            }
        }
    }
}

private const val MAX_OUTLIERS = 5

@Composable
private fun TrendsPreview(dark: Boolean) {
    CreatoraTheme(darkTheme = dark) {
        PreviewColumn {
            TrendsContent(
                trending = TrendingState.Ready(
                    listOf(sampleVideo("t", "Monsoon street food tour", 820_000)), "IN", "street food", System.currentTimeMillis() - 3_600_000, fromCache = true, stale = false,
                ),
                ui = TrendsUi(
                    outliers = listOf(RecentOutlier(sampleVideo("o", "5 chaats under 50 rupees", 120_000), 3.1, "Dal Diaries")), hasCompetitors = true,
                    next = NextState.Ready(listOf(ParsedIdea("Monsoon chaat under 50 rupees", 64, "Trending: monsoon street food; competitor: 5 chaats video"))),
                ),
                actions = TrendsActions(),
            )
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun TrendsLightPreview() = TrendsPreview(dark = false)

@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TrendsDarkPreview() = TrendsPreview(dark = true)
