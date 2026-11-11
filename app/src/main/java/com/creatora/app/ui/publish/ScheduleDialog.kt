package com.creatora.app.ui.publish

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.creatora.app.R
import com.creatora.app.domain.model.Platform
import com.creatora.app.domain.model.Project
import com.creatora.app.ui.components.ChoiceChip
import com.creatora.app.ui.components.SecondaryButton
import com.creatora.app.ui.theme.CreatoraTheme
import java.util.Calendar

fun Platform.labelRes() = when (this) {
    Platform.YOUTUBE -> R.string.publish_platform_youtube
    Platform.INSTAGRAM -> R.string.publish_platform_instagram
}

/** Tomorrow at 9:00, a sensible first suggestion for a reminder. */
private fun defaultTime(): Long = Calendar.getInstance().apply {
    add(Calendar.DAY_OF_YEAR, 1)
    set(Calendar.HOUR_OF_DAY, 9)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

/**
 * Pick a platform, a date and a time for a reminder (and a project when [projects] is given), using the
 * system date and time pickers. On Android 13+ it also asks to allow notifications.
 */
@Composable
fun ScheduleDialog(
    projects: List<Project>?,
    onConfirm: (projectId: Long?, platform: Platform, atMillis: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var time by rememberSaveable { mutableLongStateOf(defaultTime()) }
    var platform by rememberSaveable { mutableStateOf(Platform.YOUTUBE) }
    var projectId by rememberSaveable { mutableStateOf(projects?.firstOrNull()?.id) }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    fun pickDate() {
        val c = Calendar.getInstance().apply { timeInMillis = time }
        DatePickerDialog(context, { _, y, m, d ->
            time = Calendar.getInstance().apply { timeInMillis = time; set(y, m, d) }.timeInMillis
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    fun pickTime() {
        val c = Calendar.getInstance().apply { timeInMillis = time }
        TimePickerDialog(context, { _, h, min ->
            time = Calendar.getInstance().apply { timeInMillis = time; set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, min) }.timeInMillis
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), DateFormat.is24HourFormat(context)).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.schedule_title), style = CreatoraTheme.typography.sectionTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.md)) {
                if (projects != null) {
                    Text(stringResource(R.string.schedule_project), style = CreatoraTheme.typography.bodySemiBold, color = CreatoraTheme.colors.text)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
                        projects.forEach { project ->
                            ChoiceChip(project.publishTitle.ifBlank { project.title }, projectId == project.id, { projectId = project.id })
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
                    Platform.entries.forEach { ChoiceChip(stringResource(it.labelRes()), platform == it, { platform = it }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
                    SecondaryButton(DateUtils.formatDateTime(context, time, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_ALL), ::pickDate)
                    SecondaryButton(DateUtils.formatDateTime(context, time, DateUtils.FORMAT_SHOW_TIME), ::pickTime)
                }
                if (time <= System.currentTimeMillis()) {
                    Text(stringResource(R.string.schedule_in_past), style = CreatoraTheme.typography.caption, color = CreatoraTheme.colors.danger)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    if (needsPermission) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    onConfirm(projectId, platform, time)
                },
                enabled = time > System.currentTimeMillis() && (projects == null || projectId != null),
            ) { Text(stringResource(R.string.schedule_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        containerColor = CreatoraTheme.colors.surface,
    )
}
