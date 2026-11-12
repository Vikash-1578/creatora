package com.creatora.app.ui.research

import android.content.res.Configuration
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import android.widget.Toast
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.creatora.app.R
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.account.ConnectedAccount
import com.creatora.app.domain.home.CreatorIdea
import com.creatora.app.domain.home.IdeaStatus
import com.creatora.app.domain.research.BankFilter
import com.creatora.app.ui.components.ChoiceChip
import com.creatora.app.ui.home.BodyText
import com.creatora.app.ui.home.HomeCard
import com.creatora.app.ui.theme.CreatoraTheme

class IdeaBankActions(
    val setFilter: (BankFilter) -> Unit = {},
    val setAccount: (String?) -> Unit = {},
    val markPlanned: (CreatorIdea) -> Unit = {},
    val remove: (CreatorIdea) -> Unit = {},
    val create: (CreatorIdea) -> Unit = {},
    val openProject: (Long) -> Unit = {},
)

@Composable
fun IdeaBankTab(onOpenProject: (Long) -> Unit, viewModel: IdeaBankViewModel = hiltViewModel()) {
    val context = LocalContext.current
    IdeaBankContent(
        ui = viewModel.ui.collectAsStateWithLifecycle().value,
        actions = IdeaBankActions(
            setFilter = viewModel::setFilter, setAccount = viewModel::setAccount, markPlanned = viewModel::markPlanned, remove = viewModel::remove,
            create = { idea ->
                viewModel.create(idea) { started ->
                    if (started.draftFailed) Toast.makeText(context, R.string.research_draft_failed, Toast.LENGTH_LONG).show()
                    onOpenProject(started.projectId)
                }
            },
            openProject = onOpenProject,
        ),
    )
}

private fun BankFilter.labelRes(): Int = when (this) {
    BankFilter.ALL -> R.string.research_filter_all
    BankFilter.NEW -> R.string.research_status_new
    BankFilter.PLANNED -> R.string.research_status_planned
    BankFilter.MADE -> R.string.research_status_made
}

private fun IdeaStatus.labelRes(): Int = when (this) {
    IdeaStatus.PLANNED -> R.string.research_status_planned
    IdeaStatus.MADE -> R.string.research_status_made
    else -> R.string.research_status_new
}

private fun sourceRes(source: String): Int = when (source) {
    "daily" -> R.string.research_source_daily
    "research" -> R.string.research_source_research
    "trends" -> R.string.research_source_trends
    "competitor" -> R.string.research_source_competitor
    "trending" -> R.string.research_source_trending
    else -> R.string.research_source_other
}

@Composable
fun IdeaBankContent(ui: IdeaBankUi, actions: IdeaBankActions) {
    Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.md)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
            BankFilter.entries.forEach { filter -> ChoiceChip(stringResource(filter.labelRes()), ui.filter == filter, { actions.setFilter(filter) }) }
        }
        if (ui.accounts.size > 1) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
                ChoiceChip(stringResource(R.string.home_all_accounts), ui.accountKey == null, { actions.setAccount(null) })
                ui.accounts.forEach { account -> ChoiceChip(account.title, ui.accountKey == account.key, { actions.setAccount(account.key) }) }
            }
        }
        if (ui.loaded && ui.items.isEmpty()) MessageCard(R.string.research_bank_empty)
        ui.items.forEach { item ->
            HomeCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
                    Text(item.idea.title, modifier = Modifier.weight(1f), style = CreatoraTheme.typography.cardTitle, color = CreatoraTheme.colors.text)
                    Tag(item.status.labelRes())
                }
                if (item.idea.reason.isNotBlank()) BodyText(item.idea.reason)
                Text(
                    listOfNotNull(stringResource(sourceRes(item.idea.source)), item.account?.title).joinToString(" · "),
                    style = CreatoraTheme.typography.caption, color = CreatoraTheme.colors.textMuted,
                )
                item.idea.score?.let {
                    Row(horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.research_potential, it), style = CreatoraTheme.typography.caption, color = CreatoraTheme.colors.textMuted)
                        Tag(R.string.research_ai_estimate)
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
                    val projectId = item.idea.projectId
                    if (projectId != null) {
                        BankButton(R.string.research_open_project) { actions.openProject(projectId) }
                    } else if (item.status != IdeaStatus.MADE) {
                        BankButton(R.string.research_create) { actions.create(item.idea) }
                    }
                    if (item.status == IdeaStatus.SAVED) BankButton(R.string.research_mark_planned) { actions.markPlanned(item.idea) }
                    BankButton(R.string.research_remove) { actions.remove(item.idea) }
                }
            }
        }
    }
}

@Composable
private fun BankButton(textRes: Int, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = CreatoraTheme.dimens.minTouchTarget)) { Text(stringResource(textRes)) }
}

@Composable
private fun IdeaBankPreview(dark: Boolean) {
    CreatoraTheme(darkTheme = dark) {
        PreviewColumn {
            IdeaBankContent(
                ui = IdeaBankUi(
                    items = listOf(
                        BankItem(CreatorIdea("1", "Monsoon chaat under 50 rupees", "Trending: monsoon street food", 64, IdeaStatus.SAVED, "trends"), IdeaStatus.SAVED, null),
                        BankItem(CreatorIdea("2", "Dal in 10 minutes, part 2", "Follow-up to your best video", 71, IdeaStatus.PLANNED, "research", projectId = 4), IdeaStatus.PLANNED, null),
                    ),
                    accounts = listOf(ConnectedAccount(ConnectPlatform.YOUTUBE, "UC1", "Creatora Kitchen")),
                    loaded = true,
                ),
                actions = IdeaBankActions(),
            )
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun IdeaBankLightPreview() = IdeaBankPreview(dark = false)

@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun IdeaBankDarkPreview() = IdeaBankPreview(dark = true)
