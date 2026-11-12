package com.creatora.app.ui.research

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.creatora.app.R
import com.creatora.app.domain.account.VideoStats
import com.creatora.app.domain.home.ParsedIdea
import com.creatora.app.domain.research.AgeBand
import com.creatora.app.domain.research.ScoredVideo
import com.creatora.app.ui.components.SecondaryButton
import com.creatora.app.ui.home.BodyText
import com.creatora.app.ui.home.HomeCard
import com.creatora.app.ui.home.LoadingRow
import com.creatora.app.ui.home.compactCount
import com.creatora.app.ui.home.message
import com.creatora.app.ui.theme.CreatoraTheme
import java.util.Locale

class MyChannelActions(
    val refresh: () -> Unit = {},
    val explain: (ScoredVideo, Boolean) -> Unit = { _, _ -> },
    val makeMore: (ScoredVideo) -> Unit = {},
    val save: (ParsedIdea) -> Unit = {},
    val create: (ParsedIdea) -> Unit = {},
    val open: (String) -> Unit = {},
)

@Composable
fun MyChannelTab(onOpenProject: (Long) -> Unit, viewModel: MyChannelViewModel = hiltViewModel()) {
    val context = LocalContext.current
    MyChannelContent(
        state = viewModel.state.collectAsStateWithLifecycle().value,
        notes = viewModel.notes.collectAsStateWithLifecycle().value,
        more = viewModel.more.collectAsStateWithLifecycle().value,
        saved = viewModel.saved.collectAsStateWithLifecycle().value,
        actions = MyChannelActions(
            refresh = viewModel::refresh, explain = viewModel::explain, makeMore = viewModel::makeMore, save = viewModel::save,
            create = { viewModel.create(it, onOpenProject) }, open = { openVideo(context, it) },
        ),
    )
}

@Composable
fun MyChannelContent(state: MyChannelState, notes: Map<String, NoteState>, more: Map<String, MoreState>, saved: Set<String>, actions: MyChannelActions) {
    when (state) {
        MyChannelState.Loading -> HomeCard { LoadingRow(R.string.research_loading) }
        MyChannelState.NoAccount -> Unit
        MyChannelState.PickOne -> MessageCard(R.string.research_pick_one)
        MyChannelState.Unsupported -> MessageCard(R.string.research_unsupported)
        is MyChannelState.Failed -> HomeCard {
            BodyText(state.error.message())
            SecondaryButton(stringResource(R.string.action_try_again), actions.refresh)
        }
        is MyChannelState.NotEnough -> HomeCard { BodyText(stringResource(R.string.research_not_enough, state.videos)) }
        is MyChannelState.Ready -> Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.md)) {
            HomeCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
                    Text(stringResource(R.string.research_outlier_title), style = CreatoraTheme.typography.cardTitle, color = CreatoraTheme.colors.text)
                    Tag(R.string.research_estimate)
                }
                BodyText(stringResource(R.string.research_outlier_explainer))
            }
            SectionLabel(R.string.research_winners)
            if (state.winners.isEmpty()) MessageCard(R.string.research_no_winners)
            state.winners.forEach { OutlierCard(it, winner = true, notes[it.video.id], more[it.video.id], saved, actions) }
            SectionLabel(R.string.research_not_working)
            if (state.weakest.isEmpty()) MessageCard(R.string.research_no_weak)
            state.weakest.forEach { OutlierCard(it, winner = false, notes[it.video.id], more[it.video.id], saved, actions) }
            HomeCard { UpdatedRow(state.cachedAt, state.stale, quotaUnits = 2, onRefresh = actions.refresh) }
        }
    }
}

@Composable
private fun OutlierCard(item: ScoredVideo, winner: Boolean, note: NoteState?, more: MoreState?, saved: Set<String>, actions: MyChannelActions) {
    HomeCard {
        Text(
            item.video.title, style = CreatoraTheme.typography.cardTitle, color = CreatoraTheme.colors.text, maxLines = 2,
            modifier = Modifier.fillMaxWidth().heightIn(min = CreatoraTheme.dimens.minTouchTarget).clickable(role = Role.Button) { actions.open(item.video.id) },
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
            Text(
                stringResource(R.string.research_times_usual, String.format(Locale.getDefault(), "%.1f", item.score)),
                style = CreatoraTheme.typography.bodySemiBold, color = CreatoraTheme.colors.text,
            )
            Tag(R.string.research_estimate)
        }
        Text(
            stringResource(R.string.research_band_line, compactCount(item.video.views), stringResource(item.band.labelRes())),
            style = CreatoraTheme.typography.caption, color = CreatoraTheme.colors.textMuted,
        )
        if (!item.ageAdjusted) Text(stringResource(R.string.research_not_age_adjusted), style = CreatoraTheme.typography.caption, color = CreatoraTheme.colors.warning)
        when (note) {
            null -> SecondaryButton(stringResource(if (winner) R.string.research_why else R.string.research_fix), { actions.explain(item, winner) })
            NoteState.Loading -> LoadingRow(R.string.research_thinking)
            is NoteState.Text -> BodyText(note.text, muted = false)
            is NoteState.Failed -> {
                AiProblem(note.errors)
                SecondaryButton(stringResource(R.string.action_try_again), { actions.explain(item, winner) })
            }
        }
        if (winner) {
            when (more) {
                null -> TextButton(onClick = { actions.makeMore(item) }, modifier = Modifier.heightIn(min = CreatoraTheme.dimens.minTouchTarget)) {
                    Text(stringResource(R.string.research_more_like_this))
                }
                MoreState.Loading -> LoadingRow(R.string.research_thinking)
                is MoreState.Ready -> more.ideas.forEach { idea -> IdeaRow(idea, idea.title in saved, { actions.save(idea) }, { actions.create(idea) }) }
                is MoreState.Failed -> {
                    AiProblem(more.errors)
                    SecondaryButton(stringResource(R.string.action_try_again), { actions.makeMore(item) })
                }
            }
        }
    }
}

private fun AgeBand.labelRes(): Int = when (this) {
    AgeBand.WEEK -> R.string.research_band_week
    AgeBand.MONTH -> R.string.research_band_month
    AgeBand.QUARTER -> R.string.research_band_quarter
    AgeBand.OLDER -> R.string.research_band_older
}

internal fun sampleVideo(id: String, title: String, views: Long) =
    VideoStats(id = id, title = title, publishedAt = "2026-10-01T10:00:00Z", views = views, channelTitle = "Creatora Kitchen")

private val sampleWinner = ScoredVideo(sampleVideo("a", "How I cook dal in 10 minutes", 48_000), 3.4, AgeBand.MONTH, ageAdjusted = true, baseline = 14_000)
private val sampleWeak = ScoredVideo(sampleVideo("b", "Kitchen tour", 1_200), 0.3, AgeBand.OLDER, ageAdjusted = false, baseline = 4_000)

@Composable
private fun MyChannelPreview(dark: Boolean) {
    CreatoraTheme(darkTheme = dark) {
        PreviewColumn {
            MyChannelContent(
                state = MyChannelState.Ready(listOf(sampleWinner), listOf(sampleWeak), cachedAt = System.currentTimeMillis() - 3_600_000, stale = false),
                notes = mapOf("a" to NoteState.Text("It got 3.4 times your usual views. The title names a time limit.")),
                more = emptyMap(), saved = emptySet(), actions = MyChannelActions(),
            )
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun MyChannelLightPreview() = MyChannelPreview(dark = false)

@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MyChannelDarkPreview() = MyChannelPreview(dark = true)
