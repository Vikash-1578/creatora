package com.creatora.app.ui.publish

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creatora.app.domain.ai.AiTasks
import com.creatora.app.domain.model.AiError
import com.creatora.app.domain.model.AiOutcome
import com.creatora.app.domain.model.Platform
import com.creatora.app.domain.model.Project
import com.creatora.app.domain.model.PublishText
import com.creatora.app.domain.model.ScheduledPost
import com.creatora.app.domain.publish.ChecklistEntry
import com.creatora.app.domain.publish.publishChecklist
import com.creatora.app.domain.repository.ProjectRepository
import com.creatora.app.domain.repository.ReminderScheduler
import com.creatora.app.domain.repository.ScheduledPostRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PublishAiState(
    val busy: Boolean = false,
    val errors: List<AiError>? = null,
    val suggestion: PublishText? = null,
)

data class PublishUiState(
    val project: Project? = null,
    val checklist: List<ChecklistEntry> = emptyList(),
    val posts: List<ScheduledPost> = emptyList(),
    val ai: PublishAiState = PublishAiState(),
)

@HiltViewModel
class PublishViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val projects: ProjectRepository,
    private val posts: ScheduledPostRepository,
    private val reminders: ReminderScheduler,
    private val ai: AiTasks,
) : ViewModel() {

    private val projectId: Long = savedStateHandle[ARG_PROJECT_ID] ?: 0L
    private val aiState = MutableStateFlow(PublishAiState())

    val uiState: StateFlow<PublishUiState> = combine(projects.observe(projectId), posts.observeAll(), aiState) { project, allPosts, aiStatus ->
        PublishUiState(
            project = project,
            checklist = project?.let(::publishChecklist).orEmpty(),
            posts = allPosts.filter { it.projectId == projectId },
            ai = aiStatus,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PublishUiState())

    private fun edit(transform: (Project) -> Project) {
        viewModelScope.launch { projects.observe(projectId).first()?.let { projects.save(transform(it).copy(updatedAt = System.currentTimeMillis())) } }
    }

    fun setTitle(title: String) = edit { it.copy(publishTitle = title) }
    fun setDescription(text: String) = edit { it.copy(publishDescription = text) }
    fun setAiContent(value: Boolean) = edit { it.copy(aiContent = value) }
    fun setMadeForKids(value: Boolean) = edit { it.copy(madeForKids = value) }

    /** Asks the AI for titles, a description and hashtags from the script. Nothing is applied until the user picks. */
    fun suggest() {
        val project = uiState.value.project ?: return
        if (aiState.value.busy) return
        aiState.update { it.copy(busy = true, errors = null) }
        viewModelScope.launch {
            when (val outcome = ai.publishText(project.publishTitle.ifBlank { project.title }, project.script)) {
                is AiOutcome.Ok -> aiState.update { it.copy(busy = false, suggestion = outcome.value) }
                is AiOutcome.Failed -> aiState.update { it.copy(busy = false, errors = outcome.errors) }
            }
        }
    }

    fun dismissSuggestion() = aiState.update { it.copy(suggestion = null) }

    fun schedule(platform: Platform, atMillis: Long) {
        val project = uiState.value.project ?: return
        viewModelScope.launch {
            val id = posts.save(ScheduledPost(projectId = project.id, platform = platform, scheduledAt = atMillis))
            reminders.schedule(id, project.id, project.publishTitle.ifBlank { project.title }, platform.name, atMillis)
        }
    }

    fun removePost(post: ScheduledPost) {
        viewModelScope.launch {
            reminders.cancel(post.id)
            posts.delete(post.id)
        }
    }

    companion object {
        const val ARG_PROJECT_ID = "projectId"
    }
}
