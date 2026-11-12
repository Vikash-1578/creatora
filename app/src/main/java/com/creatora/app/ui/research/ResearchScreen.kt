package com.creatora.app.ui.research

import android.content.res.Configuration
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.creatora.app.R
import com.creatora.app.domain.home.AccountSelection
import com.creatora.app.ui.components.ChoiceChip
import com.creatora.app.ui.components.PrimaryButton
import com.creatora.app.ui.components.ScreenHeader
import com.creatora.app.ui.components.TabScreen
import com.creatora.app.ui.home.AccountSwitcher
import com.creatora.app.ui.home.BodyText
import com.creatora.app.ui.home.HomeCard
import com.creatora.app.ui.home.LoadingRow
import com.creatora.app.ui.theme.CreatoraTheme

enum class ResearchTab(val titleRes: Int) {
    MY_CHANNEL(R.string.research_tab_my_channel),
    COMPETITORS(R.string.research_tab_competitors),
    TRENDS(R.string.research_tab_trends),
    KEYWORDS(R.string.research_tab_keywords),
    IDEA_BANK(R.string.research_tab_idea_bank),
}

/**
 * Research tab: My Channel, Competitors, Trends, Keywords and the Idea bank. It follows the account chosen on Home;
 * with no account it asks to connect one. Everything shown comes from YouTube or the user's own saved data.
 */
@Composable
fun ResearchScreen(
    onOpenSettings: () -> Unit,
    onOpenCompetitor: (String) -> Unit,
    onOpenProject: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ResearchViewModel = hiltViewModel(),
) {
    val header = viewModel.header.collectAsStateWithLifecycle().value
    ResearchFrame(header, viewModel::select, onOpenSettings, modifier) { tab ->
        when (tab) {
            ResearchTab.MY_CHANNEL -> MyChannelTab(onOpenProject)
            ResearchTab.COMPETITORS -> CompetitorsTab(onOpenCompetitor)
            ResearchTab.TRENDS -> TrendsTab(onOpenProject)
            ResearchTab.KEYWORDS -> KeywordsTab()
            ResearchTab.IDEA_BANK -> IdeaBankTab(onOpenProject)
        }
    }
}

@Composable
fun ResearchFrame(
    header: ResearchHeader?,
    onSelect: (AccountSelection) -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
    tabContent: @Composable (ResearchTab) -> Unit,
) {
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    TabScreen(modifier = modifier) {
        ScreenHeader(title = stringResource(R.string.tab_research), subtitle = stringResource(R.string.research_subtitle))
        Column(Modifier.padding(horizontal = CreatoraTheme.spacing.lg), verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.md)) {
            when {
                header == null -> LoadingRow(R.string.research_loading)
                header.accounts.isEmpty() -> HomeCard {
                    BodyText(stringResource(R.string.research_connect_body))
                    PrimaryButton(stringResource(R.string.research_action_key), onConnect)
                }
                else -> {
                    AccountSwitcher(header.accounts, header.selection, onSelect)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
                        ResearchTab.entries.forEach { tab -> ChoiceChip(stringResource(tab.titleRes), tabIndex == tab.ordinal, { tabIndex = tab.ordinal }) }
                    }
                    tabContent(ResearchTab.entries[tabIndex])
                }
            }
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun ResearchScreenLightPreview() {
    CreatoraTheme(darkTheme = false) { ResearchFrame(header = ResearchHeader(emptyList(), AccountSelection.All), onSelect = {}, onConnect = {}) {} }
}

@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ResearchScreenDarkPreview() {
    CreatoraTheme(darkTheme = true) { ResearchFrame(header = ResearchHeader(emptyList(), AccountSelection.All), onSelect = {}, onConnect = {}) {} }
}
