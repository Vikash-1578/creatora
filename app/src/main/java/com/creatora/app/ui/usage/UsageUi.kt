package com.creatora.app.ui.usage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.creatora.app.R
import com.creatora.app.domain.account.UsageStatus
import com.creatora.app.ui.theme.CreatoraTheme

/** The usage bar and numbers. */
@Composable
fun UsageMeter(status: UsageStatus, modifier: Modifier = Modifier) {
    val colors = CreatoraTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
        Text(stringResource(R.string.usage_numbers, status.used, status.limit, status.percent), style = CreatoraTheme.typography.body, color = colors.text)
        LinearProgressIndicator(
            progress = { status.percent / 100f },
            modifier = Modifier.fillMaxWidth(),
            color = if (status.percent > 90) colors.warning else colors.accent,
            trackColor = colors.surfaceAlt,
        )
    }
}

/** Stays at the top from 91% to 99% and shows the current percent. Tapping opens Billing and usage. */
@Composable
fun UsageBanner(status: UsageStatus, onOpenPlan: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(CreatoraTheme.colors.inverse)
            .heightIn(min = CreatoraTheme.dimens.minTouchTarget)
            .clickable(role = Role.Button, onClick = onOpenPlan)
            .padding(horizontal = CreatoraTheme.spacing.lg, vertical = CreatoraTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.md),
    ) {
        Text(
            stringResource(R.string.usage_banner, status.percent),
            style = CreatoraTheme.typography.bodySemiBold,
            color = CreatoraTheme.colors.onInverse,
            modifier = Modifier.weight(1f),
        )
        Text(stringResource(R.string.usage_see_plan), style = CreatoraTheme.typography.bodySemiBold, color = CreatoraTheme.colors.onInverse)
    }
}

@Composable
fun UsageDialog(status: UsageStatus, onSeePlan: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.usage_dialog_title, status.percent), style = CreatoraTheme.typography.sectionTitle) },
        text = { Text(stringResource(R.string.usage_dialog_body, status.used, status.limit), style = CreatoraTheme.typography.body) },
        confirmButton = { TextButton(onClick = onSeePlan) { Text(stringResource(R.string.usage_see_plan)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_not_now)) } },
        containerColor = CreatoraTheme.colors.surface,
    )
}
