package com.creatora.app.ui.research

import android.content.res.Configuration
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.creatora.app.R
import com.creatora.app.domain.research.KeywordEstimate
import com.creatora.app.domain.research.Level
import com.creatora.app.ui.components.ChoiceChip
import com.creatora.app.ui.components.PrimaryButton
import com.creatora.app.ui.components.SecondaryButton
import com.creatora.app.ui.home.BodyText
import com.creatora.app.ui.home.HomeCard
import com.creatora.app.ui.home.LoadingRow
import com.creatora.app.ui.home.compactCount
import com.creatora.app.ui.home.message
import com.creatora.app.ui.theme.CreatoraTheme

class KeywordsActions(
    val search: (String, Boolean) -> Unit = { _, _ -> },
    val suggestQuestions: () -> Unit = {},
)

@Composable
fun KeywordsTab(viewModel: KeywordsViewModel = hiltViewModel()) {
    KeywordsContent(
        state = viewModel.state.collectAsStateWithLifecycle().value,
        questions = viewModel.questions.collectAsStateWithLifecycle().value,
        actions = KeywordsActions(search = viewModel::search, suggestQuestions = viewModel::suggestQuestions),
    )
}

private fun Level.labelRes(): Int = when (this) {
    Level.LOW -> R.string.research_level_low
    Level.MEDIUM -> R.string.research_level_medium
    Level.HIGH -> R.string.research_level_high
}

@Composable
fun KeywordsContent(state: KeywordState, questions: QuestionsState, actions: KeywordsActions) {
    var text by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.md)) {
        HomeCard {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.research_keyword_label)) },
            )
            BodyText(stringResource(R.string.research_keyword_cost))
            PrimaryButton(stringResource(R.string.research_keyword_search), { actions.search(text, false) }, enabled = text.isNotBlank() && state !is KeywordState.Loading)
        }
        when (state) {
            KeywordState.Idle -> MessageCard(R.string.research_keyword_idle)
            KeywordState.Loading -> HomeCard { LoadingRow(R.string.research_loading) }
            is KeywordState.Failed -> HomeCard {
                BodyText(state.error.message())
                SecondaryButton(stringResource(R.string.action_try_again), { actions.search(text, false) })
            }
            is KeywordState.NotEnough -> HomeCard { BodyText(stringResource(R.string.research_keyword_not_enough, state.results)) }
            is KeywordState.Ready -> KeywordResult(state, questions, actions)
        }
    }
}

@Composable
private fun KeywordResult(state: KeywordState.Ready, questions: QuestionsState, actions: KeywordsActions) {
    val estimate = state.estimate
    var formula by rememberSaveable { mutableStateOf(false) }
    HomeCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
            Text(state.query, style = CreatoraTheme.typography.sectionTitle, color = CreatoraTheme.colors.text, modifier = Modifier.weight(1f))
            Tag(R.string.research_estimate)
        }
        Text(stringResource(R.string.research_score, estimate.score), style = CreatoraTheme.typography.screenTitle, color = CreatoraTheme.colors.text)
        Text(
            stringResource(R.string.research_demand, stringResource(estimate.demand.labelRes()), compactCount(estimate.medianViewsPerDay), estimate.recentPercent),
            style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.text,
        )
        Text(
            stringResource(R.string.research_competition, stringResource(estimate.competition.labelRes()), estimate.exactMatchPercent, estimate.medianAgeDays),
            style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.text,
        )
        Text(
            estimate.biggerChannelsPercent?.let { stringResource(R.string.research_bigger_channels, it) } ?: stringResource(R.string.research_bigger_unknown),
            style = CreatoraTheme.typography.caption, color = CreatoraTheme.colors.textMuted,
        )
        BodyText(stringResource(R.string.research_keyword_disclaimer, estimate.sampleSize))
        TextButton(onClick = { formula = !formula }) { Text(stringResource(R.string.research_formula_toggle)) }
        if (formula) BodyText(stringResource(R.string.research_formula))
        UpdatedRow(state.cachedAt, state.stale, quotaUnits = KEYWORD_UNITS, onRefresh = { actions.search(state.query, true) })
    }
    SectionLabel(R.string.research_related)
    HomeCard {
        if (estimate.related.isEmpty()) {
            BodyText(stringResource(R.string.research_related_none))
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
                estimate.related.forEach { tag -> ChoiceChip(tag, selected = false, onClick = { actions.search(tag, false) }) }
            }
            BodyText(stringResource(R.string.research_related_note))
        }
    }
    SectionLabel(R.string.research_questions)
    HomeCard {
        when (questions) {
            QuestionsState.Idle -> {
                BodyText(stringResource(R.string.research_questions_body))
                SecondaryButton(stringResource(R.string.research_questions_button), actions.suggestQuestions)
            }
            QuestionsState.Loading -> LoadingRow(R.string.research_thinking)
            is QuestionsState.Failed -> {
                AiProblem(questions.errors)
                SecondaryButton(stringResource(R.string.action_try_again), actions.suggestQuestions)
            }
            is QuestionsState.Ready -> {
                Tag(R.string.research_ai_suggestions)
                questions.questions.forEach { BodyText(it, muted = false) }
            }
        }
    }
}

private const val KEYWORD_UNITS = 102

private val sampleEstimate = KeywordEstimate(
    demand = Level.HIGH, demandPoints = 72, medianViewsPerDay = 1_900, recentPercent = 64, competition = Level.MEDIUM, competitionPoints = 48,
    biggerChannelsPercent = 55, exactMatchPercent = 40, medianAgeDays = 210, score = 66, related = listOf("street food", "dal recipe"), sampleSize = 25,
)

@Composable
private fun KeywordsPreview(dark: Boolean) {
    CreatoraTheme(darkTheme = dark) {
        PreviewColumn {
            KeywordsContent(
                state = KeywordState.Ready("dal recipe", sampleEstimate, System.currentTimeMillis() - 1_800_000, fromCache = true, stale = false),
                questions = QuestionsState.Ready(listOf("How long does dal take to cook?")),
                actions = KeywordsActions(),
            )
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun KeywordsLightPreview() = KeywordsPreview(dark = false)

@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun KeywordsDarkPreview() = KeywordsPreview(dark = true)
