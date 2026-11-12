package com.creatora.app.ui.publish

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.creatora.app.data.ai.FakeTransport
import com.creatora.app.data.youtube.YouTubeUploader
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.account.ConnectedAccount
import com.creatora.app.domain.account.Plan
import com.creatora.app.domain.ai.AiTasks
import com.creatora.app.domain.home.dayKey
import com.creatora.app.domain.model.Project
import com.creatora.app.domain.model.VideoFormat
import com.creatora.app.domain.publish.PostStatus
import com.creatora.app.domain.publish.PublishGate
import com.creatora.app.domain.score.CheckStatus
import com.creatora.app.testutil.FakeAiService
import com.creatora.app.testutil.FakeAssetRepository
import com.creatora.app.testutil.FakeConnectedAccountRepository
import com.creatora.app.testutil.FakeErrorReporter
import com.creatora.app.testutil.FakeLicenceRepository
import com.creatora.app.testutil.FakePlatformAdapter
import com.creatora.app.testutil.FakeProjectRepository
import com.creatora.app.testutil.FakeScheduledPosts
import com.creatora.app.testutil.FakeYouTubeAuthorizer
import com.creatora.app.testutil.MainDispatcherRule
import com.creatora.app.testutil.PlanKit
import com.creatora.app.testutil.keepActive
import com.creatora.app.testutil.okReply
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PublishViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val kit = PlanKit(Plan.FREE)
    private val projects = FakeProjectRepository()
    private val accounts = FakeConnectedAccountRepository()
    private val posts = FakeScheduledPosts()
    private val youtube = FakePlatformAdapter()
    private val registry = com.creatora.app.domain.account.PlatformRegistry(listOf(youtube))
    private val ai = FakeAiService(okReply("TITLES:\n- 5 Python tricks every beginner needs\n- Python for absolute beginners in 2026\nDESCRIPTION: A short description of the video that says what it is about.\nHASHTAGS: #python #coding\nCHAPTERS:\n00:00 Intro"))
    private var projectId = 0L

    private val ig = ConnectedAccount(ConnectPlatform.INSTAGRAM, "ig1", "Maya IG")
    private val ig2 = ConnectedAccount(ConnectPlatform.MOJ, "moj1", "Maya Moj", link = "https://mojapp.in/@m")

    private suspend fun setUp(rendered: Boolean = true) {
        projectId = projects.save(Project(title = "Python tricks", format = VideoFormat.SHORTS_REELS, hook = "Hook", renderUri = if (rendered) "file:///data/v.mp4" else null))
        accounts.save("uid1", ig)
        accounts.save("uid1", ig2)
    }

    private fun viewModel() = PublishViewModel(
        SavedStateHandle(mapOf(PublishViewModel.ARG_PROJECT_ID to projectId)), projects, accounts, kit.auth, kit.plans, kit.daily, posts,
        AiTasks(ai), com.creatora.app.domain.home.ChannelOverviewLoader(registry) { System.currentTimeMillis() }, registry,
        YouTubeUploader(FakeTransport(), FakeYouTubeAuthorizer(), FakeErrorReporter()), FakeAssetRepository(), FakeLicenceRepository(),
        ApplicationProvider.getApplicationContext(),
    )

    @Test
    fun freeAllowsOneDestination_aSecondShowsTheLimit() = runTest {
        setUp()
        val vm = viewModel()
        keepActive(vm.uiState)
        advanceUntilIdle()

        vm.toggle(ig)
        assertEquals(PublishGate.Open, vm.uiState.value.gate)
        vm.toggle(ig2)
        assertEquals(PublishGate.TooManyDestinations(1), vm.uiState.value.gate)
        vm.toggle(ig2)
        assertEquals(PublishGate.Open, vm.uiState.value.gate)
    }

    @Test
    fun theTwoQuestionsAreNeverDefaulted_andBlockPublishingUntilAnswered() = runTest {
        setUp()
        val vm = viewModel()
        keepActive(vm.uiState)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.canPublish)
        assertEquals(CheckStatus.FAIL, vm.uiState.value.checks.first { it.check == com.creatora.app.domain.publish.PubCheck.AI_DISCLOSURE }.status)
        vm.answerAi(false)
        assertFalse(vm.uiState.value.canPublish)
        vm.answerKids(false)
        assertTrue(vm.uiState.value.canPublish)
    }

    @Test
    fun withoutARenderedVideo_publishingIsBlocked() = runTest {
        setUp(rendered = false)
        val vm = viewModel()
        keepActive(vm.uiState)
        advanceUntilIdle()
        vm.answerAi(false)
        vm.answerKids(false)
        assertFalse(vm.uiState.value.canPublish)
    }

    @Test
    fun theAiButtonFillsTheFields_andOffersScoredTitles_withoutChoosingOne() = runTest {
        setUp()
        val vm = viewModel()
        keepActive(vm.uiState)
        advanceUntilIdle()

        vm.regenerate()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.description.startsWith("A short description"))
        assertEquals("#python #coding", state.hashtags)
        assertEquals("python, coding", state.tags)
        assertEquals(2, state.ai.options.size)
        assertTrue(state.ai.options.first().score.points >= state.ai.options.last().score.points) // best first
        assertEquals("Python tricks", state.title) // the user picks; nothing is applied silently
        vm.pickTitle(state.ai.options.first())
        assertEquals(state.ai.options.first().title, vm.uiState.value.title)
    }

    @Test
    fun aShareSheetPublish_savesQueuedPosts_countsOnce_andAsksTheScreenToShare() = runTest {
        setUp()
        val vm = viewModel()
        keepActive(vm.uiState)
        val shares = mutableListOf<ShareRequest>()
        backgroundScope.launch { vm.shares.collect { shares += it } }
        advanceUntilIdle()
        vm.answerAi(false)
        vm.answerKids(false)
        vm.toggle(ig)
        advanceUntilIdle()

        vm.publish()
        advanceUntilIdle()

        val saved = posts.posts.value.values.single()
        assertEquals(PostStatus.QUEUED, saved.status)
        assertEquals(ConnectPlatform.INSTAGRAM, saved.platform)
        assertEquals(1, kit.daily.byDay.value.getValue(dayKey(System.currentTimeMillis())).publishes)
        assertEquals(listOf(ConnectPlatform.INSTAGRAM), shares.map { it.platform })
        assertTrue(vm.uiState.value.phase is PublishPhase.Started)

        // The plan's one publish a day is used now.
        vm.toggle(ig)
        vm.toggle(ig)
        advanceUntilIdle()
        assertEquals(PublishGate.DailyLimit(1), vm.uiState.value.gate)
        vm.publish()
        advanceUntilIdle()
        assertEquals(PublishPhase.Failed(PublishProblem.PLAN_DAILY), vm.uiState.value.phase)
        assertEquals(1, posts.posts.value.size)
    }

    @Test
    fun publishing_neverStartsWithoutAnAnswerOrADestination() = runTest {
        setUp()
        val vm = viewModel()
        keepActive(vm.uiState)
        advanceUntilIdle()

        vm.publish()
        advanceUntilIdle()
        assertEquals(PublishPhase.Failed(PublishProblem.NOTHING_CHOSEN), vm.uiState.value.phase)

        vm.toggle(ig)
        vm.publish()
        advanceUntilIdle()
        assertEquals(PublishPhase.Failed(PublishProblem.CHECKS), vm.uiState.value.phase)
        assertTrue(posts.posts.value.isEmpty())
    }

    @Test
    fun aTimeInThePast_isRefused() = runTest {
        setUp()
        val vm = viewModel()
        keepActive(vm.uiState)
        advanceUntilIdle()
        vm.answerAi(false)
        vm.answerKids(false)
        vm.toggle(ig2)
        vm.setScheduledAt(System.currentTimeMillis() - 1_000)
        advanceUntilIdle()

        vm.publish()
        advanceUntilIdle()

        assertEquals(PublishPhase.Failed(PublishProblem.PAST_TIME), vm.uiState.value.phase)
        assertTrue(posts.posts.value.isEmpty())
    }

    @Test
    fun editsAreSavedOnTheProject() = runTest {
        setUp()
        val vm = viewModel()
        keepActive(vm.uiState)
        advanceUntilIdle()

        vm.setTitle("A better title for the video")
        vm.setDescription("Line one.\nLine two.")
        vm.setHashtags("#one two")
        vm.answerKids(true)
        advanceUntilIdle()
        kotlinx.coroutines.delay(1_000)
        advanceUntilIdle()

        val saved = projects.observe(projectId).first()!!
        assertEquals("A better title for the video", saved.publishTitle)
        assertEquals(listOf("one", "two"), saved.publishHashtags)
        assertEquals(true, saved.madeForKids)
        assertEquals(null, saved.aiContent)
    }
}
