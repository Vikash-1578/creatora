package com.creatora.app.ui.calendar

import android.content.res.Configuration
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.Text
import com.creatora.app.R
import com.creatora.app.domain.model.Platform
import com.creatora.app.domain.model.ScheduledPost
import com.creatora.app.ui.components.EmptyState
import com.creatora.app.ui.components.PrimaryButton
import com.creatora.app.ui.components.SectionTitle
import com.creatora.app.ui.components.SubScreenScaffold
import com.creatora.app.ui.library.MenuAction
import com.creatora.app.ui.library.SimpleItemRow
import com.creatora.app.ui.publish.ScheduleDialog
import com.creatora.app.ui.publish.labelRes
import com.creatora.app.ui.theme.CreatoraTheme

@Composable
fun CalendarScreen(
    onBack: () -> Unit,
    onOpenPublish: (projectId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CalendarContent(state, onBack, onOpenPublish, viewModel::schedule, viewModel::remove, modifier)
}

@Composable
fun CalendarContent(
    state: CalendarUiState,
    onBack: () -> Unit,
    onOpenPublish: (projectId: Long) -> Unit,
    onSchedule: (projectId: Long, platform: Platform, atMillis: Long) -> Unit,
    onRemove: (ScheduledPost) -> Unit,
    modifier: Modifier = Modifier,
) {
    var scheduling by rememberSaveable { mutableStateOf(false) }
    SubScreenScaffold(stringResource(R.string.calendar_title), onBack, modifier) {
        PrimaryButton(stringResource(R.string.calendar_add), { scheduling = true }, enabled = state.schedulable.isNotEmpty())
        if (state.schedulable.isEmpty()) {
            Text(stringResource(R.string.calendar_needs_render), style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.textMuted)
        }
        if (state.upcoming.isEmpty() && state.past.isEmpty()) {
            EmptyState(Icons.Outlined.CalendarMonth, stringResource(R.string.calendar_empty_title), stringResource(R.string.calendar_empty_body))
        }
        Section(R.string.calendar_upcoming, state.upcoming, onOpenPublish, onRemove)
        Section(R.string.calendar_past, state.past, onOpenPublish, onRemove)
    }
    if (scheduling) {
        ScheduleDialog(
            projects = state.schedulable,
            onConfirm = { projectId, platform, at ->
                projectId?.let { onSchedule(it, platform, at) }
                scheduling = false
            },
            onDismiss = { scheduling = false },
        )
    }
}

@Composable
private fun Section(
    titleRes: Int,
    entries: List<CalendarEntry>,
    onOpen: (Long) -> Unit,
    onRemove: (ScheduledPost) -> Unit,
) {
    if (entries.isEmpty()) return
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
        SectionTitle(stringResource(titleRes))
        entries.forEach { entry ->
            val post = entry.post
            SimpleItemRow(
                title = entry.projectTitle ?: stringResource(R.string.calendar_deleted_project),
                subtitle = stringResource(
                    R.string.publish_post_row,
                    stringResource(post.platform.labelRes()),
                    DateUtils.formatDateTime(context, post.scheduledAt, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_ALL),
                ),
                actions = listOfNotNull(
                    if (entry.projectTitle != null) MenuAction(stringResource(R.string.calendar_open)) { onOpen(post.projectId) } else null,
                    MenuAction(stringResource(R.string.action_remove)) { onRemove(post) },
                ),
            )
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun CalendarLightPreview() {
    CreatoraTheme(darkTheme = false) { CalendarContent(CalendarUiState(), {}, {}, { _, _, _ -> }, {}) }
}

@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CalendarDarkPreview() {
    CreatoraTheme(darkTheme = true) { CalendarContent(CalendarUiState(), {}, {}, { _, _, _ -> }, {}) }
}
