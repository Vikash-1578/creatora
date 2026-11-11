package com.creatora.app.ui.settings

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.creatora.app.R
import com.creatora.app.domain.account.Plan
import com.creatora.app.domain.repository.AuthUser
import com.creatora.app.ui.accounts.Avatar
import com.creatora.app.ui.components.InfoListRow
import com.creatora.app.ui.components.ListGroup
import com.creatora.app.ui.components.ListRowDivider
import com.creatora.app.ui.components.SecondaryButton
import com.creatora.app.ui.components.SubScreenScaffold
import com.creatora.app.ui.theme.CreatoraTheme

@Composable
fun AccountSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: AccountViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AccountContent(state, viewModel::signOut, viewModel::askDelete, viewModel::deleteAccount, onBack, modifier)
}

@Composable
fun AccountContent(
    state: AccountUiState,
    onSignOut: () -> Unit,
    onAskDelete: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CreatoraTheme.colors
    SubScreenScaffold(title = stringResource(R.string.settings_account), onBack = onBack, modifier = modifier) {
        state.user?.let { user ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.lg)) {
                Avatar(user.photoUrl, user.name.ifBlank { user.email })
                Column {
                    Text(user.name.ifBlank { stringResource(R.string.account_no_name) }, style = CreatoraTheme.typography.sectionTitle, color = colors.text)
                    Text(user.email, style = CreatoraTheme.typography.caption, color = colors.textMuted)
                }
            }
            ListGroup {
                InfoListRow(stringResource(R.string.account_plan), stringResource(if (state.plan == Plan.PRO) R.string.plan_pro_name else R.string.plan_free_name))
                ListRowDivider()
                InfoListRow(stringResource(R.string.account_signed_in_with), stringResource(R.string.account_google))
            }
        } ?: Text(stringResource(R.string.account_signed_out), style = CreatoraTheme.typography.body, color = colors.textMuted)
        SecondaryButton(stringResource(R.string.account_sign_out), onSignOut, Modifier.fillMaxWidth(), enabled = state.user != null && !state.working)
        Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
            Text(stringResource(R.string.account_delete_body), style = CreatoraTheme.typography.caption, color = colors.textMuted)
            SecondaryButton(stringResource(R.string.account_delete), { onAskDelete(true) }, Modifier.fillMaxWidth(), enabled = state.user != null && !state.working)
            if (state.needsRecentSignIn) Text(stringResource(R.string.account_delete_recent), style = CreatoraTheme.typography.body, color = colors.warning)
            if (state.deleteFailed) Text(stringResource(R.string.account_delete_failed), style = CreatoraTheme.typography.body, color = colors.danger)
        }
    }
    if (state.confirmDelete) {
        AlertDialog(
            onDismissRequest = { onAskDelete(false) },
            title = { Text(stringResource(R.string.account_delete_confirm_title), style = CreatoraTheme.typography.sectionTitle) },
            text = { Text(stringResource(R.string.account_delete_confirm_body), style = CreatoraTheme.typography.body) },
            confirmButton = { TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete), color = colors.danger) } },
            dismissButton = { TextButton(onClick = { onAskDelete(false) }) { Text(stringResource(R.string.action_cancel)) } },
            containerColor = colors.surface,
        )
    }
}

private val PreviewState = AccountUiState(user = AuthUser("1", "Asha Rao", "asha@example.com", null), plan = Plan.FREE)

@Preview(name = "Light", showBackground = true)
@Composable
private fun AccountLightPreview() {
    CreatoraTheme(darkTheme = false) { AccountContent(PreviewState, {}, {}, {}, {}) }
}

@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AccountDarkPreview() {
    CreatoraTheme(darkTheme = true) { AccountContent(AccountUiState(), {}, {}, {}, {}) }
}
