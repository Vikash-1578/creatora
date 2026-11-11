package com.creatora.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.creatora.app.R
import com.creatora.app.domain.account.ChannelPreview
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.account.ConnectedAccount
import com.creatora.app.domain.account.PlatformSupport
import com.creatora.app.ui.accounts.AddAccountPanel
import com.creatora.app.ui.accounts.Avatar
import com.creatora.app.ui.accounts.ChannelCard
import com.creatora.app.ui.accounts.labelRes
import com.creatora.app.ui.components.SecondaryButton
import com.creatora.app.ui.components.SectionTitle
import com.creatora.app.ui.theme.CreatoraTheme

/** What the Connected accounts section needs to react to. It lives inside Settings > Account. */
class ConnectedAccountsActions(
    val login: (ConnectPlatform) -> Unit,
    val lookUp: (String, ConnectPlatform) -> Unit,
    val confirm: (ChannelPreview) -> Unit,
    val dismissPasted: () -> Unit,
    val remove: (ConnectedAccount) -> Unit,
)

/** The list of connected accounts and the "Connect your accounts" button that opens the connect panel. */
@Composable
fun ConnectedAccountsSection(state: ConnectedAccountsUiState, actions: ConnectedAccountsActions, modifier: Modifier = Modifier) {
    val colors = CreatoraTheme.colors
    var connecting by rememberSaveable { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.md)) {
        SectionTitle(stringResource(R.string.settings_connected))
        Text(stringResource(R.string.connected_limit, state.accounts.size, state.plan.maxAccounts), style = CreatoraTheme.typography.body, color = colors.textMuted)
        if (state.accounts.isEmpty()) {
            Text(stringResource(R.string.connected_empty), style = CreatoraTheme.typography.body, color = colors.textMuted)
        }
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
                IconButton(onClick = { actions.remove(account) }, modifier = Modifier.size(CreatoraTheme.dimens.minTouchTarget)) {
                    Icon(Icons.Outlined.Delete, stringResource(R.string.connected_remove, account.title), tint = colors.textMuted)
                }
            }
        }
        if (state.loadingOwn) Text(stringResource(R.string.connect_loading), style = CreatoraTheme.typography.caption, color = colors.textMuted)
        state.ownChannels.filter { own -> state.accounts.none { it.id == own.id } }.forEach {
            ChannelCard(it, stringResource(R.string.connect_is_this_you), { actions.confirm(it) })
        }
        SecondaryButton(
            text = stringResource(if (connecting) R.string.connect_hide else R.string.account_connect_accounts),
            onClick = { connecting = !connecting },
            modifier = Modifier.fillMaxWidth(),
        )
        if (connecting) {
            AddAccountPanel(state.lookup, actions.login, actions.lookUp, actions.confirm, actions.dismissPasted)
        }
    }
}
