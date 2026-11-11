package com.creatora.app.ui.notifications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import com.creatora.app.R
import com.creatora.app.data.notify.nameRes
import com.creatora.app.domain.account.NotificationCategory
import com.creatora.app.domain.account.NotificationPrefs
import com.creatora.app.ui.components.ListGroup
import com.creatora.app.ui.components.ListRowDivider
import com.creatora.app.ui.theme.CreatoraTheme

private fun NotificationCategory.hintRes(): Int = when (this) {
    NotificationCategory.DAILY_IDEAS -> R.string.notify_daily_ideas_hint
    NotificationCategory.RENDER_DONE -> R.string.notify_render_done_hint
    NotificationCategory.NEW_COMMENTS -> R.string.notify_new_comments_hint
    NotificationCategory.VIEW_MILESTONES -> R.string.notify_view_milestones_hint
    NotificationCategory.EARNINGS -> R.string.notify_earnings_hint
    NotificationCategory.PLAN_USAGE -> R.string.notify_plan_usage_hint
}

/** One switch per category, plus the milestone size. Shared by onboarding and Settings. */
@Composable
fun NotificationToggles(
    prefs: NotificationPrefs,
    onToggle: (NotificationCategory, Boolean) -> Unit,
    onMilestone: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = CreatoraTheme.spacing
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        ListGroup {
            NotificationCategory.entries.forEachIndexed { index, category ->
                if (index > 0) ListRowDivider()
                val on = prefs.isOn(category)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = CreatoraTheme.dimens.minTouchTarget)
                        .clickable(role = Role.Switch) { onToggle(category, !on) }
                        .padding(horizontal = spacing.lg, vertical = spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(category.nameRes()), style = CreatoraTheme.typography.bodySemiBold, color = CreatoraTheme.colors.text)
                        Text(stringResource(category.hintRes()), style = CreatoraTheme.typography.caption, color = CreatoraTheme.colors.textMuted)
                    }
                    Switch(checked = on, onCheckedChange = null)
                }
            }
        }
        var text by remember(prefs.milestoneViews) { mutableStateOf(prefs.milestoneViews.toString()) }
        OutlinedTextField(
            value = text,
            onValueChange = { value ->
                text = value.filter { it.isDigit() }
                text.toLongOrNull()?.takeIf { it > 0 }?.let(onMilestone)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.notify_milestone_label)) },
            supportingText = { Text(stringResource(R.string.notify_milestone_support)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            enabled = prefs.isOn(NotificationCategory.VIEW_MILESTONES),
        )
    }
}
