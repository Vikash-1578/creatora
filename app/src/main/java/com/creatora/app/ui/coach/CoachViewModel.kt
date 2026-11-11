package com.creatora.app.ui.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creatora.app.domain.ai.AiTasks
import com.creatora.app.domain.model.AiError
import com.creatora.app.domain.model.AiOutcome
import com.creatora.app.domain.model.ChatMessage
import com.creatora.app.domain.model.ChatRole
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CoachUiState(
    val messages: List<ChatMessage> = emptyList(),
    val sending: Boolean = false,
    /** Set when the last request failed; the question stays in [messages] so [CoachViewModel.retry] can resend it. */
    val errors: List<AiError>? = null,
)

/** The AI Coach conversation. It lives as long as the screen; nothing is stored. */
@HiltViewModel
class CoachViewModel @Inject constructor(private val tasks: AiTasks) : ViewModel() {

    private val _uiState = MutableStateFlow(CoachUiState())
    val uiState: StateFlow<CoachUiState> = _uiState.asStateFlow()

    fun send(text: String) {
        val question = text.trim()
        if (question.isEmpty() || _uiState.value.sending) return
        _uiState.update { it.copy(messages = it.messages + ChatMessage(ChatRole.USER, question)) }
        request()
    }

    /** Sends the conversation again after an error. */
    fun retry() {
        if (_uiState.value.errors != null && !_uiState.value.sending) request()
    }

    private fun request() {
        _uiState.update { it.copy(sending = true, errors = null) }
        viewModelScope.launch {
            val outcome = tasks.coach(_uiState.value.messages)
            _uiState.update {
                when (outcome) {
                    is AiOutcome.Ok -> it.copy(messages = it.messages + ChatMessage(ChatRole.ASSISTANT, outcome.value), sending = false)
                    is AiOutcome.Failed -> it.copy(sending = false, errors = outcome.errors)
                }
            }
        }
    }
}
