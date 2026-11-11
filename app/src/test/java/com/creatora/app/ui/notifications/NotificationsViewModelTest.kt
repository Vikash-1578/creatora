package com.creatora.app.ui.notifications

import com.creatora.app.domain.grow.CommentDraft
import com.creatora.app.domain.grow.DraftStatus
import com.creatora.app.domain.home.NoticeKind
import com.creatora.app.domain.model.Platform
import com.creatora.app.domain.model.Project
import com.creatora.app.domain.model.ScheduledPost
import com.creatora.app.testutil.FakeCommentDraftRepository
import com.creatora.app.testutil.FakeProjectRepository
import com.creatora.app.testutil.FakeScheduledPostRepository
import com.creatora.app.testutil.MainDispatcherRule
import com.creatora.app.testutil.keepActive
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NotificationsViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    @Test
    fun noticesFollowPostsAndPendingReplyDrafts() = runTest {
        val projects = FakeProjectRepository()
        val posts = FakeScheduledPostRepository()
        val drafts = FakeCommentDraftRepository()
        val viewModel = NotificationsViewModel(projects, posts, drafts)
        keepActive(viewModel.notices)
        assertTrue(viewModel.notices.value.orEmpty().isEmpty())

        val project = projects.save(Project(title = "Studio tour"))
        posts.save(ScheduledPost(projectId = project, platform = Platform.YOUTUBE, scheduledAt = System.currentTimeMillis() + 3_600_000))
        drafts.save(CommentDraft(comment = "a", reply = "r"))
        drafts.save(CommentDraft(comment = "b", reply = "r", status = DraftStatus.APPROVED))

        val notices = viewModel.notices.value!!
        assertEquals(listOf(NoticeKind.POST_SOON, NoticeKind.REPLIES_WAITING), notices.map { it.kind })
        assertEquals("Studio tour", notices[0].title)
        assertEquals(1, notices[1].count)
    }
}
