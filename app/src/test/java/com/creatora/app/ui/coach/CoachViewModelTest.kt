package com.creatora.app.ui.coach

import com.creatora.app.domain.ai.AiTasks
import com.creatora.app.domain.model.AiError
import com.creatora.app.domain.model.AiOutcome
import com.creatora.app.domain.model.ChatRole
import com.creatora.app.testutil.FakeAiService
import com.creatora.app.testutil.MainDispatcherRule
import com.creatora.app.testutil.okReply
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class CoachViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val service = FakeAiService(okReply("Post weekly."))

    private fun viewModel() = CoachViewModel(AiTasks(service))

    @Test
    fun send_addsTheQuestionAndTheAnswer() {
        val viewModel = viewModel()

        viewModel.send("  How often should I post?  ")

        val state = viewModel.uiState.value
        assertEquals(listOf(ChatRole.USER, ChatRole.ASSISTANT), state.messages.map { it.role })
        assertEquals("How often should I post?", state.messages[0].text)
        assertEquals("Post weekly.", state.messages[1].text)
        assertFalse(state.sending)
        assertNull(state.errors)
    }

    @Test
    fun send_ignoresBlankText() {
        val viewModel = viewModel()
        viewModel.send("   ")
        assertEquals(0, viewModel.uiState.value.messages.size)
        assertEquals(0, service.requests.size)
    }

    @Test
    fun theWholeConversationIsSentEachTime() {
        val viewModel = viewModel()
        viewModel.send("One")
        viewModel.send("Two")

        assertEquals(listOf("One", "Post weekly.", "Two"), service.requests.last().messages.map { it.text })
    }

    @Test
    fun aFailureKeepsTheQuestionAndShowsTheErrors_andRetrySendsItAgain() {
        service.outcome = AiOutcome.Failed(listOf(AiError.NoKey))
        val viewModel = viewModel()

        viewModel.send("Hello")

        assertEquals(listOf(AiError.NoKey), viewModel.uiState.value.errors)
        assertEquals(1, viewModel.uiState.value.messages.size)
        assertFalse(viewModel.uiState.value.sending)

        service.outcome = okReply("Hi!")
        viewModel.retry()

        assertEquals(listOf("Hello", "Hi!"), viewModel.uiState.value.messages.map { it.text })
        assertNull(viewModel.uiState.value.errors)
        assertEquals(2, service.requests.size)
    }

    @Test
    fun retry_doesNothingWithoutAnError() {
        val viewModel = viewModel()
        viewModel.retry()
        assertEquals(0, service.requests.size)
    }
}
