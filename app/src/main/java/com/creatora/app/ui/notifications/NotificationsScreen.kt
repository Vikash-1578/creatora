package com.creatora.app.ui.notifications

import android.content.res.Configuration
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.creatora.app.R
import com.creatora.app.domain.home.Notice
import com.creatora.app.domain.home.NoticeKind
import com.creatora.app.domain.model.Platform
import com.creatora.app.ui.components.EmptyState
import com.creatora.app.ui.components.ListGroup
import com.creatora.app.ui.components.ListRowDivider
import com.creatora.app.ui.components.NavigationListRow
import com.creatora.app.ui.components.SubScreenScaffold
import com.creatora.app.ui.publish.labelRes
import com.creatora.app.ui.theme.CreatoraTheme

@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onOpenPublish: (projectId: Long) -> Unit,
    onOpenGrow: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationsViewModel = hiltViewModel(),
) {
    val notices by viewModel.notices.collectAsStateWithLifecycle()
    NotificationsContent(notices.orEmpty(), onBack, onOpenPublish, onOpenGrow, modifier)
}

@Composable
fun NotificationsContent(
    notices: List<Notice>,
    onBack: () -> Unit,
    onOpenPublish: (Long) -> Unit,
    onOpenGrow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SubScreenScaffold(title = stringResource(R.string.notifications_title), onBack = onBack, modifier = modifier) {
        if (notices.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.NotificationsNone,
                title = stringResource(R.string.notifications_empty_title),
                body = stringResource(R.string.notifications_empty_body),
            )
        } else {
            val context = LocalContext.current
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = CreatoraTheme.spacing.lg)) {
                ListGroup {
                    notices.forEachIndexed { index, notice ->
                        if (index > 0) ListRowDivider()
                        val whenText = DateUtils.getRelativeTimeSpanString(notice.atMillis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
                        val title = when (notice.kind) {
                            NoticeKind.POST_SOON -> stringResource(R.string.notice_post_soon, notice.title.ifBlank { stringResource(R.string.home_untitled) })
                            NoticeKind.POST_MISSED -> stringResource(R.string.notice_post_missed, notice.title.ifBlank { stringResource(R.string.home_untitled) })
                            NoticeKind.REPLIES_WAITING -> pluralStringResource(R.plurals.notice_replies_waiting, notice.count, notice.count)
                        }
                        NavigationListRow(
                            title = title,
                            value = notice.platform?.let { context.getString(R.string.notice_when, context.getString(it.labelRes()), whenText) },
                            onClick = { notice.projectId?.let(onOpenPublish) ?: onOpenGrow() },
                        )
                    }
                }
            }
        }
    }
}

private val PreviewNotices = listOf(
    Notice(NoticeKind.POST_SOON, 1, "Studio tour", Platform.YOUTUBE, System.currentTimeMillis() + 3_600_000),
    Notice(NoticeKind.REPLIES_WAITING, count = 3),
)

@Preview(name = "Light", showBackground = true)
@Composable
private fun NotificationsLightPreview() {
    CreatoraTheme(darkTheme = false) { NotificationsContent(PreviewNotices, {}, {}, {}) }
}

@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NotificationsDarkPreview() {
    CreatoraTheme(darkTheme = true) { NotificationsContent(emptyList(), {}, {}, {}) }
}
