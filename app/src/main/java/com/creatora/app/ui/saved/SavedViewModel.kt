package com.creatora.app.ui.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creatora.app.domain.model.Idea
import com.creatora.app.domain.model.Project
import com.creatora.app.domain.model.VideoFormat
import com.creatora.app.domain.repository.IdeaRepository
import com.creatora.app.domain.repository.ProjectRepository
import com.creatora.app.domain.repository.SwipeRepository
import com.creatora.app.domain.research.SwipeItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SavedUiState(val ideas: List<Idea> = emptyList(), val swipe: List<SwipeItem> = emptyList())

/** The idea bank and the swipe file. */
@HiltViewModel
class SavedViewModel @Inject constructor(
    private val ideas: IdeaRepository,
    private val swipe: SwipeRepository,
    private val projects: ProjectRepository,
) : ViewModel() {

    val uiState: StateFlow<SavedUiState> = combine(ideas.observeAll(), swipe.observeAll(), ::SavedUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SavedUiState())

    fun addIdea(title: String, notes: String) {
        val trimmed = title.trim()
        if (trimmed.isNotEmpty()) viewModelScope.launch { ideas.save(Idea(title = trimmed, notes = notes.trim())) }
    }

    fun deleteIdea(idea: Idea) {
        viewModelScope.launch { ideas.delete(idea.id) }
    }

    /** Starts a project from an idea (its title and notes become the brief) and links the idea to it. */
    fun useIdea(idea: Idea, onCreated: (projectId: Long) -> Unit) {
        viewModelScope.launch {
            val brief = listOf(idea.title, idea.notes).filter { it.isNotBlank() }.joinToString("\n")
            val projectId = projects.save(Project(title = idea.title, format = VideoFormat.LONG_VIDEO, brief = brief))
            ideas.save(idea.copy(projectId = projectId))
            onCreated(projectId)
        }
    }

    fun deleteSwipe(item: SwipeItem) {
        viewModelScope.launch { swipe.delete(item.id) }
    }
}
