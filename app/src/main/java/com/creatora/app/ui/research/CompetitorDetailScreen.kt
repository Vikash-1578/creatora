package com.creatora.app.ui.research

import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.creatora.app.R
import com.creatora.app.domain.account.ChannelPreview
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.research.AgeBand
import com.creatora.app.domain.research.Growth
import com.creatora.app.domain.research.ScoredVideo
import com.creatora.app.domain.research.ViralConcept
import com.creatora.app.ui.components.PrimaryButton
import com.creatora.app.ui.components.SecondaryButton
import com.creatora.app.ui.components.SubScreenScaffold
import com.creatora.app.ui.components.tabBottomSpace
import com.creatora.app.ui.home.BodyText
import com.creatora.app.ui.home.HomeCard
import com.creatora.app.ui.home.LoadingRow
import com.creatora.app.ui.home.compactCount
import com.creatora.app.ui.home.message
import com.creatora.app.ui.theme.CreatoraTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class CompetitorDetailActions(
    val refresh: () -> Unit = {},
    val explain: (ScoredVideo) -> Unit = {},
    val use: (ViralConcept, Boolean) -> Unit = { _, _ -> },
    val open: (String) -> Unit = {},
)

@Composable
fun CompetitorDetailScreen(
    onBack: () -> Unit,
    onOpenProject: (Long) -> Unit,
    viewModel: CompetitorDetailViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val title = (state as? DetailState.Ready)?.detail?.info?.title ?: stringResource(R.string.research_competitor)
    SubScreenScaffold(title = title, onBack = onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.md)) {
            CompetitorDetailContent(
                state = state,
                viral = viewModel.viralNotes.collectAsStateWithLifecycle().value,
                actions = CompetitorDetailActions(
                    refresh = viewModel::refresh, explain = viewModel::explain, open = { openVideo(context, it) },
                    use = { concept, letAi ->
                        viewModel.use(concept, letAi) { projectId, draftFailed ->
                            if (draftFailed) Toast.makeText(context, R.string.research_draft_failed, Toast.LENGTH_LONG).show()
                            onOpenProject(projectId)
                        }
                    },
                ),
            )
            Spacer(Modifier.height(tabBottomSpace()))
        }
    }
}

/** yyyyMMdd as a date in the phone's format. */
private fun formatDay(day: String): String =
    runCatching { LocalDate.parse(day, DateTimeFormatter.BASIC_ISO_DATE).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }.getOrDefault(day)

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.md)) {
        Text(label, modifier = Modifier.weight(1f), style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.textMuted)
        Text(value, style = CreatoraTheme.typography.bodySemiBold, color = CreatoraTheme.colors.text)
    }
}

@Composable
fun CompetitorDetailContent(state: DetailState, viral: Map<String, ViralState>, actions: CompetitorDetailActions) {
    when (state) {
        DetailState.Loading -> HomeCard { LoadingRow(R.string.research_loading) }
        is DetailState.Failed -> HomeCard {
            BodyText(state.error.message())
            SecondaryButton(stringResource(R.string.action_try_again), actions.refresh)
        }
        is DetailState.Ready -> {
            val detail = state.detail
            val none = stringResource(R.string.research_not_available)
            HomeCard {
                StatRow(stringResource(R.string.research_subscribers), detail.info.subscribers?.let { compactCount(it) } ?: stringResource(R.string.research_subscribers_hidden))
                StatRow(stringResource(R.string.research_total_views), detail.info.viewCount?.let { compactCount(it) } ?: none)
                StatRow(stringResource(R.string.research_video_count), detail.info.videoCount?.toString() ?: none)
                StatRow(
                    stringResource(R.string.research_upload_frequency),
                    detail.uploadsPerWeek?.let { stringResource(R.string.research_per_week, String.format(Locale.getDefault(), "%.1f", it)) } ?: none,
                )
                StatRow(stringResource(R.string.research_average_views), detail.averageViews?.let { compactCount(it) } ?: none)
                StatRow(stringResource(R.string.research_growth), growthText(detail.growth))
                Text(stringResource(R.string.research_detail_note), style = CreatoraTheme.typography.caption, color = CreatoraTheme.colors.textMuted)
            }
            detail.videosError?.let { HomeCard { BodyText(it.message()) } }
            if (detail.topVideos.isNotEmpty()) {
                SectionLabel(R.string.research_top_videos)
                HomeCard { detail.topVideos.forEach { VideoLine(it) { actions.open(it.id) } } }
            }
            if (detail.viral.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
                    SectionLabel(R.string.research_viral)
                    Tag(R.string.research_estimate)
                }
                detail.viral.forEach { ViralCard(it, viral[it.video.id], actions) }
            }
            HomeCard { UpdatedRow(detail.updatedAt, detail.stale, quotaUnits = 3, onRefresh = actions.refresh) }
        }
    }
}

@Composable
private fun growthText(growth: Growth): String = when (growth) {
    is Growth.Tracking -> growth.sinceDay?.let { stringResource(R.string.research_tracking_since, formatDay(it)) } ?: stringResource(R.string.research_tracking_starts)
    is Growth.Known -> stringResource(
        R.string.research_growth_known,
        growth.subscribers?.let { (if (it >= 0) "+" else "-") + compactCount(kotlin.math.abs(it)) } ?: stringResource(R.string.research_not_available),
        growth.days, formatDay(growth.sinceDay),
    )
}

@Composable
private fun ViralCard(item: ScoredVideo, note: ViralState?, actions: CompetitorDetailActions) {
    HomeCard {
        VideoLine(item.video) { actions.open(item.video.id) }
        Text(
            stringResource(R.string.research_times_usual, String.format(Locale.getDefault(), "%.1f", item.score)),
            style = CreatoraTheme.typography.bodySemiBold, color = CreatoraTheme.colors.text,
        )
        when (note) {
            null -> SecondaryButton(stringResource(R.string.research_why_viral), { actions.explain(item) })
            ViralState.Loading -> LoadingRow(R.string.research_thinking)
            is ViralState.Failed -> {
                AiProblem(note.errors)
                SecondaryButton(stringResource(R.string.action_try_again), { actions.explain(item) })
            }
            is ViralState.Ready -> {
                Text(note.concept.concept, style = CreatoraTheme.typography.cardTitle, color = CreatoraTheme.colors.text)
                BodyText(note.concept.why, muted = false)
                Text(stringResource(R.string.research_viral_note), style = CreatoraTheme.typography.caption, color = CreatoraTheme.colors.textMuted)
                PrimaryButton(stringResource(R.string.research_use_concept), { actions.use(note.concept, false) }, modifier = Modifier.fillMaxWidth())
                SecondaryButton(stringResource(R.string.research_let_ai), { actions.use(note.concept, true) }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private val sampleInfo = ChannelPreview(ConnectPlatform.YOUTUBE, "UC1", "Dal Diaries", subscribers = 120_000, videoCount = 240, viewCount = 9_400_000)
private val sampleViral = ScoredVideo(sampleVideo("v", "Dal in 10 minutes", 310_000), 4.2, AgeBand.MONTH, ageAdjusted = true, baseline = 74_000)

@Composable
private fun CompetitorDetailPreview(dark: Boolean) {
    CreatoraTheme(darkTheme = dark) {
        PreviewColumn {
            CompetitorDetailContent(
                state = DetailState.Ready(
                    CompetitorDetail(
                        info = sampleInfo, growth = Growth.Tracking("20261101"), uploadsPerWeek = 2.5, averageViews = 80_000,
                        topVideos = listOf(sampleViral.video), viral = listOf(sampleViral), videosError = null,
                        updatedAt = System.currentTimeMillis() - 3_600_000, stale = false,
                    ),
                ),
                viral = mapOf("v" to ViralState.Ready(ViralConcept("A fast weeknight dish with a time limit", "The title promises a time limit. Comments mention saving time."))),
                actions = CompetitorDetailActions(),
            )
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun CompetitorDetailLightPreview() = CompetitorDetailPreview(dark = false)

@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CompetitorDetailDarkPreview() = CompetitorDetailPreview(dark = true)
