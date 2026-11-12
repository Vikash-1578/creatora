package com.creatora.app.ui.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creatora.app.domain.account.Plan
import com.creatora.app.domain.account.PlanResolver
import com.creatora.app.domain.ai.AiTasks
import com.creatora.app.domain.create.ConceptExtractor
import com.creatora.app.domain.create.ConceptOutcome
import com.creatora.app.domain.create.FormatAccess
import com.creatora.app.domain.create.LinkReader
import com.creatora.app.domain.create.LinkResult
import com.creatora.app.domain.create.formatAccess
import com.creatora.app.domain.home.CreatorIdea
import com.creatora.app.domain.home.IdeaStatus
import com.creatora.app.domain.model.CreateMode
import com.creatora.app.domain.model.StartSource
import com.creatora.app.domain.repository.AuthService
import com.creatora.app.domain.repository.CreatorIdeaRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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

/** How starting a project went. [Started] carries the new project's id. */
sealed interface StartResult {
    data object Idle : StartResult
    data object Working : StartResult
    data class Started(val projectId: Long) : StartResult
    data object LinkInvalid : StartResult
    data object LinkUnreadable : StartResult
    /** The AI's version stayed too close to the source, so nothing was used. */
    data object TooClose : StartResult
    data class AiFailed(val errors: List<AiError>) : StartResult
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CreateViewModel @Inject constructor(
    private val projects: ProjectRepository,
    private val ai: AiTasks,
    private val auth: AuthService,
    private val plans: PlanResolver,
    private val ideaRepository: CreatorIdeaRepository,
    private val links: LinkReader,
    private val concepts: ConceptExtractor,
) : ViewModel() {

    private val _plan = MutableStateFlow(Plan.FREE)
    /** The plan decides which format tiles are open. */
    val plan: StateFlow<Plan> = _plan.asStateFlow()

    /** Saved ideas (Home, Research, Trends, competitor concepts) to start from. */
    val savedIdeas: StateFlow<List<CreatorIdea>> = auth.user
        .flatMapLatest { user -> if (user == null) flowOf(emptyList()) else ideaRepository.observe(user.uid) }
        .map { list -> list.filter { it.status == IdeaStatus.SAVED } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _start = MutableStateFlow<StartResult>(StartResult.Idle)
    val startResult: StateFlow<StartResult> = _start.asStateFlow()

    init {
        viewModelScope.launch { auth.user.first()?.uid?.let { _plan.value = plans.plan(it) } }
    }

    fun clearStartResult() { _start.value = StartResult.Idle }

    /**
     * The three ways in: a saved [idea], the user's own [text] (a topic), or a link in [text]. A link is read, the AI writes
     * the idea behind it in new words, and only that and the link are kept. [mode] is step by step or Auto.
     */
    fun startFrom(format: VideoFormat, source: StartSource, text: String, idea: CreatorIdea?, mode: CreateMode) {
        if (_start.value is StartResult.Working || formatAccess(_plan.value, format) != FormatAccess.OPEN) return
        _start.value = StartResult.Working
        viewModelScope.launch {
            _start.value = when (source) {
                StartSource.TOPIC -> text.trim().takeIf { it.isNotEmpty() }?.let {
                    StartResult.Started(projects.save(Project(title = it.take(MAX_TITLE), format = format, brief = it, mode = mode, startedFrom = StartSource.TOPIC)))
                } ?: StartResult.Idle
                StartSource.IDEA -> idea?.let { fromIdea(format, it, mode) } ?: StartResult.Idle
                StartSource.LINK -> fromLink(format, text, mode)
            }
        }
    }

    private suspend fun fromIdea(format: VideoFormat, idea: CreatorIdea, mode: CreateMode): StartResult {
        val brief = listOf(idea.title, idea.reason).filter { it.isNotBlank() }.joinToString("\n")
        val id = projects.save(Project(title = idea.title.take(MAX_TITLE), format = format, brief = brief, mode = mode, startedFrom = StartSource.IDEA))
        auth.user.first()?.uid?.let { ideaRepository.setStatus(it, idea.id, IdeaStatus.PLANNED, id) }
        return StartResult.Started(id)
    }

    private suspend fun fromLink(format: VideoFormat, text: String, mode: CreateMode): StartResult {
        val info = when (val read = links.read(text)) {
            LinkResult.Invalid -> return StartResult.LinkInvalid
            LinkResult.Unreadable -> return StartResult.LinkUnreadable
            is LinkResult.Ok -> read.info
        }
        return when (val outcome = concepts.extract(info, topic = "")) {
            is ConceptOutcome.Failed -> StartResult.AiFailed(outcome.errors)
            ConceptOutcome.TooClose -> StartResult.TooClose
            is ConceptOutcome.Ok -> {
                val c = outcome.concept
                val brief = listOf(c.concept, c.hook).filter { it.isNotBlank() }.joinToString("\n")
                StartResult.Started(
                    projects.save(
                        Project(
                            title = c.concept.take(MAX_TITLE), format = format, brief = brief, angle = c.angle, concept = c.concept,
                            sourceLink = info.url, mode = mode, startedFrom = StartSource.LINK,
                        ),
                    ),
                )
            }
        }
    }

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

    private companion object {
        const val MAX_TITLE = 100
    }
}
