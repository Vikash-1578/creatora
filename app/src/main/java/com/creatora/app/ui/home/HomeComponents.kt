package com.creatora.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import com.creatora.app.R
import com.creatora.app.domain.account.AdapterError
import com.creatora.app.domain.home.Change
import com.creatora.app.ui.theme.CreatoraTheme

@Composable
fun HomeCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = CreatoraTheme.shapes.radius16
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CreatoraTheme.colors.surface)
            .border(CreatoraTheme.dimens.borderWidth, CreatoraTheme.colors.border, shape)
            .padding(CreatoraTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm),
    ) { content() }
}

/** A titled section of Home. */
@Composable
fun HomeSection(titleRes: Int, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
            Text(stringResource(titleRes), style = CreatoraTheme.typography.sectionTitle, color = CreatoraTheme.colors.text, modifier = Modifier.weight(1f))
            trailing()
        }
        content()
    }
}

@Composable
fun BodyText(text: String, modifier: Modifier = Modifier, muted: Boolean = true) {
    Text(text, modifier = modifier, style = CreatoraTheme.typography.body, color = if (muted) CreatoraTheme.colors.textMuted else CreatoraTheme.colors.text)
}

@Composable
fun LoadingRow(textRes: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.md)) {
        CircularProgressIndicator(Modifier.size(CreatoraTheme.dimens.icon))
        BodyText(stringResource(textRes))
    }
}

/** Why data is missing, in plain words. */
@Composable
fun AdapterError.message(): String {
    // When the platform gave a reason, say it: "not available" alone does not help anyone fix it.
    if (this is AdapterError.NotAvailable && reason.isNotBlank()) return stringResource(R.string.home_error_platform_said, reason)
    return stringResource(
    when (this) {
        AdapterError.NotAuthorized -> R.string.home_error_not_authorized
        AdapterError.Network -> R.string.home_error_network
        AdapterError.QuotaExceeded -> R.string.home_error_quota
        AdapterError.NotFound -> R.string.home_error_not_found
        AdapterError.PermissionMissing -> R.string.home_error_permission
        AdapterError.ComingSoon -> R.string.home_error_coming_soon
        is AdapterError.NotAvailable -> R.string.home_error_not_available
    },
    )
}

/** "↑ 1.2K (+12%) vs the previous 28 days", coloured by direction; or that there is no previous period. */
@Composable
fun ChangeText(change: Change, formatDelta: @Composable (Double) -> String, modifier: Modifier = Modifier) {
    val colors = CreatoraTheme.colors
    val delta = change.delta
    if (delta == null) {
        Text(stringResource(R.string.home_change_no_previous), modifier = modifier, style = CreatoraTheme.typography.caption, color = colors.textMuted)
        return
    }
    val up = delta >= 0
    val percent = change.percent?.let { " (" + percentText(it) + ")" }.orEmpty()
    Text(
        text = (if (up) "↑ " else "↓ ") + formatDelta(delta) + percent,
        modifier = modifier,
        style = CreatoraTheme.typography.caption,
        color = if (delta == 0.0) colors.textMuted else if (up) colors.success else colors.danger,
    )
}
