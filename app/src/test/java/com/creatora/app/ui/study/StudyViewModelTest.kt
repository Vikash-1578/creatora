package com.creatora.app.ui.study

import com.creatora.app.domain.ai.AiTasks
import com.creatora.app.domain.model.AiError
import com.creatora.app.domain.model.AiOutcome
import com.creatora.app.domain.research.YtError
import com.creatora.app.domain.research.YtOutcome
import com.creatora.app.domain.research.YtVideo
import com.creatora.app.testutil.FakeAiService
import com.creatora.app.testutil.FakeSwipeRepository
import com.creatora.app.testutil.FakeYouTubeApi
import com.creatora.app.testutil.MainDispatcherRule
import com.creatora.app.testutil.okReply
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StudyViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val api = FakeYouTubeApi()
    private val ai = FakeAiService(okReply("- Hook: a question"))
    private val swipe = FakeSwipeRepository()
    private val id = "dQw4w9WgXcQ"
    private val video = YtVideo(id, "A title", "c1", "A channel", 1_000, null, "A description")

    private fun viewModel() = StudyViewModel(api, AiTasks(ai), swipe)

    @Test
    fun study_looksTheVideoUpAndAsksTheAiAboutItsStyle() {
        api.videoResult = YtOutcome.Ok(video)
        val viewModel = viewModel()

        viewModel.study("https://youtu.be/$id")

        val state = viewModel.uiState.value
        assertEquals(video, state.video)
        assertEquals("- Hook: a question", state.notes)
        assertFalse(state.loading)
        assertEquals(listOf("video:$id"), api.calls)
        val prompt = ai.requests.single().messages.single().text
        assertTrue("A title" in prompt && "A channel" in prompt && "A description" in prompt)
    }

    @Test
    fun aTextThatIsNotAYouTubeLink_isRejectedWithoutCallingAnything() {
        val viewModel = viewModel()

        viewModel.study("hello")

        assertTrue(viewModel.uiState.value.notLink)
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun aMissingVideoAndYouTubeErrors_areReported() {
        val viewModel = viewModel()

        viewModel.study(id)
        assertTrue(viewModel.uiState.value.notFound)

        api.videoResult = YtOutcome.Failed(YtError.InvalidKey)
        viewModel.study(id)
        assertEquals(YtError.InvalidKey, viewModel.uiState.value.youTubeError)
        assertTrue(ai.requests.isEmpty())
    }

    @Test
    fun withoutAnAiKey_theVideoStillShowsWithTheAiErrors() {
        api.videoResult = YtOutcome.Ok(video)
        ai.outcome = AiOutcome.Failed(listOf(AiError.NoKey))
        val viewModel = viewModel()

        viewModel.study(id)

        assertEquals(video, viewModel.uiState.value.video)
        assertEquals(listOf(AiError.NoKey), viewModel.uiState.value.aiErrors)
        assertFalse(viewModel.uiState.value.loading)
    }

    @Test
    fun save_keepsTheVideoWithTheAiNotesInTheSwipeFile() {
        api.videoResult = YtOutcome.Ok(video)
        val viewModel = viewModel()
        viewModel.study(id)

        viewModel.save()

        val item = swipe.items.value.single()
        assertEquals("https://youtu.be/$id", item.url)
        assertEquals("- Hook: a question", item.note)
        assertTrue(viewModel.uiState.value.saved)
    }
}
