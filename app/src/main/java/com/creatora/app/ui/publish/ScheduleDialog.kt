package com.creatora.app.ui.publish

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.creatora.app.R
import com.creatora.app.ui.theme.CreatoraTheme
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Date, then time, in the phone's time zone (shown on the screen that opens this). Calls [onPicked] with the moment in epoch milliseconds. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleDialog(initial: Long?, onDismiss: () -> Unit, onPicked: (Long) -> Unit) {
    val zone = ZoneId.systemDefault()
    val start = initial?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone) } ?: LocalDateTime.now(zone).plusHours(2)
    val date = rememberDatePickerState(initialSelectedDateMillis = start.toLocalDate().atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli())
    val time = rememberTimePickerState(initialHour = start.hour, initialMinute = start.minute, is24Hour = false)
    var pickingTime by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CreatoraTheme.colors.surface,
        title = { Text(stringResource(if (pickingTime) R.string.publish_pick_time else R.string.publish_pick_date), style = CreatoraTheme.typography.sectionTitle) },
        text = {
            Column {
                if (pickingTime) TimePicker(time) else DatePicker(date, showModeToggle = false)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (!pickingTime) {
                    pickingTime = true
                } else {
                    // The date picker answers in UTC midnight of the chosen day; read its calendar day, then apply the time in the local zone.
                    val day = Instant.ofEpochMilli(date.selectedDateMillis ?: return@TextButton).atZone(ZoneId.of("UTC")).toLocalDate()
                    onPicked(day.atTime(time.hour, time.minute).atZone(zone).toInstant().toEpochMilli())
                }
            }) { Text(stringResource(if (pickingTime) R.string.action_done else R.string.action_continue)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
