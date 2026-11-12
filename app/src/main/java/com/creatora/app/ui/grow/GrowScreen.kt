package com.creatora.app.ui.grow

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Science
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.creatora.app.R
import com.creatora.app.ui.components.FeatureCard
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.creatora.app.ui.home.AccountSwitcher
import com.creatora.app.ui.home.ChannelCardsViewModel
import com.creatora.app.ui.home.SnapshotSection
import com.creatora.app.ui.home.StreakSection
import com.creatora.app.ui.home.UploadsSection
import com.creatora.app.ui.home.WhyDialog
import com.creatora.app.ui.research.ResearchViewModel
import com.creatora.app.ui.components.ScreenHeader
import com.creatora.app.ui.components.TabScreen
import com.creatora.app.ui.theme.CreatoraTheme

/**
 * Grow tab. It reads the same connected accounts as Home: with none it asks to connect one; with one or more it shows the
 * real numbers for the selected account (snapshot, recent uploads, upload streak) under the same account switcher.
 */
@Composable
fun GrowScreen(
    onConnectChannel: () -> Unit,
    onOpenCreate: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenAnalytics: () -> Unit = {},
    onOpenEarn: () -> Unit = {},
    accountsViewModel: ResearchViewModel = hiltViewModel(),
    cardsViewModel: ChannelCardsViewModel = hiltViewModel(),
) {
    val header by accountsViewModel.header.collectAsStateWithLifecycle()
    val cards by cardsViewModel.state.collectAsStateWithLifecycle()
    val accounts = header?.accounts.orEmpty()
    TabScreen(modifier = modifier) {
        ScreenHeader(
            title = stringResource(R.string.tab_grow),
            subtitle = stringResource(R.string.grow_subtitle),
        )
        Column(
            modifier = Modifier.padding(horizontal = CreatoraTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.md),
        ) {
            if (header != null && accounts.isEmpty()) {
                val connect = stringResource(R.string.grow_action_connect)
                FeatureCard(Icons.Outlined.BarChart, stringResource(R.string.grow_analytics), stringResource(R.string.grow_analytics_body), connect, onConnectChannel)
                FeatureCard(Icons.Outlined.Payments, stringResource(R.string.earn_title), stringResource(R.string.grow_earn_body), connect, onConnectChannel)
            } else if (header != null) {
                AccountSwitcher(accounts, header!!.selection, accountsViewModel::select)
                val open = stringResource(R.string.grow_action_open)
                FeatureCard(Icons.Outlined.BarChart, stringResource(R.string.grow_analytics), stringResource(R.string.grow_analytics_body), open, onOpenAnalytics)
                FeatureCard(Icons.Outlined.Payments, stringResource(R.string.earn_title), stringResource(R.string.grow_earn_body), open, onOpenEarn)
                SnapshotSection(cards, cardsViewModel::setHideAmounts, cardsViewModel::refresh)
                UploadsSection(cards, cardsViewModel::explain)
                StreakSection(cards, cardsViewModel::setStreakUnit)
            }
        }
    }
    WhyDialog(cards.why, cardsViewModel::closeWhy)
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun GrowScreenLightPreview() {
    CreatoraTheme(darkTheme = false) { GrowEmptyPreview() }
}

@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GrowScreenDarkPreview() {
    CreatoraTheme(darkTheme = true) { GrowEmptyPreview() }
}

/** The state with no account connected: the connect cards. */
@Composable
private fun GrowEmptyPreview() {
    TabScreen {
        ScreenHeader(title = stringResource(R.string.tab_grow), subtitle = stringResource(R.string.grow_subtitle))
        Column(Modifier.padding(horizontal = CreatoraTheme.spacing.lg), verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.md)) {
            FeatureCard(Icons.Outlined.BarChart, stringResource(R.string.grow_analytics), stringResource(R.string.grow_analytics_body), stringResource(R.string.grow_action_connect), {})
        }
    }
}
