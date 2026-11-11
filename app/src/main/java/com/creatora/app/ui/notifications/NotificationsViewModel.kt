package com.creatora.app.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creatora.app.domain.grow.DraftStatus
import com.creatora.app.domain.home.Notice
import com.creatora.app.domain.home.buildNotices
import com.creatora.app.domain.repository.CommentDraftRepository
import com.creatora.app.domain.repository.ProjectRepository
import com.creatora.app.domain.repository.ScheduledPostRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Things that need attention, worked out from the user's own posts and reply drafts. Nothing is stored. */
@HiltViewModel
class NotificationsViewModel @Inject constructor(
    projects: ProjectRepository,
    posts: ScheduledPostRepository,
    drafts: CommentDraftRepository,
) : ViewModel() {

    val notices: StateFlow<List<Notice>?> = combine(projects.observeAll(), posts.observeAll(), drafts.observeAll()) { projectList, postList, draftList ->
        val titles = projectList.associate { it.id to it.publishTitle.ifBlank { it.title } }
        buildNotices(System.currentTimeMillis(), postList, titles, draftList.count { it.status == DraftStatus.PENDING })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
