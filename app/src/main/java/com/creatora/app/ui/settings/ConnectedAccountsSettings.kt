package com.creatora.app.ui.settings

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.creatora.app.R
import com.creatora.app.domain.account.ChannelPreview
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.account.ConnectedAccount
import com.creatora.app.domain.account.PlatformSupport
import com.creatora.app.ui.accounts.AddAccountPanel
import com.creatora.app.ui.accounts.Avatar
import com.creatora.app.ui.accounts.ChannelCard
import com.creatora.app.ui.accounts.labelRes
import com.creatora.app.ui.components.SectionTitle
import com.creatora.app.ui.components.SubScreenScaffold
import com.creatora.app.ui.theme.CreatoraTheme

@Composable
fun ConnectedAccountsScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: ConnectedAccountsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadOwn() }
    ConnectedAccountsContent(state, viewModel::lookUp, viewModel::confirm, viewModel::dismissPasted, viewModel::remove, onBack, modifier)
}

@Composable
fun ConnectedAccountsContent(
    state: ConnectedAccountsUiState,
    onLookUp: (String, ConnectPlatform) -> Unit,
    onConfirm: (ChannelPreview) -> Unit,
    onDismissPasted: () -> Unit,
    onRemove: (ConnectedAccount) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CreatoraTheme.colors
    SubScreenScaffold(title = stringResource(R.string.settings_connected), onBack = onBack, modifier = modifier) {
        Text(stringResource(R.string.connected_limit, state.accounts.size, state.plan.maxAccounts), style = CreatoraTheme.typography.body, color = colors.textMuted)
        if (state.accounts.isEmpty()) {
            Text(stringResource(R.string.connected_empty), style = CreatoraTheme.typography.body, color = colors.textMuted)
        }
        Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.md)) {
            state.accounts.forEach { account ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = CreatoraTheme.dimens.minTouchTarget),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.md),
                ) {
                    Avatar(account.photoUrl, account.title)
                    Column(Modifier.weight(1f)) {
                        Text(account.title, style = CreatoraTheme.typography.cardTitle, color = colors.text, maxLines = 1)
                        Text(
                            stringResource(
                                if (account.platform.support == PlatformSupport.LINK_ONLY) R.string.connected_link_only else R.string.connected_connected,
                                stringResource(account.platform.labelRes()),
                            ),
                            style = CreatoraTheme.typography.caption, color = colors.textMuted,
                        )
                    }
                    IconButton(onClick = { onRemove(account) }, modifier = Modifier.size(CreatoraTheme.dimens.minTouchTarget)) {
                        Icon(Icons.Outlined.Delete, stringResource(R.string.connected_remove, account.title), tint = colors.textMuted)
                    }
                }
            }
        }
        if (state.loadingOwn) Text(stringResource(R.string.connect_loading), style = CreatoraTheme.typography.caption, color = colors.textMuted)
        state.ownChannels.filter { own -> state.accounts.none { it.id == own.id } }.forEach {
            ChannelCard(it, stringResource(R.string.connect_is_this_you), { onConfirm(it) })
        }
        Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
            SectionTitle(stringResource(R.string.connect_add_other))
            AddAccountPanel(state.lookup, onLookUp, onConfirm, onDismissPasted, Modifier.padding(top = CreatoraTheme.spacing.xs))
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun ConnectedLightPreview() {
    CreatoraTheme(darkTheme = false) {
        ConnectedAccountsContent(
            ConnectedAccountsUiState(accounts = listOf(ConnectedAccount(ConnectPlatform.YOUTUBE, "UC1", "My channel"))),
            { _, _ -> }, {}, {}, {}, {},
        )
    }
}

@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ConnectedDarkPreview() {
    CreatoraTheme(darkTheme = true) { ConnectedAccountsContent(ConnectedAccountsUiState(), { _, _ -> }, {}, {}, {}, {}) }
}
