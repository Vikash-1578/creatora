package com.creatora.app.ui.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creatora.app.domain.ai.AiTasks
import com.creatora.app.domain.model.AiError
import com.creatora.app.domain.model.AiOutcome
import com.creatora.app.domain.repository.SwipeRepository
import com.creatora.app.domain.repository.YouTubeApi
import com.creatora.app.domain.research.SwipeItem
import com.creatora.app.domain.research.YtError
import com.creatora.app.domain.research.YtOutcome
import com.creatora.app.domain.research.YtVideo
import com.creatora.app.domain.research.parseVideoId
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StudyUiState(
    val loading: Boolean = false,
    val video: YtVideo? = null,
    /** AI notes on the video's style, once they arrive. */
    val notes: String? = null,
    val notLink: Boolean = false,
    val notFound: Boolean = false,
    val youTubeError: YtError? = null,
    val aiErrors: List<AiError>? = null,
    val saved: Boolean = false,
)

/** Look up a video from a link, ask the AI what works about it, and keep it in the swipe file. */
@HiltViewModel
class StudyViewModel @Inject constructor(
    private val api: YouTubeApi,
    private val ai: AiTasks,
    private val swipe: SwipeRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StudyUiState())
    val uiState: StateFlow<StudyUiState> = _uiState.asStateFlow()

    fun study(link: String) {
        if (_uiState.value.loading) return
        val id = parseVideoId(link)
        if (id == null) {
            _uiState.value = StudyUiState(notLink = true)
            return
        }
        _uiState.value = StudyUiState(loading = true)
        viewModelScope.launch {
            val video = when (val result = api.video(id)) {
                is YtOutcome.Ok -> result.value
                is YtOutcome.Failed -> return@launch _uiState.update { StudyUiState(youTubeError = result.error) }
            }
            if (video == null) return@launch _uiState.update { StudyUiState(notFound = true) }
            _uiState.update { it.copy(video = video) }
            when (val result = ai.styleNotes(video.title, video.channelTitle, video.description)) {
                is AiOutcome.Ok -> _uiState.update { it.copy(loading = false, notes = result.value) }
                is AiOutcome.Failed -> _uiState.update { it.copy(loading = false, aiErrors = result.errors) }
            }
        }
    }

    /** Saves the video to the swipe file with the AI's notes (if any) as its note. */
    fun save() {
        val state = _uiState.value
        val video = state.video ?: return
        viewModelScope.launch {
            swipe.save(SwipeItem(title = video.title, url = video.url, channelTitle = video.channelTitle, note = state.notes.orEmpty()))
            _uiState.update { it.copy(saved = true) }
        }
    }
}
