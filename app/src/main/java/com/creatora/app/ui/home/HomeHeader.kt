package com.creatora.app.ui.home

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import com.creatora.app.R
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.account.ConnectedAccount
import com.creatora.app.domain.home.AccountSelection
import com.creatora.app.domain.home.dateLocale
import com.creatora.app.ui.accounts.Avatar
import com.creatora.app.ui.accounts.labelRes
import com.creatora.app.ui.components.ChoiceChip
import com.creatora.app.ui.components.HeaderIconButton
import com.creatora.app.ui.theme.CreatoraTheme
import java.text.SimpleDateFormat
import java.util.Date

/** Greeting with the first name, today's date in the app language, and the bell, Creatora AI and profile photo. */
@Composable
fun HomeHeader(state: HomeUiState, onBell: () -> Unit, onCoach: () -> Unit, onProfile: () -> Unit, modifier: Modifier = Modifier) {
    val colors = CreatoraTheme.colors
    val spacing = CreatoraTheme.spacing
    val date = remember(state.language) {
        SimpleDateFormat(DateFormat.getBestDateTimePattern(state.language.dateLocale(), "EEEEdMMMM"), state.language.dateLocale()).format(Date())
    }
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = spacing.lg, vertical = spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                state.firstName?.let { stringResource(R.string.home_greeting, it) } ?: stringResource(R.string.home_greeting_no_name),
                style = CreatoraTheme.typography.screenTitle, color = colors.text, maxLines = 1,
            )
            Text(date, style = CreatoraTheme.typography.caption, color = colors.textMuted)
        }
        HeaderIconButton(Icons.Outlined.Notifications, stringResource(R.string.cd_notifications), onBell)
        HeaderIconButton(Icons.Outlined.AutoAwesome, stringResource(R.string.cd_open_coach), onCoach)
        Avatar(
            state.photoUrl, state.firstName.orEmpty(),
            Modifier.heightIn(min = CreatoraTheme.dimens.minTouchTarget).clickable(role = Role.Button, onClickLabel = stringResource(R.string.cd_profile), onClick = onProfile),
        )
    }
}

private const val DROPDOWN_FROM = 5

/**
 * Every connected account, grouped by platform, with "All accounts" when there is more than one. Home follows the
 * choice. Up to four accounts are chips; more than that is a dropdown.
 */
@Composable
fun AccountSwitcher(state: HomeUiState, onSelect: (AccountSelection) -> Unit, modifier: Modifier = Modifier) {
    val spacing = CreatoraTheme.spacing
    val colors = CreatoraTheme.colors
    val grouped: Map<ConnectPlatform, List<ConnectedAccount>> = state.accounts.groupBy { it.platform }
    if (state.accounts.size < DROPDOWN_FROM) {
        Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(spacing.md), verticalAlignment = Alignment.CenterVertically) {
            if (state.accounts.size > 1) {
                ChoiceChip(stringResource(R.string.home_all_accounts), state.selection == AccountSelection.All, { onSelect(AccountSelection.All) })
            }
            grouped.forEach { (platform, accounts) ->
                Text(stringResource(platform.labelRes()), style = CreatoraTheme.typography.caption, color = colors.textMuted)
                accounts.forEach { account ->
                    ChoiceChip(account.title, state.selection == AccountSelection.One(account.key), { onSelect(AccountSelection.One(account.key)) })
                }
            }
        }
    } else {
        var open by remember { mutableStateOf(false) }
        val current = (state.selection as? AccountSelection.One)?.let { sel -> state.accounts.firstOrNull { it.key == sel.key }?.title } ?: stringResource(R.string.home_all_accounts)
        ChoiceChip(current, selected = true, onClick = { open = true }, modifier = modifier)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = colors.surface) {
            DropdownMenuItem(text = { Text(stringResource(R.string.home_all_accounts)) }, onClick = { open = false; onSelect(AccountSelection.All) })
            grouped.forEach { (platform, accounts) ->
                accounts.forEach { account ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.home_account_menu_item, stringResource(platform.labelRes()), account.title)) },
                        onClick = { open = false; onSelect(AccountSelection.One(account.key)) },
                    )
                }
            }
        }
    }
}
