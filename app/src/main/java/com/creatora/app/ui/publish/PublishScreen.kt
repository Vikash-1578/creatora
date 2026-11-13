package com.creatora.app.ui.publish

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.creatora.app.R
import com.creatora.app.domain.account.ConnectedAccount
import com.creatora.app.domain.publish.DestinationNote
import com.creatora.app.domain.publish.PlatformSpecs
import com.creatora.app.domain.publish.PubFix
import com.creatora.app.domain.publish.PubItem
import com.creatora.app.domain.publish.PublishGate
import com.creatora.app.domain.publish.TitleNote
import com.creatora.app.domain.publish.aspectOf
import com.creatora.app.domain.publish.fitIssues
import com.creatora.app.domain.publish.noteFor
import com.creatora.app.domain.score.CheckStatus
import com.creatora.app.ui.accounts.labelRes
import com.creatora.app.ui.ai.message
import com.creatora.app.ui.components.ChoiceChip
import com.creatora.app.ui.components.PrimaryButton
import com.creatora.app.ui.components.SecondaryButton
import com.creatora.app.ui.components.SectionHeader
import com.creatora.app.ui.components.StatusChip
import com.creatora.app.ui.components.SubScreenScaffold
import com.creatora.app.ui.components.Tone
import com.creatora.app.ui.home.BodyText
import com.creatora.app.ui.home.HomeCard
import com.creatora.app.ui.theme.CreatoraTheme
import java.io.File
import kotlinx.coroutines.launch

private enum class Section { DESTINATIONS, TITLE, DESCRIPTION, TAGS, THUMBNAIL, QUESTIONS, CHECKS, WHEN }

/** Publish: where, the details (all editable), Creatora's checks, and when. Nothing is posted before the button at the bottom. */
@Composable
fun PublishScreen(onBack: () -> Unit, onOpenThumbnail: (Long) -> Unit, onOpenCalendar: () -> Unit, modifier: Modifier = Modifier, viewModel: PublishViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.shares.collect { share -> shareVideo(context, share); viewModel.markShared(share.postId) }
    }
    PublishContent(
        state = state, onBack = onBack, modifier = modifier,
        actions = PublishActions(
            toggle = viewModel::toggle, title = viewModel::setTitle, description = viewModel::setDescription, hashtags = viewModel::setHashtags,
            tags = viewModel::setTags, regenerate = viewModel::regenerate, pick = viewModel::pickTitle, ai = viewModel::answerAi, kids = viewModel::answerKids,
            schedule = viewModel::setScheduledAt, bestTime = viewModel::useBestTime, makePublic = viewModel::setPublic, publish = viewModel::publish,
            thumbnail = { state.project?.id?.let(onOpenThumbnail) }, calendar = onOpenCalendar,
        ),
    )
}

class PublishActions(
    val toggle: (ConnectedAccount) -> Unit = {}, val title: (String) -> Unit = {}, val description: (String) -> Unit = {}, val hashtags: (String) -> Unit = {},
    val tags: (String) -> Unit = {}, val regenerate: () -> Unit = {}, val pick: (TitleOption) -> Unit = {}, val ai: (Boolean) -> Unit = {}, val kids: (Boolean) -> Unit = {},
    val schedule: (Long?) -> Unit = {}, val bestTime: () -> Unit = {}, val makePublic: (Boolean) -> Unit = {}, val publish: () -> Unit = {},
    val thumbnail: () -> Unit = {}, val calendar: () -> Unit = {},
)

private fun fixSection(fix: PubFix?): Section? = when (fix) {
    PubFix.TITLE -> Section.TITLE
    PubFix.DESCRIPTION -> Section.DESCRIPTION
    PubFix.TAGS -> Section.TAGS
    PubFix.THUMBNAIL -> Section.THUMBNAIL
    PubFix.AI_QUESTION, PubFix.KIDS_QUESTION -> Section.QUESTIONS
    PubFix.LICENCES, PubFix.RENDER, null -> null
}

@Composable
fun PublishContent(state: PublishUiState, onBack: () -> Unit, actions: PublishActions, modifier: Modifier = Modifier) {
    val spacing = CreatoraTheme.spacing
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val sections = Section.entries
    SubScreenScaffold(title = stringResource(R.string.publish_title), onBack = onBack, modifier = modifier) {
        LazyColumn(state = listState, modifier = Modifier.padding(horizontal = spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.xl)) {
            item { DestinationsCard(state, actions) }
            item { TitleCard(state, actions) }
            item { DescriptionCard(state, actions) }
            item { TagsCard(state, actions) }
            item { ThumbnailCard(state, actions) }
            item { QuestionsCard(state, actions) }
            item { ChecksCard(state) { fix -> fixSection(fix)?.let { target -> scope.launch { listState.animateScrollToItem(sections.indexOf(target)) } } } }
            item { WhenCard(state, actions) }
            item { PublishButton(state, actions) }
        }
    }
}

@Composable
private fun DestinationsCard(state: PublishUiState, actions: PublishActions) {
    val spacing = CreatoraTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        SectionHeader(stringResource(R.string.publish_where), icon = Icons.Outlined.Send, info = stringResource(R.string.publish_where_info))
        HomeCard {
            if (state.accounts.isEmpty()) BodyText(stringResource(R.string.publish_no_accounts))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                state.accounts.forEach { account ->
                    ChoiceChip("${stringResource(account.platform.labelRes())}: ${account.title}", account.key in state.selected, { actions.toggle(account) })
                }
            }
            state.accounts.filter { it.key in state.selected }.forEach { account ->
                val scheduled = state.scheduledAt != null
                val name = stringResource(account.platform.labelRes())
                when (noteFor(account, scheduled)) {
                    DestinationNote.ShareSheet -> BodyText(stringResource(R.string.publish_note_share, name))
                    DestinationNote.ReminderOnly -> BodyText(stringResource(R.string.publish_note_reminder, name))
                    DestinationNote.PrivateUntilApproved -> BodyText(stringResource(R.string.publish_note_private, name))
                    DestinationNote.None -> Unit
                }
                // What does not fit this platform is said with its limit; nothing is cut.
                val spec = PlatformSpecs.of(account.platform)
                if (spec != null) {
                    val seconds = state.project?.targetSeconds ?: 0
                    fitIssues(spec, seconds, aspectOf(state.project?.format), state.title, state.description, state.hashtags.split(' ').filter { it.isNotBlank() }).forEach { fit ->
                        StatusChip(fitText(name, fit), Tone.WARN)
                    }
                }
            }
            when (val gate = state.gate) {
                is PublishGate.TooManyDestinations -> StatusChip(pluralStringResource(R.plurals.publish_limit_destinations, gate.max, gate.max), Tone.WARN)
                is PublishGate.DailyLimit -> StatusChip(pluralStringResource(R.plurals.publish_limit_daily, gate.max, gate.max), Tone.WARN)
                else -> Unit
            }
        }
    }
}

@Composable
private fun CountText(used: Int, max: Int?) {
    val over = max != null && used > max
    Text(
        if (max != null) stringResource(R.string.publish_counter, used, max) else used.toString(),
        style = CreatoraTheme.typography.caption, color = if (over) CreatoraTheme.colors.danger else CreatoraTheme.colors.textMuted,
    )
}

@Composable
private fun AiButton(state: PublishUiState, actions: PublishActions) {
    val cost = com.creatora.app.domain.account.PlanLimits.costs.text
    SecondaryButton(stringResource(R.string.publish_regenerate), actions.regenerate, Modifier.fillMaxWidth(), enabled = !state.ai.busy && state.project != null)
    Text(pluralStringResource(R.plurals.create_credit_cost, cost, cost), style = CreatoraTheme.typography.caption, color = CreatoraTheme.colors.textMuted)
    state.ai.errors.forEach { Text(it.message(), style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.danger) }
}

@Composable
private fun TitleCard(state: PublishUiState, actions: PublishActions) {
    Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.md)) {
        SectionHeader(stringResource(R.string.publish_field_title), icon = Icons.Outlined.Title)
        HomeCard {
            OutlinedTextField(state.title, actions.title, Modifier.fillMaxWidth(), singleLine = false, label = { Text(stringResource(R.string.publish_field_title)) })
            CountText(state.title.length, 100)
            AiButton(state, actions)
            if (state.ai.options.isNotEmpty()) BodyText(stringResource(R.string.publish_options_note))
            state.ai.options.forEach { option ->
                Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.xs)) {
                    Text(option.title, style = CreatoraTheme.typography.cardTitle, color = CreatoraTheme.colors.text)
                    StatusChip(stringResource(R.string.publish_option_score, option.score.points), if (option.score.points >= 70) Tone.GOOD else Tone.WARN)
                    BodyText(option.score.reasons.map { stringResource(it.textRes()) }.joinToString(" · "))
                    SecondaryButton(stringResource(R.string.publish_use_title), { actions.pick(option) })
                }
            }
        }
    }
}

private fun TitleNote.textRes() = when (this) {
    TitleNote.GOOD_LENGTH -> R.string.title_note_length_ok
    TitleNote.TOO_SHORT -> R.string.title_note_short
    TitleNote.TOO_LONG_FOR_SEARCH -> R.string.title_note_long
    TitleNote.NOT_SHOUTING -> R.string.title_note_not_caps
    TitleNote.ALL_CAPS -> R.string.title_note_caps
    TitleNote.HAS_CUE -> R.string.title_note_cue
    TitleNote.HAS_KEYWORD -> R.string.title_note_keyword
    TitleNote.NO_KEYWORD -> R.string.title_note_no_keyword
}

@Composable
private fun DescriptionCard(state: PublishUiState, actions: PublishActions) {
    Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.md)) {
        SectionHeader(stringResource(R.string.publish_field_description), icon = Icons.Outlined.AutoAwesome, info = stringResource(R.string.publish_description_info))
        HomeCard {
            OutlinedTextField(state.description, actions.description, Modifier.fillMaxWidth(), minLines = 3, label = { Text(stringResource(R.string.publish_field_description)) })
            CountText(state.description.length, 5000)
        }
    }
}

@Composable
private fun TagsCard(state: PublishUiState, actions: PublishActions) {
    Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.md)) {
        SectionHeader(stringResource(R.string.publish_field_tags), icon = Icons.Outlined.Checklist)
        HomeCard {
            OutlinedTextField(state.hashtags, actions.hashtags, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.publish_field_hashtags)) })
            OutlinedTextField(state.tags, actions.tags, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.publish_field_tag_list)) })
        }
    }
}

@Composable
private fun ThumbnailCard(state: PublishUiState, actions: PublishActions) {
    Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.md)) {
        SectionHeader(stringResource(R.string.publish_field_thumbnail), icon = Icons.Outlined.Image)
        HomeCard {
            state.project?.thumbnailUri?.let { AsyncImage(model = it, contentDescription = stringResource(R.string.publish_thumbnail_description), modifier = Modifier.fillMaxWidth()) }
                ?: BodyText(stringResource(R.string.publish_thumbnail_none))
            SecondaryButton(stringResource(if (state.project?.thumbnailUri != null) R.string.publish_thumbnail_change else R.string.publish_thumbnail_make), actions.thumbnail)
        }
    }
}

@Composable
private fun QuestionsCard(state: PublishUiState, actions: PublishActions) {
    Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.md)) {
        SectionHeader(stringResource(R.string.publish_questions), icon = Icons.Outlined.Checklist, info = stringResource(R.string.publish_questions_info))
        HomeCard {
            Text(stringResource(R.string.publish_q_ai), style = CreatoraTheme.typography.cardTitle, color = CreatoraTheme.colors.text)
            if (state.aiContent == null && state.suggestAi) BodyText(stringResource(R.string.publish_q_ai_suggest))
            YesNo(state.aiContent, actions.ai)
            Text(stringResource(R.string.publish_q_kids), style = CreatoraTheme.typography.cardTitle, color = CreatoraTheme.colors.text)
            YesNo(state.madeForKids, actions.kids)
        }
    }
}

@Composable
private fun YesNo(answer: Boolean?, onAnswer: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
        ChoiceChip(stringResource(R.string.publish_yes), answer == true, { onAnswer(true) })
        ChoiceChip(stringResource(R.string.publish_no), answer == false, { onAnswer(false) })
    }
}

@Composable
private fun ChecksCard(state: PublishUiState, onFix: (PubFix?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.md)) {
        SectionHeader(stringResource(R.string.publish_checks), icon = Icons.Outlined.Checklist, info = stringResource(R.string.publish_checks_info))
        HomeCard {
            state.checks.forEach { item ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.md)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.xs)) {
                        Text(stringResource(item.check.labelRes()), style = CreatoraTheme.typography.cardTitle, color = CreatoraTheme.colors.text)
                        StatusChip(stringResource(item.statusText()), item.statusTone())
                        BodyText(item.reasonText())
                    }
                    if (item.fix != null && item.status != CheckStatus.PASS) SecondaryButton(stringResource(R.string.score_fix), { onFix(item.fix) })
                }
            }
        }
    }
}

@Composable
private fun WhenCard(state: PublishUiState, actions: PublishActions) {
    var dialog by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.md)) {
        SectionHeader(stringResource(R.string.publish_when), icon = Icons.Outlined.CalendarMonth)
        HomeCard {
            Row(horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
                ChoiceChip(stringResource(R.string.publish_now), state.scheduledAt == null, { actions.schedule(null) })
                ChoiceChip(stringResource(R.string.publish_schedule), state.scheduledAt != null, { dialog = true })
            }
            state.scheduledAt?.let {
                BodyText(stringResource(R.string.publish_scheduled_for, DateUtils.formatDateTime(context, it, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_WEEKDAY), java.util.TimeZone.getDefault().getDisplayName(false, java.util.TimeZone.SHORT)))
            }
            state.bestTime?.let { best ->
                BodyText(bestText(best))
                state.bestAtMillis?.let { SecondaryButton(stringResource(R.string.publish_use_best), actions.bestTime) }
            }
            if (state.accounts.any { it.key in state.selected && it.platform == com.creatora.app.domain.account.ConnectPlatform.YOUTUBE }) {
                Text(stringResource(R.string.publish_public_question), style = CreatoraTheme.typography.cardTitle, color = CreatoraTheme.colors.text)
                Row(horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
                    ChoiceChip(stringResource(R.string.publish_private), !state.makePublic, { actions.makePublic(false) })
                    ChoiceChip(stringResource(R.string.publish_public), state.makePublic, { actions.makePublic(true) })
                }
            }
        }
    }
    if (dialog) ScheduleDialog(initial = state.scheduledAt, onDismiss = { dialog = false }, onPicked = { dialog = false; actions.schedule(it) })
}

@Composable
private fun PublishButton(state: PublishUiState, actions: PublishActions) {
    val names = state.accounts.filter { it.key in state.selected }.map { stringResource(it.platform.labelRes()) }.joinToString(", ")
    val label = when {
        names.isEmpty() -> stringResource(R.string.publish_choose_first)
        state.scheduledAt != null -> stringResource(R.string.publish_button_schedule, names)
        else -> stringResource(R.string.publish_button_now, names)
    }
    Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
        PrimaryButton(label, actions.publish, Modifier.fillMaxWidth(), enabled = state.phase != PublishPhase.Working)
        when (val phase = state.phase) {
            is PublishPhase.Failed -> Text(stringResource(phase.reason.textRes()), style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.danger)
            is PublishPhase.Started -> {
                Text(pluralStringResource(R.plurals.publish_started, phase.count, phase.count), style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.success)
                SecondaryButton(stringResource(R.string.publish_open_calendar), actions.calendar)
            }
            else -> Unit
        }
        BodyText(stringResource(R.string.publish_honesty))
    }
}

private fun PublishProblem.textRes() = when (this) {
    PublishProblem.NOT_SIGNED_IN -> R.string.publish_problem_signed_in
    PublishProblem.NOTHING_CHOSEN -> R.string.publish_choose_first
    PublishProblem.PLAN_DESTINATIONS -> R.string.publish_problem_plan_destinations
    PublishProblem.PLAN_DAILY -> R.string.publish_problem_plan_daily
    PublishProblem.CHECKS -> R.string.publish_problem_checks
    PublishProblem.SAVE_FAILED -> R.string.publish_problem_save
    PublishProblem.UPLOAD_PERMISSION -> R.string.publish_problem_permission
    PublishProblem.PAST_TIME -> R.string.publish_problem_past
}

/** Copies the caption to the clipboard and opens the share sheet with the finished video (Creatora cannot see the result). */
private fun shareVideo(context: Context, share: ShareRequest) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("caption", share.caption))
    val file = share.videoUri?.let { Uri.parse(it).path }?.let(::File)?.takeIf { it.exists() } ?: return
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_TEXT, share.caption)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Preview(name = "Publish light", showBackground = true, heightDp = 1800)
@Composable
private fun PublishLightPreview() {
    CreatoraTheme(darkTheme = false) { PublishContent(PublishUiState(), {}, PublishActions()) }
}

@Preview(name = "Publish dark", showBackground = true, heightDp = 1800)
@Composable
private fun PublishDarkPreview() {
    CreatoraTheme(darkTheme = true) { PublishContent(PublishUiState(), {}, PublishActions()) }
}
