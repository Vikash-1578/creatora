package com.creatora.app.ui.grow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creatora.app.domain.ai.AiTasks
import com.creatora.app.domain.ai.NextTopic
import com.creatora.app.domain.grow.AbTest
import com.creatora.app.domain.grow.CommentDraft
import com.creatora.app.domain.grow.DraftStatus
import com.creatora.app.domain.grow.MyChannel
import com.creatora.app.domain.grow.TestKind
import com.creatora.app.domain.grow.TestVariant
import com.creatora.app.domain.grow.shouldCapture
import com.creatora.app.domain.model.AiError
import com.creatora.app.domain.model.AiOutcome
import com.creatora.app.domain.model.ChannelSnapshot
import com.creatora.app.domain.model.Project
import com.creatora.app.domain.model.VideoFormat
import com.creatora.app.domain.repository.AbTestRepository
import com.creatora.app.domain.repository.ChannelSnapshotRepository
import com.creatora.app.domain.repository.CommentDraftRepository
import com.creatora.app.domain.repository.MyChannelRepository
import com.creatora.app.domain.repository.ProjectRepository
import com.creatora.app.domain.repository.YouTubeApi
import com.creatora.app.domain.repository.YouTubeKeyStore
import com.creatora.app.domain.research.YtError
import com.creatora.app.domain.research.YtOutcome
import com.creatora.app.domain.research.parseChannelQuery
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class GrowTab { OVERVIEW, COMMENTS, TESTS }

enum class ReplyTone { FRIENDLY, SHORT, FUNNY }

/** What the "Make this next" card shows. */
data class NextSuggestion(
    val loading: Boolean = false,
    val topic: NextTopic? = null,
    val aiErrors: List<AiError>? = null,
    val youTubeError: YtError? = null,
)

data class GrowUiState(
    val hasKey: Boolean = false,
    val channel: MyChannel? = null,
    /** Oldest first. */
    val snapshots: List<ChannelSnapshot> = emptyList(),
    val drafts: List<CommentDraft> = emptyList(),
    val tests: List<AbTest> = emptyList(),
    val tab: GrowTab = GrowTab.OVERVIEW,
    val periodDays: Int = 7,
    val loading: Boolean = false,
    val error: YtError? = null,
    /** A channel search found nothing. */
    val channelNotFound: Boolean = false,
    val next: NextSuggestion = NextSuggestion(),
    val replying: Boolean = false,
    val replyErrors: List<AiError>? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GrowViewModel @Inject constructor(
    private val api: YouTubeApi,
    keys: YouTubeKeyStore,
    private val myChannel: MyChannelRepository,
    private val snapshots: ChannelSnapshotRepository,
    private val drafts: CommentDraftRepository,
    private val tests: AbTestRepository,
    private val projects: ProjectRepository,
    private val ai: AiTasks,
) : ViewModel() {

    private val local = MutableStateFlow(GrowUiState())

    val uiState: StateFlow<GrowUiState> = combine(
        local,
        keys.hasKey,
        myChannel.channel,
        myChannel.channel.flatMapLatest { channel -> channel?.let { snapshots.observeForChannel(it.id) } ?: flowOf(emptyList()) },
        combine(drafts.observeAll(), tests.observeAll(), ::Pair),
    ) { state, hasKey, channel, history, (draftList, testList) ->
        state.copy(hasKey = hasKey, channel = channel, snapshots = history, drafts = draftList, tests = testList)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GrowUiState())

    fun selectTab(tab: GrowTab) = local.update { it.copy(tab = tab) }
    fun setPeriod(days: Int) = local.update { it.copy(periodDays = days) }

    /** Finds the user's channel by name, @handle or link, remembers it and takes the first snapshot. */
    fun connect(query: String) {
        val text = query.trim()
        if (text.isEmpty() || local.value.loading) return
        local.update { it.copy(loading = true, error = null, channelNotFound = false) }
        viewModelScope.launch {
            when (val result = api.findChannel(parseChannelQuery(text))) {
                is YtOutcome.Ok -> {
                    val channel = result.value
                    if (channel == null) {
                        local.update { it.copy(channelNotFound = true) }
                    } else {
                        myChannel.set(MyChannel(channel.id, channel.title))
                        snapshots.save(ChannelSnapshot(channelId = channel.id, subscriberCount = channel.subscribers ?: 0, viewCount = channel.views, videoCount = channel.videos))
                    }
                }
                is YtOutcome.Failed -> local.update { it.copy(error = result.error) }
            }
            local.update { it.copy(loading = false) }
        }
    }

    fun disconnect() {
        viewModelScope.launch { myChannel.clear() }
    }

    /** Reads the channel's current numbers and stores them as a new snapshot. */
    fun refresh() {
        val channel = uiState.value.channel ?: return
        if (local.value.loading) return
        local.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = api.channels(listOf(channel.id))) {
                is YtOutcome.Ok -> result.value.firstOrNull()?.let {
                    snapshots.save(ChannelSnapshot(channelId = it.id, subscriberCount = it.subscribers ?: 0, viewCount = it.views, videoCount = it.videos))
                }
                is YtOutcome.Failed -> local.update { it.copy(error = result.error) }
            }
            local.update { it.copy(loading = false) }
        }
    }

    /** Called when the tab opens: takes a snapshot if the last one is older than 12 hours. */
    fun captureIfDue(nowMillis: Long = System.currentTimeMillis()) {
        val state = uiState.value
        if (state.hasKey && state.channel != null && shouldCapture(state.snapshots.lastOrNull()?.capturedAt, nowMillis)) refresh()
    }

    fun suggestNext() {
        val state = uiState.value
        val channel = state.channel ?: return
        if (state.next.loading) return
        local.update { it.copy(next = NextSuggestion(loading = true)) }
        viewModelScope.launch {
            val top = when (val result = api.topVideos(channel.id)) {
                is YtOutcome.Ok -> result.value
                is YtOutcome.Failed -> return@launch local.update { it.copy(next = NextSuggestion(youTubeError = result.error)) }
            }
            val latest = state.snapshots.lastOrNull()
            val stats = latest?.let { "${it.subscriberCount} subscribers, ${it.viewCount} views, ${it.videoCount} videos" }.orEmpty()
            val outcome = ai.nextTopic(
                channel = channel.title,
                stats = stats,
                topVideos = top.map { "${it.title} (${it.views} views)" },
                comments = state.drafts.take(RECENT_COMMENTS).map { it.comment },
            )
            local.update {
                it.copy(
                    next = when (outcome) {
                        is AiOutcome.Ok -> NextSuggestion(topic = outcome.value)
                        is AiOutcome.Failed -> NextSuggestion(aiErrors = outcome.errors)
                    },
                )
            }
        }
    }

    /** Starts a project from the suggested topic. */
    fun startSuggestion(onCreated: (projectId: Long) -> Unit) {
        val topic = uiState.value.next.topic ?: return
        viewModelScope.launch {
            val brief = listOf(topic.topic, topic.reason).filter { it.isNotBlank() }.joinToString("\n")
            onCreated(projects.save(Project(title = topic.topic, format = VideoFormat.LONG_VIDEO, brief = brief)))
        }
    }

    fun draftReply(comment: String, tone: ReplyTone) {
        val text = comment.trim()
        if (text.isEmpty() || local.value.replying) return
        local.update { it.copy(replying = true, replyErrors = null) }
        viewModelScope.launch {
            when (val outcome = ai.replyDraft(text, tone.name.lowercase())) {
                is AiOutcome.Ok -> drafts.save(CommentDraft(comment = text, reply = outcome.value))
                is AiOutcome.Failed -> local.update { it.copy(replyErrors = outcome.errors) }
            }
            local.update { it.copy(replying = false) }
        }
    }

    fun setDraftStatus(draft: CommentDraft, status: DraftStatus) {
        viewModelScope.launch { drafts.save(draft.copy(status = status)) }
    }

    fun deleteDraft(draft: CommentDraft) {
        viewModelScope.launch { drafts.delete(draft.id) }
    }

    fun createTest(name: String, kind: TestKind, variantA: String, variantB: String, notes: String) {
        if (name.isBlank() || variantA.isBlank() || variantB.isBlank()) return
        viewModelScope.launch {
            tests.save(AbTest(name = name.trim(), kind = kind, variantA = variantA.trim(), variantB = variantB.trim(), notes = notes.trim()))
        }
    }

    fun setWinner(test: AbTest, winner: TestVariant?) {
        viewModelScope.launch {
            tests.save(test.copy(winner = winner, endedAt = if (winner == null) null else System.currentTimeMillis()))
        }
    }

    fun deleteTest(test: AbTest) {
        viewModelScope.launch { tests.delete(test.id) }
    }

    private companion object {
        const val RECENT_COMMENTS = 10
    }
}
