package com.creatora.app.ui.home

import android.content.res.Configuration
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.creatora.app.R
import com.creatora.app.domain.account.NotificationCategory
import com.creatora.app.domain.repository.InboxEntry
import com.creatora.app.ui.components.EmptyState
import com.creatora.app.ui.components.ListGroup
import com.creatora.app.ui.components.ListRowDivider
import com.creatora.app.ui.components.SecondaryButton
import com.creatora.app.ui.components.SubScreenScaffold
import com.creatora.app.ui.theme.CreatoraTheme

/** The bell: the last notifications the app sent. */
@Composable
fun NotificationInboxScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: NotificationInboxViewModel = hiltViewModel()) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    NotificationInboxContent(entries.orEmpty(), viewModel::clear, onBack, modifier)
}

@Composable
fun NotificationInboxContent(entries: List<InboxEntry>, onClear: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    SubScreenScaffold(title = stringResource(R.string.cd_notifications), onBack = onBack, modifier = modifier) {
        if (entries.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.NotificationsNone,
                title = stringResource(R.string.inbox_empty_title),
                body = stringResource(R.string.inbox_empty_body),
            )
        } else {
            ListGroup {
                entries.forEachIndexed { index, entry ->
                    if (index > 0) ListRowDivider()
                    Column(Modifier.fillMaxWidth().padding(CreatoraTheme.spacing.lg), verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.xs)) {
                        Text(entry.title, style = CreatoraTheme.typography.cardTitle, color = CreatoraTheme.colors.text)
                        Text(entry.text, style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.textMuted)
                        Text(
                            DateUtils.getRelativeTimeSpanString(entry.at, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                            style = CreatoraTheme.typography.caption, color = CreatoraTheme.colors.textMuted,
                        )
                    }
                }
            }
            SecondaryButton(stringResource(R.string.inbox_clear), onClear, Modifier.fillMaxWidth())
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun InboxLightPreview() {
    CreatoraTheme(darkTheme = false) {
        NotificationInboxContent(listOf(InboxEntry(1, NotificationCategory.VIEW_MILESTONES, "Your channel passed 10,000 views", "My channel now has 10,012 views in total.", 0)), {}, {})
    }
}

@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun InboxDarkPreview() {
    CreatoraTheme(darkTheme = true) { NotificationInboxContent(emptyList(), {}, {}) }
}
