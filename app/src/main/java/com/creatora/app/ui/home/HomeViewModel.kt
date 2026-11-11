package com.creatora.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creatora.app.domain.model.Project
import com.creatora.app.domain.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class HomeViewModel @Inject constructor(projects: ProjectRepository) : ViewModel() {

    /** The project most recently worked on in the Create wizard that isn't marked ready yet. */
    val continueProject: StateFlow<Project?> = projects.observeAll()
        .map { list -> list.filter { it.isInProgress }.maxByOrNull { it.updatedAt } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The last few projects touched, newest first, templates left out. */
    val recentProjects: StateFlow<List<Project>> = projects.observeAll()
        .map { list -> list.filterNot { it.isTemplate }.sortedByDescending { it.updatedAt }.take(MAX_RECENT) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    companion object {
        const val MAX_RECENT = 5
    }
}
