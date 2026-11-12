package com.creatora.app.ui.home

import android.text.format.DateUtils
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.creatora.app.R
import com.creatora.app.domain.account.Goal
import com.creatora.app.domain.account.GoalMetric
import com.creatora.app.domain.home.GoalMissing
import com.creatora.app.ui.ai.message
import com.creatora.app.ui.components.ChoiceChip
import com.creatora.app.ui.components.PrimaryButton
import com.creatora.app.ui.components.SecondaryButton
import com.creatora.app.ui.theme.CreatoraTheme

private fun GoalMetric.labelRes() = when (this) {
    GoalMetric.SUBSCRIBERS -> R.string.goal_metric_subscribers
    GoalMetric.VIEWS -> R.string.goal_metric_views
    GoalMetric.UPLOADS -> R.string.goal_metric_uploads
    GoalMetric.EARNINGS -> R.string.goal_metric_earnings
}

private const val DAY_MILLIS = 24L * 60 * 60 * 1000

/** Goals with a real progress bar, edit and delete, a celebration when one is reached, and the weekly plan. */
@Composable
fun GoalsSection(state: GoalsState, canAdd: Boolean, onAdd: () -> Unit, onEdit: (Goal) -> Unit, onDelete: (Goal) -> Unit, onPlan: (GoalItem, Boolean) -> Unit) {
    HomeSection(R.string.home_goals_title, icon = Icons.Outlined.Flag) {
        if (state.goals.isEmpty() && !state.loading) HomeCard { BodyText(stringResource(R.string.home_goals_empty)) }
        state.goals.forEach { item ->
            val goal = item.goal
            HomeCard {
                Text(
                    stringResource(R.string.home_goal_line, stringResource(goal.metric.labelRes()), goalText(goal.target)),
                    style = CreatoraTheme.typography.cardTitle, color = CreatoraTheme.colors.text,
                )
                val daysLeft = ((goal.deadline - System.currentTimeMillis()) / DAY_MILLIS).coerceAtLeast(0)
                Text(
                    if (goal.reachedAt != null) stringResource(R.string.home_goal_reached) else stringResource(R.string.home_goal_days_left, daysLeft.toInt()),
                    style = CreatoraTheme.typography.caption, color = CreatoraTheme.colors.textMuted,
                )
                val progress = item.progress
                if (progress?.fraction != null) {
                    LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth(), color = CreatoraTheme.colors.accent, trackColor = CreatoraTheme.colors.surfaceAlt)
                    Text(stringResource(R.string.home_goal_progress, goalText(progress.current ?: 0), goalText(goal.target), (progress.fraction * 100).toInt()), style = CreatoraTheme.typography.caption, color = CreatoraTheme.colors.textMuted)
                } else if (progress?.missing != null) {
                    BodyText(stringResource(if (progress.missing == GoalMissing.NO_START_VALUE) R.string.home_goal_no_start else R.string.home_goal_no_data))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
                    TextButton(onClick = { onEdit(goal) }) { Text(stringResource(R.string.action_edit)) }
                    TextButton(onClick = { onDelete(goal) }) { Text(stringResource(R.string.action_delete)) }
                    if (goal.primary) TextButton(onClick = { onPlan(item, false) }) { Text(stringResource(R.string.home_goal_plan)) }
                }
                if (goal.primary) {
                    when (val plan = state.plan) {
                        PlanState.None -> Unit
                        PlanState.Loading -> LoadingRow(R.string.home_loading)
                        is PlanState.Ready -> if (plan.goalId == goal.id) {
                            Text(stringResource(R.string.home_plan_title), style = CreatoraTheme.typography.bodySemiBold, color = CreatoraTheme.colors.text)
                            BodyText(plan.text, muted = false)
                            TextButton(onClick = { onPlan(item, true) }) { Text(stringResource(R.string.home_plan_refresh)) }
                        }
                        is PlanState.Failed -> plan.errors.forEach { Text(it.message(), style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.danger) }
                    }
                }
            }
        }
        if (canAdd) SecondaryButton(stringResource(R.string.home_goal_add), onAdd, Modifier.fillMaxWidth())
        BodyText(stringResource(R.string.home_goals_note))
    }
}

/** A goal number with grouping (and lakh or crore in Hindi and Hinglish), like "10,000,000 (1 crore)". */
@Composable
fun goalText(value: Long): String =
    com.creatora.app.domain.home.goalNumber(value, java.util.Locale.getDefault(), stringResource(R.string.unit_lakh), stringResource(R.string.unit_crore))

/** A goal that looks out of reach: a kind note and a target that fits, before anything is saved. */
@Composable
fun GoalAdviceDialog(advice: GoalAdvice?, onAnswer: (Boolean) -> Unit, onDismiss: () -> Unit) {
    if (advice == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.goal_advice_title), style = CreatoraTheme.typography.sectionTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
                if (advice.loading) LoadingRow(R.string.home_loading)
                BodyText(advice.message ?: stringResource(R.string.goal_advice_static, goalText(advice.target), goalText(advice.suggested)), muted = false)
                Text(stringResource(R.string.goal_advice_suggested, goalText(advice.suggested)), style = CreatoraTheme.typography.bodySemiBold, color = CreatoraTheme.colors.accentText)
            }
        },
        confirmButton = { TextButton(onClick = { onAnswer(true) }, enabled = !advice.loading) { Text(stringResource(R.string.goal_advice_use)) } },
        dismissButton = { TextButton(onClick = { onAnswer(false) }, enabled = !advice.loading) { Text(stringResource(R.string.goal_advice_keep)) } },
        containerColor = CreatoraTheme.colors.surface,
    )
}

/** Add or edit a goal: what to measure, the target and how many days it runs. */
@Composable
fun GoalDialog(existing: Goal?, onSave: (GoalMetric, Long, Int) -> Unit, onDismiss: () -> Unit) {
    var metric by rememberSaveable { mutableStateOf(existing?.metric ?: GoalMetric.SUBSCRIBERS) }
    var target by rememberSaveable { mutableStateOf(existing?.target?.toString().orEmpty()) }
    var days by rememberSaveable { mutableStateOf(existing?.let { ((it.deadline - it.startedAt) / DAY_MILLIS).toInt() } ?: 30) }
    val targetNumber = target.toLongOrNull()?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing == null) R.string.home_goal_add else R.string.home_goal_edit), style = CreatoraTheme.typography.sectionTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
                if (existing == null) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
                        GoalMetric.entries.forEach { ChoiceChip(stringResource(it.labelRes()), it == metric, { metric = it }) }
                    }
                }
                OutlinedTextField(
                    target, { target = it.filter { c -> c.isDigit() }.take(12) }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.goal_target_label)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
                    listOf(7, 30, 90).forEach { ChoiceChip(stringResource(R.string.home_goal_days, it), it == days, { days = it }) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { targetNumber?.let { onSave(metric, it, days) } }, enabled = targetNumber != null) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        containerColor = CreatoraTheme.colors.surface,
    )
}

/** Shown once when a goal is reached. It says well done; it does not claim anything about the future. */
@Composable
fun CelebrationDialog(goal: Goal?, onDismiss: () -> Unit) {
    if (goal == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🎉 " + stringResource(R.string.home_goal_celebrate_title), style = CreatoraTheme.typography.sectionTitle) },
        text = { BodyText(stringResource(R.string.home_goal_celebrate_body, stringResource(goal.metric.labelRes()), goalText(goal.target)), muted = false) },
        confirmButton = { PrimaryButton(stringResource(R.string.action_close), onDismiss) },
        containerColor = CreatoraTheme.colors.surface,
    )
}
