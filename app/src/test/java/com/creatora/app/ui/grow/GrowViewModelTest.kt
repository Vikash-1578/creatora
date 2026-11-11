package com.creatora.app.ui.grow

import com.creatora.app.domain.ai.AiTasks
import com.creatora.app.domain.grow.AbTest
import com.creatora.app.domain.grow.CommentDraft
import com.creatora.app.domain.grow.DraftStatus
import com.creatora.app.domain.grow.MyChannel
import com.creatora.app.domain.grow.TestKind
import com.creatora.app.domain.grow.TestVariant
import com.creatora.app.domain.model.AiError
import com.creatora.app.domain.model.AiOutcome
import com.creatora.app.domain.model.ChannelSnapshot
import com.creatora.app.domain.research.YtChannel
import com.creatora.app.domain.research.YtError
import com.creatora.app.domain.research.YtOutcome
import com.creatora.app.domain.research.YtVideo
import com.creatora.app.testutil.FakeAbTestRepository
import com.creatora.app.testutil.FakeAiService
import com.creatora.app.testutil.FakeCommentDraftRepository
import com.creatora.app.testutil.FakeMyChannelRepository
import com.creatora.app.testutil.FakeProjectRepository
import com.creatora.app.testutil.FakeSnapshotRepository
import com.creatora.app.testutil.FakeYouTubeApi
import com.creatora.app.testutil.FakeYouTubeKeyStore
import com.creatora.app.testutil.MainDispatcherRule
import com.creatora.app.testutil.keepActive
import com.creatora.app.testutil.okReply
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GrowViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val api = FakeYouTubeApi()
    private val keys = FakeYouTubeKeyStore("key")
    private val channelStore = FakeMyChannelRepository()
    private val snapshots = FakeSnapshotRepository()
    private val drafts = FakeCommentDraftRepository()
    private val tests = FakeAbTestRepository()
    private val projects = FakeProjectRepository()
    private val aiService = FakeAiService(okReply("TOPIC: Ten editing tricks\nWHY: Your editing video did best."))

    private fun channel(views: Long = 5_000, videos: Long = 10, subs: Long? = 200) = YtChannel("c1", "My Channel", subs, views, videos, null)

    private fun TestScope.open(): GrowViewModel {
        val viewModel = GrowViewModel(api, keys, channelStore, snapshots, drafts, tests, projects, AiTasks(aiService))
        keepActive(viewModel.uiState)
        return viewModel
    }

    @Test
    fun connect_remembersTheChannelAndTakesTheFirstSnapshot() = runTest {
        api.channel = YtOutcome.Ok(channel())
        val viewModel = open()

        viewModel.connect(" @mychannel ")

        assertEquals(MyChannel("c1", "My Channel"), viewModel.uiState.value.channel)
        val snapshot = snapshots.items.value.single()
        assertEquals(Triple("c1", 200L, 5_000L), Triple(snapshot.channelId, snapshot.subscriberCount, snapshot.viewCount))
        assertEquals(1, viewModel.uiState.value.snapshots.size)
        assertFalse(viewModel.uiState.value.loading)
    }

    @Test
    fun connect_withHiddenSubscribersStoresZero() = runTest {
        api.channel = YtOutcome.Ok(channel(subs = null))
        open().connect("My Channel")
        assertEquals(0L, snapshots.items.value.single().subscriberCount)
    }

    @Test
    fun connect_reportsAMissingChannelAndApiErrors() = runTest {
        val viewModel = open()

        viewModel.connect("nobody")
        assertTrue(viewModel.uiState.value.channelNotFound)
        assertNull(viewModel.uiState.value.channel)

        api.channel = YtOutcome.Failed(YtError.InvalidKey)
        viewModel.connect("someone")
        assertEquals(YtError.InvalidKey, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.channelNotFound)
    }

    @Test
    fun refresh_storesANewSnapshotForTheConnectedChannel() = runTest {
        channelStore.set(MyChannel("c1", "My Channel"))
        api.channelList = YtOutcome.Ok(listOf(channel(views = 7_000)))
        val viewModel = open()

        viewModel.refresh()

        assertEquals(7_000L, snapshots.items.value.single().viewCount)
        assertEquals(listOf("channels:c1"), api.calls)
    }

    @Test
    fun captureIfDue_onlySnapshotsWhenTheLastOneIsOld() = runTest {
        channelStore.set(MyChannel("c1", "My Channel"))
        api.channelList = YtOutcome.Ok(listOf(channel()))
        val viewModel = open()

        viewModel.captureIfDue(nowMillis = 1_000) // nothing stored yet
        assertEquals(1, snapshots.items.value.size)

        viewModel.captureIfDue(nowMillis = 2_000) // too soon: the snapshot has capturedAt "now"
        snapshots.save(ChannelSnapshot(channelId = "c1", subscriberCount = 1, viewCount = 1, videoCount = 1, capturedAt = 0))
        viewModel.captureIfDue(nowMillis = 5L * 60 * 60 * 1000)
        assertEquals(2, snapshots.items.value.size)
    }

    @Test
    fun captureIfDue_doesNothingWithoutAKeyOrChannel() = runTest {
        val viewModel = open()
        viewModel.captureIfDue(nowMillis = 99_999_999_999)
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun suggestNext_usesTopVideosStatsAndRecentComments() = runTest {
        channelStore.set(MyChannel("c1", "My Channel"))
        snapshots.save(ChannelSnapshot(channelId = "c1", subscriberCount = 200, viewCount = 5_000, videoCount = 10, capturedAt = 1))
        drafts.save(CommentDraft(comment = "Please make an editing tutorial", reply = "Sure!"))
        api.top = YtOutcome.Ok(listOf(YtVideo("v1", "My editing video", "c1", "My Channel", 9_000, null)))
        val viewModel = open()

        viewModel.suggestNext()

        val topic = viewModel.uiState.value.next.topic!!
        assertEquals("Ten editing tricks", topic.topic)
        assertEquals("Your editing video did best.", topic.reason)
        val prompt = aiService.requests.single().messages.single().text
        assertTrue("My editing video (9000 views)" in prompt && "Please make an editing tutorial" in prompt && "200 subscribers" in prompt)
    }

    @Test
    fun suggestNext_reportsYouTubeAndAiProblems() = runTest {
        channelStore.set(MyChannel("c1", "My Channel"))
        val viewModel = open()

        api.top = YtOutcome.Failed(YtError.QuotaExceeded)
        viewModel.suggestNext()
        assertEquals(YtError.QuotaExceeded, viewModel.uiState.value.next.youTubeError)

        api.top = YtOutcome.Ok(emptyList())
        aiService.outcome = AiOutcome.Failed(listOf(AiError.NoKey))
        viewModel.suggestNext()
        assertEquals(listOf(AiError.NoKey), viewModel.uiState.value.next.aiErrors)
    }

    @Test
    fun startSuggestion_createsAProjectFromTheTopic() = runTest {
        channelStore.set(MyChannel("c1", "My Channel"))
        val viewModel = open()
        viewModel.suggestNext()
        var started = 0L

        viewModel.startSuggestion { started = it }

        val project = projects.items.value.single()
        assertEquals(started, project.id)
        assertEquals("Ten editing tricks", project.title)
        assertEquals("Ten editing tricks\nYour editing video did best.", project.brief)
    }

    @Test
    fun draftReply_savesAPendingDraftInTheChosenTone() = runTest {
        aiService.outcome = okReply("\"Thanks so much!\"")
        val viewModel = open()

        viewModel.draftReply("  Great video!  ", ReplyTone.FUNNY)

        val draft = drafts.items.value.single()
        assertEquals("Great video!", draft.comment)
        assertEquals("Thanks so much!", draft.reply)
        assertEquals(DraftStatus.PENDING, draft.status)
        assertTrue("funny" in aiService.requests.single().system)
        assertFalse(viewModel.uiState.value.replying)
    }

    @Test
    fun draftReply_ignoresBlankText_andReportsAiErrors() = runTest {
        val viewModel = open()
        viewModel.draftReply("   ", ReplyTone.FRIENDLY)
        assertTrue(aiService.requests.isEmpty())

        aiService.outcome = AiOutcome.Failed(listOf(AiError.NoKey))
        viewModel.draftReply("Nice", ReplyTone.SHORT)
        assertEquals(listOf(AiError.NoKey), viewModel.uiState.value.replyErrors)
        assertTrue(drafts.items.value.isEmpty())
    }

    @Test
    fun draftStatusAndDeletion() = runTest {
        val id = drafts.save(CommentDraft(comment = "c", reply = "r"))
        val viewModel = open()

        viewModel.setDraftStatus(drafts.items.value.single { it.id == id }, DraftStatus.APPROVED)
        assertEquals(DraftStatus.APPROVED, drafts.items.value.single().status)

        viewModel.deleteDraft(drafts.items.value.single())
        assertTrue(drafts.items.value.isEmpty())
    }

    @Test
    fun tests_needANameAndBothVersions_andCanGetAWinnerAndBeReopened() = runTest {
        val viewModel = open()
        viewModel.createTest("", TestKind.TITLE, "a", "b", "")
        viewModel.createTest("Name", TestKind.TITLE, "a", " ", "")
        assertTrue(tests.items.value.isEmpty())

        viewModel.createTest(" Title test ", TestKind.THUMBNAIL, " Version one ", "Version two", " notes ")
        val test: AbTest = tests.items.value.single()
        assertEquals(listOf("Title test", "Version one", "notes"), listOf(test.name, test.variantA, test.notes))
        assertNull(test.winner)

        viewModel.setWinner(test, TestVariant.B)
        assertEquals(TestVariant.B, tests.items.value.single().winner)
        assertTrue(tests.items.value.single().endedAt != null)

        viewModel.setWinner(tests.items.value.single(), null)
        assertNull(tests.items.value.single().winner)
        assertNull(tests.items.value.single().endedAt)

        viewModel.deleteTest(tests.items.value.single())
        assertTrue(tests.items.value.isEmpty())
    }

    @Test
    fun tabAndPeriodSelection() = runTest {
        val viewModel = open()
        viewModel.selectTab(GrowTab.TESTS)
        viewModel.setPeriod(28)
        assertEquals(GrowTab.TESTS, viewModel.uiState.value.tab)
        assertEquals(28, viewModel.uiState.value.periodDays)
    }
}
