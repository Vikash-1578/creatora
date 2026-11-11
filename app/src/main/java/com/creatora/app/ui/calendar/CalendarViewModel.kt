package com.creatora.app.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creatora.app.domain.model.Platform
import com.creatora.app.domain.model.Project
import com.creatora.app.domain.model.ScheduledPost
import com.creatora.app.domain.repository.ProjectRepository
import com.creatora.app.domain.repository.ReminderScheduler
import com.creatora.app.domain.repository.ScheduledPostRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A scheduled post with its project's title (null if the project was deleted). */
data class CalendarEntry(val post: ScheduledPost, val projectTitle: String?)

data class CalendarUiState(
    /** Posts that are still to come, soonest first. */
    val upcoming: List<CalendarEntry> = emptyList(),
    /** Posts whose time has passed, newest first. */
    val past: List<CalendarEntry> = emptyList(),
    /** Projects that have a finished render and can be scheduled. */
    val schedulable: List<Project> = emptyList(),
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val projects: ProjectRepository,
    private val posts: ScheduledPostRepository,
    private val reminders: ReminderScheduler,
) : ViewModel() {

    val uiState: StateFlow<CalendarUiState> = combine(posts.observeAll(), projects.observeAll()) { postList, projectList ->
        val now = System.currentTimeMillis()
        val titles = projectList.associate { it.id to it.publishTitle.ifBlank { it.title } }
        val entries = postList.map { CalendarEntry(it, titles[it.projectId]) }
        CalendarUiState(
            upcoming = entries.filter { it.post.scheduledAt >= now }.sortedBy { it.post.scheduledAt },
            past = entries.filter { it.post.scheduledAt < now }.sortedByDescending { it.post.scheduledAt },
            schedulable = projectList.filter { it.renderUri != null && !it.isTemplate },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    fun schedule(projectId: Long, platform: Platform, atMillis: Long) {
        viewModelScope.launch {
            val project = projects.observe(projectId).first() ?: return@launch
            val id = posts.save(ScheduledPost(projectId = projectId, platform = platform, scheduledAt = atMillis))
            reminders.schedule(id, projectId, project.publishTitle.ifBlank { project.title }, platform.name, atMillis)
        }
    }

    fun remove(post: ScheduledPost) {
        viewModelScope.launch {
            reminders.cancel(post.id)
            posts.delete(post.id)
        }
    }
}
