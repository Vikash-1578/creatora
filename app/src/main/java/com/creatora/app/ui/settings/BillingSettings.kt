package com.creatora.app.ui.settings

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.creatora.app.R
import com.creatora.app.domain.account.Plan
import com.creatora.app.domain.account.UsageLevel
import com.creatora.app.domain.account.usageStatus
import com.creatora.app.ui.components.InfoListRow
import com.creatora.app.ui.components.ListGroup
import com.creatora.app.ui.components.PrimaryButton
import com.creatora.app.ui.components.SecondaryButton
import com.creatora.app.ui.components.SectionTitle
import com.creatora.app.ui.components.SubScreenScaffold
import com.creatora.app.ui.theme.CreatoraTheme
import com.creatora.app.ui.usage.UsageMeter

private const val PLAY_SUBSCRIPTIONS = "https://play.google.com/store/account/subscriptions"

@Composable
fun BillingSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: BillingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    BillingContent(
        state, viewModel::upgrade,
        onManage = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_SUBSCRIPTIONS))) } },
        onBack = onBack, modifier = modifier,
    )
}

@Composable
fun BillingContent(state: BillingUiState, onUpgrade: () -> Unit, onManage: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = CreatoraTheme.colors
    SubScreenScaffold(title = stringResource(R.string.settings_billing), onBack = onBack, modifier = modifier) {
        ListGroup {
            InfoListRow(stringResource(R.string.account_plan), stringResource(if (state.plan == Plan.PRO) R.string.plan_pro_name else R.string.plan_free_name))
        }
        Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
            SectionTitle(stringResource(R.string.usage_title))
            UsageMeter(state.usage)
            Text(stringResource(R.string.usage_resets), style = CreatoraTheme.typography.caption, color = colors.textMuted)
            if (state.usage.level == UsageLevel.BLOCKED) {
                Text(stringResource(R.string.usage_blocked), style = CreatoraTheme.typography.body, color = colors.danger)
            }
        }
        if (state.plan == Plan.FREE) {
            Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
                SectionTitle(stringResource(R.string.plan_pro_name))
                Text(stringResource(R.string.plan_pro_features, Plan.PRO.aiLimit, Plan.PRO.maxAccounts), style = CreatoraTheme.typography.body, color = colors.textMuted)
                PrimaryButton(
                    text = state.billing.price?.let { stringResource(R.string.billing_upgrade_price, it) } ?: stringResource(R.string.billing_upgrade),
                    onClick = onUpgrade,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.billing.available,
                )
                if (!state.billing.available || state.upgradeUnavailable) {
                    Text(stringResource(R.string.billing_unavailable), style = CreatoraTheme.typography.caption, color = colors.warning)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
            SectionTitle(stringResource(R.string.billing_history))
            Text(stringResource(R.string.billing_history_body), style = CreatoraTheme.typography.body, color = colors.textMuted)
            SecondaryButton(stringResource(R.string.billing_manage), onManage, Modifier.fillMaxWidth())
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun BillingLightPreview() {
    CreatoraTheme(darkTheme = false) { BillingContent(BillingUiState(usage = usageStatus(46, 50)), {}, {}, {}) }
}

@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BillingDarkPreview() {
    CreatoraTheme(darkTheme = true) { BillingContent(BillingUiState(), {}, {}, {}) }
}
