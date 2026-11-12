package com.creatora.app.ui.research

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.creatora.app.R
import com.creatora.app.domain.account.ChannelPreview
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.repository.Competitor
import com.creatora.app.ui.components.HeaderIconButton
import com.creatora.app.ui.components.PrimaryButton
import com.creatora.app.ui.components.SecondaryButton
import com.creatora.app.ui.home.BodyText
import com.creatora.app.ui.home.HomeCard
import com.creatora.app.ui.home.LoadingRow
import com.creatora.app.ui.home.compactCount
import com.creatora.app.ui.home.message
import com.creatora.app.ui.theme.CreatoraTheme

class CompetitorsActions(
    val addByLink: (String) -> Unit = {},
    val addSuggestion: (ChannelPreview) -> Unit = {},
    val remove: (Competitor) -> Unit = {},
    val open: (String) -> Unit = {},
    val discover: (Boolean) -> Unit = {},
)

@Composable
fun CompetitorsTab(onOpenCompetitor: (String) -> Unit, viewModel: CompetitorsViewModel = hiltViewModel()) {
    CompetitorsContent(
        ui = viewModel.ui.collectAsStateWithLifecycle().value,
        actions = CompetitorsActions(
            addByLink = viewModel::addByLink, addSuggestion = viewModel::addSuggestion, remove = viewModel::remove,
            open = onOpenCompetitor, discover = viewModel::discover,
        ),
    )
}

@Composable
private fun AddProblem.text(limit: Int): String = when (this) {
    AddProblem.InvalidLink -> stringResource(R.string.research_problem_invalid)
    AddProblem.NotYouTube -> stringResource(R.string.research_problem_not_youtube)
    AddProblem.LimitReached -> stringResource(R.string.research_problem_limit, limit)
    AddProblem.Duplicate -> stringResource(R.string.research_problem_duplicate)
    AddProblem.OwnChannel -> stringResource(R.string.research_problem_own)
    is AddProblem.Failed -> error.message()
}

@Composable
fun CompetitorsContent(ui: CompetitorsUi, actions: CompetitorsActions) {
    var link by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.md)) {
        HomeCard {
            Text(stringResource(R.string.research_following, ui.competitors.size, ui.limit), style = CreatoraTheme.typography.cardTitle, color = CreatoraTheme.colors.text)
            OutlinedTextField(
                value = link, onValueChange = { link = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.research_add_label)) },
            )
            ui.problem?.let { Text(it.text(ui.limit), style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.danger) }
            if (ui.adding) LoadingRow(R.string.research_loading)
            PrimaryButton(stringResource(R.string.research_add), { actions.addByLink(link); link = "" }, enabled = link.isNotBlank() && !ui.adding)
        }
        if (ui.competitors.isEmpty()) MessageCard(R.string.research_no_competitors)
        ui.competitors.forEach { CompetitorRow(it, actions) }
        SectionLabel(R.string.research_suggestions)
        HomeCard {
            when (val suggestions = ui.suggestions) {
                Suggestions.Idle -> {
                    BodyText(stringResource(R.string.research_suggestions_body))
                    SecondaryButton(stringResource(R.string.research_find_channels), { actions.discover(false) })
                }
                Suggestions.Loading -> LoadingRow(R.string.research_loading)
                Suggestions.NeedsProfile -> BodyText(stringResource(R.string.research_needs_profile))
                is Suggestions.Failed -> {
                    BodyText(suggestions.error.message())
                    SecondaryButton(stringResource(R.string.action_try_again), { actions.discover(false) })
                }
                is Suggestions.Ready -> {
                    if (suggestions.channels.isEmpty()) BodyText(stringResource(R.string.research_no_suggestions))
                    suggestions.channels.take(MAX_SUGGESTIONS).forEach { SuggestionRow(it, actions.addSuggestion) }
                    UpdatedRow(suggestions.cachedAt, suggestions.stale, quotaUnits = DISCOVER_UNITS, onRefresh = { actions.discover(true) })
                }
            }
        }
    }
}

@Composable
private fun Avatar(url: String?, name: String) {
    AsyncImage(
        model = url, contentDescription = name, contentScale = ContentScale.Crop,
        modifier = Modifier.size(CreatoraTheme.dimens.minTouchTarget).clip(CircleShape).background(CreatoraTheme.colors.surfaceAlt),
    )
}

@Composable
private fun CompetitorRow(competitor: Competitor, actions: CompetitorsActions) {
    HomeCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.md)) {
            Row(
                Modifier.weight(1f).heightIn(min = CreatoraTheme.dimens.minTouchTarget).clickable(role = Role.Button) { actions.open(competitor.channelId) },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.md),
            ) {
                Avatar(competitor.photoUrl, competitor.title)
                Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.xs)) {
                    Text(competitor.title, style = CreatoraTheme.typography.cardTitle, color = CreatoraTheme.colors.text, maxLines = 1)
                    Text(
                        competitor.subscribers?.let { stringResource(R.string.research_subscribers_count, compactCount(it)) } ?: stringResource(R.string.research_subscribers_hidden),
                        style = CreatoraTheme.typography.caption, color = CreatoraTheme.colors.textMuted,
                    )
                }
            }
            HeaderIconButton(Icons.Outlined.Delete, stringResource(R.string.research_remove_competitor, competitor.title), { actions.remove(competitor) })
        }
    }
}

@Composable
private fun SuggestionRow(channel: ChannelPreview, onAdd: (ChannelPreview) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.md)) {
        Avatar(channel.photoUrl, channel.title)
        Column(Modifier.weight(1f)) {
            Text(channel.title, style = CreatoraTheme.typography.cardTitle, color = CreatoraTheme.colors.text, maxLines = 1)
            Text(
                channel.subscribers?.let { stringResource(R.string.research_subscribers_count, compactCount(it)) } ?: stringResource(R.string.research_subscribers_hidden),
                style = CreatoraTheme.typography.caption, color = CreatoraTheme.colors.textMuted,
            )
        }
        SecondaryButton(stringResource(R.string.research_follow), { onAdd(channel) })
    }
}

private const val MAX_SUGGESTIONS = 8
private const val DISCOVER_UNITS = 100

private fun sampleChannel(id: String, name: String, subs: Long) = ChannelPreview(ConnectPlatform.YOUTUBE, id, name, subscribers = subs)

@Composable
private fun CompetitorsPreview(dark: Boolean) {
    CreatoraTheme(darkTheme = dark) {
        PreviewColumn {
            CompetitorsContent(
                ui = CompetitorsUi(
                    competitors = listOf(Competitor("UC1", "Dal Diaries", subscribers = 120_000)),
                    limit = 3,
                    suggestions = Suggestions.Ready(listOf(sampleChannel("UC2", "Street Food Lab", 54_000)), System.currentTimeMillis() - 7_200_000, fromCache = true, stale = false),
                ),
                actions = CompetitorsActions(),
            )
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun CompetitorsLightPreview() = CompetitorsPreview(dark = false)

@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CompetitorsDarkPreview() = CompetitorsPreview(dark = true)
