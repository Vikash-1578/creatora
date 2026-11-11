package com.creatora.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creatora.app.domain.grow.Delta
import com.creatora.app.domain.grow.MyChannel
import com.creatora.app.domain.grow.deltaOver
import com.creatora.app.domain.model.Idea
import com.creatora.app.domain.model.Project
import com.creatora.app.domain.model.ScheduledPost
import com.creatora.app.domain.model.VideoFormat
import com.creatora.app.domain.repository.ChannelSnapshotRepository
import com.creatora.app.domain.repository.IdeaRepository
import com.creatora.app.domain.repository.MyChannelRepository
import com.creatora.app.domain.repository.ProjectRepository
import com.creatora.app.domain.repository.ScheduledPostRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The next scheduled post with its project's title. */
data class NextPost(val post: ScheduledPost, val title: String)

/** Public numbers for the connected channel, each with its change over the last [HomeViewModel.SNAPSHOT_DAYS] days. */
data class ChannelStats(val channel: MyChannel, val subscribers: Delta?, val views: Delta?, val videos: Delta?)

data class HomeUiState(
    /** The project most recently worked on in the Create wizard that isn't marked ready yet. */
    val continueProject: Project? = null,
    /** Saved ideas that haven't become a project yet, newest first. */
    val ideas: List<Idea> = emptyList(),
    val nextPost: NextPost? = null,
    /** Null until a channel is connected on the Grow tab. */
    val channel: ChannelStats? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val projects: ProjectRepository,
    private val ideas: IdeaRepository,
    posts: ScheduledPostRepository,
    myChannel: MyChannelRepository,
    snapshots: ChannelSnapshotRepository,
) : ViewModel() {

    private val channelStats: Flow<ChannelStats?> = myChannel.channel.flatMapLatest { channel ->
        if (channel == null) flowOf(null) else snapshots.observeForChannel(channel.id).map { list ->
            ChannelStats(
                channel,
                deltaOver(list, SNAPSHOT_DAYS) { it.subscriberCount.takeIf { count -> count > 0 } },
                deltaOver(list, SNAPSHOT_DAYS) { it.viewCount },
                deltaOver(list, SNAPSHOT_DAYS) { it.videoCount },
            )
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(projects.observeAll(), ideas.observeAll(), posts.observeAll(), channelStats) { projectList, ideaList, postList, stats ->
        val now = System.currentTimeMillis()
        val next = postList.filter { it.scheduledAt >= now }.minByOrNull { it.scheduledAt }
        HomeUiState(
            continueProject = projectList.filter { it.isInProgress }.maxByOrNull { it.updatedAt },
            ideas = ideaList.filter { it.projectId == null }.sortedByDescending { it.createdAt }.take(MAX_IDEAS),
            nextPost = next?.let { post ->
                NextPost(post, projectList.firstOrNull { it.id == post.projectId }?.let { it.publishTitle.ifBlank { it.title } }.orEmpty())
            },
            channel = stats,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Creates a project from the composer text and passes its id to [onCreated]. */
    fun startProject(text: String, format: VideoFormat, onCreated: (Long) -> Unit) {
        val title = text.trim()
        if (title.isEmpty()) return
        viewModelScope.launch { onCreated(projects.save(Project(title = title, format = format, brief = title))) }
    }

    /** Starts a project from a saved idea, like the Saved screen does, and links the idea to it. */
    fun useIdea(idea: Idea, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val brief = listOf(idea.title, idea.notes).filter { it.isNotBlank() }.joinToString("\n")
            val id = projects.save(Project(title = idea.title, format = VideoFormat.LONG_VIDEO, brief = brief))
            ideas.save(idea.copy(projectId = id))
            onCreated(id)
        }
    }

    companion object {
        const val SNAPSHOT_DAYS = 7
        const val MAX_IDEAS = 2
    }
}
