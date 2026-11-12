package com.creatora.app.domain.research

import com.creatora.app.domain.account.AdapterError
import com.creatora.app.domain.account.ChannelPreview
import com.creatora.app.domain.account.PlatformRef
import com.creatora.app.domain.account.AdapterOutcome
import com.creatora.app.domain.account.VideoAnalytics
import com.creatora.app.domain.account.VideoStats
import com.creatora.app.domain.ai.AiTasks
import com.creatora.app.domain.home.CreatorIdea
import com.creatora.app.domain.home.IdeaStatus
import com.creatora.app.domain.model.AiOutcome
import com.creatora.app.domain.model.Project
import com.creatora.app.domain.model.VideoFormat
import com.creatora.app.domain.repository.CreatorIdeaRepository
import com.creatora.app.domain.repository.ProjectRepository
import javax.inject.Inject

/** What the idea bank filter shows. New is a saved idea nobody started on. */
enum class BankFilter { ALL, NEW, PLANNED, MADE }

/** An idea is Made when the project started from it was marked ready; otherwise the stored status stands. */
fun effectiveStatus(idea: CreatorIdea, projectReady: Boolean?): IdeaStatus =
    if (idea.projectId != null && projectReady == true) IdeaStatus.MADE else idea.status

/** The bank holds saved ideas only; daily ideas nobody saved and dismissed ideas are not in it. */
fun inBank(status: IdeaStatus): Boolean = status == IdeaStatus.SAVED || status == IdeaStatus.PLANNED || status == IdeaStatus.MADE

fun BankFilter.matches(status: IdeaStatus): Boolean = when (this) {
    BankFilter.ALL -> inBank(status)
    BankFilter.NEW -> status == IdeaStatus.SAVED
    BankFilter.PLANNED -> status == IdeaStatus.PLANNED
    BankFilter.MADE -> status == IdeaStatus.MADE
}

/** What starting a project from an idea gave: the project, and whether the AI script draft (if asked for) failed. */
data class StartedProject(val projectId: Long, val draftFailed: Boolean)

/** Saving ideas and turning them into projects, shared by every Research tab and the idea bank. */
class IdeaActions @Inject constructor(
    private val ideas: CreatorIdeaRepository,
    private val projects: ProjectRepository,
    private val ai: AiTasks,
) {
    /** Saves [idea] into the bank as Saved and returns its id. */
    suspend fun save(uid: String, idea: CreatorIdea): String = ideas.save(uid, idea.copy(status = IdeaStatus.SAVED))

    /**
     * Starts a project from the idea (saving the idea first when it has no id), marks it Planned and links the two. With
     * [draftWithAi] the AI writes the script draft from the idea straight away; if that fails the project still opens.
     */
    suspend fun start(uid: String, idea: CreatorIdea, draftWithAi: Boolean = false): StartedProject {
        val brief = listOf(idea.title, idea.reason).filter { it.isNotBlank() }.joinToString("\n")
        var project = Project(title = idea.title, format = VideoFormat.LONG_VIDEO, brief = brief)
        var failed = false
        if (draftWithAi) {
            when (val outcome = ai.scriptDraft(idea.title, brief, "long video")) {
                is AiOutcome.Ok -> project = project.copy(hook = outcome.value.hook, structure = outcome.value.structure, callToAction = outcome.value.callToAction)
                is AiOutcome.Failed -> failed = true
            }
        }
        val projectId = projects.save(project)
        val id = idea.id.ifBlank { ideas.save(uid, idea.copy(status = IdeaStatus.PLANNED)) }
        ideas.setStatus(uid, id, IdeaStatus.PLANNED, projectId)
        return StartedProject(projectId, failed)
    }
}

/** How fresh a Research result is: from the cache or just fetched, and whether a failed refresh left old data showing. */
sealed interface Fetched<out T> {
    data class Ok<T>(val value: T, val cachedAt: Long, val fromCache: Boolean, val stale: Boolean = false) : Fetched<T>
    data class Failed(val error: AdapterError) : Fetched<Nothing>
}

/**
 * Research data with quota-aware caching. Every call reads the cloud cache first and only goes to the platform when the
 * entry is older than its time to live (or [force] is set). When the platform fails, an older cache is shown as stale.
 */
interface ResearchRepository {
    /** A channel's latest 50 uploads (2 quota units), cached 24 hours. */
    suspend fun channelVideos(uid: String, channelId: String, force: Boolean = false): Fetched<List<VideoStats>>

    /** What is cached for a channel's uploads, however old; null when nothing is. Costs no quota. */
    suspend fun peekChannelVideos(uid: String, channelId: String): List<VideoStats>?

    /** A channel's public details (1 quota unit), cached 24 hours. */
    suspend fun channelInfo(uid: String, channelId: String, force: Boolean = false): Fetched<ChannelPreview>

    /** Channels for the niche: a channel search (100 units) plus channels featured by [sources], cached 7 days. */
    suspend fun discover(uid: String, niche: String, sources: List<String>, force: Boolean = false): Fetched<List<ChannelPreview>>

    /** The top results for a keyword with their channels' sizes (about 102 units), cached 12 hours. */
    suspend fun keyword(uid: String, query: String, force: Boolean = false): Fetched<KeywordSearch>

    /** The top comments of a video (1 unit), cached 24 hours. */
    suspend fun comments(uid: String, videoId: String, force: Boolean = false): Fetched<List<String>>

    /** A channel by link, id or handle (1 unit). Not cached. */
    suspend fun lookup(ref: PlatformRef): AdapterOutcome<ChannelPreview>

    /** Analytics of one video of a channel the user owns; null when the platform did not return them. Not cached. */
    suspend fun ownAnalytics(channelId: String, videoId: String): VideoAnalytics?
}
