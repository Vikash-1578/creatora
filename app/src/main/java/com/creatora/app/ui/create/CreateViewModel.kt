package com.creatora.app.ui.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creatora.app.domain.ai.AiTasks
import com.creatora.app.domain.model.AiError
import com.creatora.app.domain.model.AiOutcome
import com.creatora.app.domain.model.Project
import com.creatora.app.domain.model.VideoFormat
import com.creatora.app.domain.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class IdeasUiState(
    val loading: Boolean = false,
    val ideas: List<String> = emptyList(),
    val errors: List<AiError>? = null,
)

@HiltViewModel
class CreateViewModel @Inject constructor(
    private val projects: ProjectRepository,
    private val ai: AiTasks,
) : ViewModel() {

    private val _ideas = MutableStateFlow(IdeasUiState())
    val ideas: StateFlow<IdeasUiState> = _ideas.asStateFlow()

    private var starting = false

    /**
     * Creates a project in [format] and passes its id to [onCreated] so the wizard can open. If the user
     * already has an empty draft in that format (nothing written yet), it is reused instead of adding
     * another, and a second tap while the first is still saving is ignored.
     */
    fun start(format: VideoFormat, title: String, onCreated: (Long) -> Unit, brief: String = "") {
        if (starting) return
        starting = true
        viewModelScope.launch {
            try {
                val empty = if (brief.isBlank()) {
                    projects.observeAll().first()
                        .filter { it.isInProgress && it.format == format && it.step == 0 && it.brief.isBlank() && it.script.isBlank() }
                        .maxByOrNull { it.updatedAt }
                } else null
                onCreated(empty?.id ?: projects.save(Project(title = title.trim(), format = format, brief = brief.trim())))
            } finally {
                starting = false
            }
        }
    }

    fun suggestIdeas(topic: String) {
        if (_ideas.value.loading) return
        _ideas.update { it.copy(loading = true, errors = null) }
        viewModelScope.launch {
            _ideas.update {
                when (val outcome = ai.ideas(topic.trim())) {
                    is AiOutcome.Ok -> IdeasUiState(ideas = outcome.value)
                    is AiOutcome.Failed -> IdeasUiState(errors = outcome.errors)
                }
            }
        }
    }

    fun clearIdeas() {
        _ideas.value = IdeasUiState()
    }
}
