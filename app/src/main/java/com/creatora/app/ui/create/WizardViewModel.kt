package com.creatora.app.ui.create

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creatora.app.domain.account.Plan
import com.creatora.app.domain.account.PlanLimits
import com.creatora.app.domain.account.PlanResolver
import com.creatora.app.domain.account.monthKey
import com.creatora.app.domain.ai.AiTasks
import com.creatora.app.domain.create.AutoPlan
import com.creatora.app.domain.create.AiImages
import com.creatora.app.domain.create.ImageOutcome
import com.creatora.app.domain.create.autoPlan
import com.creatora.app.domain.create.parseBrief
import com.creatora.app.domain.create.replaceLine
import com.creatora.app.domain.create.stepApproved
import com.creatora.app.domain.create.stepChanged
import com.creatora.app.domain.home.dayKey
import com.creatora.app.domain.model.CreateMode
import com.creatora.app.domain.render.splitSentences
import com.creatora.app.domain.repository.AuthService
import com.creatora.app.domain.repository.DailyUsage
import com.creatora.app.domain.repository.DailyUsageRepository
import com.creatora.app.domain.repository.UsageRepository
import com.creatora.app.domain.score.VideoScore
import com.creatora.app.domain.score.videoScore
import com.creatora.app.domain.model.AiError
import com.creatora.app.domain.model.AiOutcome
import com.creatora.app.domain.model.Character
import com.creatora.app.domain.model.PublishText
import com.creatora.app.domain.model.ScriptDraft
import com.creatora.app.domain.model.Project
import com.creatora.app.domain.model.RenderEstimate
import com.creatora.app.domain.model.VideoFormat
import com.creatora.app.domain.model.VoicePreset
import com.creatora.app.domain.model.WizardStep
import com.creatora.app.domain.model.applyPronunciations
import com.creatora.app.domain.model.estimateRender
import com.creatora.app.domain.model.readingSeconds
import com.creatora.app.domain.model.wordCount
import com.creatora.app.domain.repository.AssetRepository
import com.creatora.app.domain.repository.CharacterRepository
import com.creatora.app.domain.repository.LicenceRepository
import com.creatora.app.domain.repository.ProjectRepository
import com.creatora.app.domain.repository.SpeechPlayer
import com.creatora.app.domain.repository.VoicePresetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Reasons a project can't be marked ready yet. */
enum class Blocker { EMPTY_SCRIPT }

/** State of the AI helpers on the Script and Review steps. */
data class WizardAiState(
    val busy: Boolean = false,
    val errors: List<AiError>? = null,
    /** A script the AI wrote, waiting for the user to accept or drop it. */
    val draft: ScriptDraft? = null,
    val publishText: PublishText? = null,
)

/** State of the AI image maker on the Visuals step. */
data class ImageState(val busy: Boolean = false, val outcome: ImageOutcome? = null)

enum class AutoStep { BRIEF, SCRIPT, IMAGES }

sealed interface AutoStatus {
    data object Waiting : AutoStatus
    data object Running : AutoStatus
    data object Done : AutoStatus
    /** Skipped or failed, with the AI's reasons when there were any. */
    data class Stopped(val errors: List<AiError>) : AutoStatus
}

/** Auto mode: the plan and cost shown for confirmation, then the progress of each step. */
data class AutoRun(val plan: AutoPlan, val started: Boolean = false, val finished: Boolean = false, val steps: Map<AutoStep, AutoStatus> = emptyMap())

data class WizardUiState(
    /** Null while the project is loading. */
    val project: Project? = null,
    val characters: List<Character> = emptyList(),
    val voices: List<VoicePreset> = emptyList(),
    val importedAssets: Int = 0,
    /** Imported assets with at least one licence log entry. */
    val licensedAssets: Int = 0,
    val speechUnavailable: Boolean = false,
    val ai: WizardAiState = WizardAiState(),
    val plan: Plan = Plan.FREE,
    val usage: DailyUsage = DailyUsage(),
    val creditsLeft: Int = 0,
    val image: ImageState = ImageState(),
    /** Set while Auto mode is waiting for confirmation, running, or showing what it did. */
    val auto: AutoRun? = null,
    /** True after Auto mode finished: the Generate step starts the render by itself. */
    val autoRender: Boolean = false,
) {
    val score: VideoScore? get() = project?.let { videoScore(it, plan, importedAssets, licensedAssets) }
    val seconds: Int get() = readingSeconds(words)
    val overPlanLength: Boolean get() = seconds > plan.limits.maxVideoSeconds

    val step: WizardStep
        get() = WizardStep.entries[(project?.step ?: 0).coerceIn(0, WizardStep.entries.lastIndex)]

    val cast: List<Character> get() = characters.filter { it.id in project?.castIds.orEmpty() }
    val words: Int get() = wordCount(project?.script.orEmpty())
    val estimate: RenderEstimate get() = estimateRender(readingSeconds(words))
    val blockers: List<Blocker> get() = if (words == 0) listOf(Blocker.EMPTY_SCRIPT) else emptyList()

    /** The Brief step needs a project name; every other step can be skipped through. */
    val canGoNext: Boolean get() = step != WizardStep.BRIEF || project?.title?.isNotBlank() == true
}

private data class LimitsState(val plan: Plan, val usage: DailyUsage, val credits: Int, val image: ImageState, val auto: AutoRun?, val autoRender: Boolean)

@OptIn(FlowPreview::class)
@HiltViewModel
class WizardViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val projects: ProjectRepository,
    private val characters: CharacterRepository,
    voices: VoicePresetRepository,
    assets: AssetRepository,
    licences: LicenceRepository,
    private val speech: SpeechPlayer,
    private val ai: AiTasks,
    private val auth: AuthService,
    private val plans: PlanResolver,
    private val daily: DailyUsageRepository,
    private val usage: UsageRepository,
    private val aiImages: AiImages,
) : ViewModel() {

    private val projectId: Long = savedStateHandle[ARG_PROJECT_ID] ?: 0L

    /** The project being edited. Edits show at once and are saved to Room after a short pause. */
    private val draft = MutableStateFlow<Project?>(null)
    private val speechUnavailable = MutableStateFlow(false)
    private val aiState = MutableStateFlow(WizardAiState())
    private val imageState = MutableStateFlow(ImageState())
    private val autoState = MutableStateFlow<AutoRun?>(null)
    private val autoRender = MutableStateFlow(false)
    private val planState = MutableStateFlow(Plan.FREE)
    private val usageState = MutableStateFlow(DailyUsage())
    private val creditsLeft = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            draft.value = projects.observe(projectId).first()
            draft.filterNotNull().drop(1).debounce(AUTOSAVE_MS).collect { projects.save(it) }
        }
        viewModelScope.launch { loadLimits() }
    }

    /** The plan, today's counters and the credits left, kept up to date for the limits shown on the steps. */
    private suspend fun loadLimits() {
        val uid = auth.user.first()?.uid ?: return
        planState.value = plans.plan(uid)
        val now = System.currentTimeMillis()
        launchCollect(daily.observe(uid, dayKey(now))) { usageState.value = it }
        launchCollect(usage.observe(uid, monthKey(now))) { creditsLeft.value = (planState.value.aiLimit - it).coerceAtLeast(0) }
        // The first project load decides whether Auto mode must ask for confirmation.
        val project = draft.filterNotNull().first()
        if (project.mode == CreateMode.AUTO && project.stepStates.isEmpty() && autoState.value == null) {
            val imagesToday = daily.observe(uid, dayKey(now)).first().aiImages
            val credits = (planState.value.aiLimit - usage.observe(uid, monthKey(now)).first()).coerceAtLeast(0)
            autoState.value = AutoRun(autoPlan(planState.value, imagesToday, credits))
        }
    }

    private fun <T> launchCollect(flow: kotlinx.coroutines.flow.Flow<T>, onEach: (T) -> Unit) {
        viewModelScope.launch { flow.collect(onEach) }
    }

    val uiState: StateFlow<WizardUiState> = combine(
        draft,
        characters.observeAll(),
        voices.observeAll(),
        combine(assets.observeAll(), licences.observeAll()) { assetList, licenceList ->
            assetList.size to assetList.count { asset -> licenceList.any { it.assetId == asset.id } }
        },
        combine(
            combine(speechUnavailable, aiState, ::Pair),
            combine(planState, usageState, creditsLeft, imageState, combine(autoState, autoRender, ::Pair)) { plan, usageToday, credits, image, (auto, render) ->
                LimitsState(plan, usageToday, credits, image, auto, render)
            },
            ::Pair,
        ),
    ) { project, characterList, voiceList, (imported, licensed), (speechAndAi, limits) ->
        WizardUiState(
            project, characterList, voiceList, imported, licensed, speechAndAi.first, speechAndAi.second,
            limits.plan, limits.usage, limits.credits, limits.image, limits.auto, limits.autoRender,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WizardUiState())

    private fun edit(transform: (Project) -> Project) {
        draft.update { it?.let(transform)?.copy(updatedAt = System.currentTimeMillis()) }
    }

    /** Applies a change made by the user to [step] and marks later finished steps as possibly out of date. */
    private fun editStep(step: WizardStep, transform: (Project) -> Project) =
        edit { transform(it).let { changed -> changed.copy(stepStates = stepChanged(changed.stepStates, step, byAi = false)) } }

    fun setTitle(title: String) = editStep(WizardStep.BRIEF) { it.copy(title = title) }
    fun setFormat(format: VideoFormat) = editStep(WizardStep.BRIEF) { it.copy(format = format) }
    fun setBrief(brief: String) = editStep(WizardStep.BRIEF) { it.copy(brief = brief) }
    fun setAngle(text: String) = editStep(WizardStep.BRIEF) { it.copy(angle = text) }
    fun setTone(text: String) = editStep(WizardStep.BRIEF) { it.copy(tone = text) }
    fun setAudience(text: String) = editStep(WizardStep.BRIEF) { it.copy(audience = text) }
    fun setTargetSeconds(seconds: Int) = editStep(WizardStep.BRIEF) { it.copy(targetSeconds = seconds.coerceAtLeast(0)) }
    fun setHook(text: String) = editStep(WizardStep.SCRIPT) { it.copy(hook = text) }
    fun setStructure(text: String) = editStep(WizardStep.SCRIPT) { it.copy(structure = text) }
    fun setCallToAction(text: String) = editStep(WizardStep.SCRIPT) { it.copy(callToAction = text) }
    fun setPronunciations(text: String) = editStep(WizardStep.SCRIPT) { it.copy(pronunciations = text) }
    fun setNarratorVoice(voiceId: Long?) = editStep(WizardStep.VOICE) { it.copy(narratorVoiceId = voiceId) }
    fun setLicensedFootageOnly(value: Boolean) = editStep(WizardStep.FOOTAGE) { it.copy(licensedFootageOnly = value) }

    fun toggleCast(characterId: Long) = editStep(WizardStep.CAST) {
        it.copy(castIds = if (characterId in it.castIds) it.castIds - characterId else it.castIds + characterId)
    }

    /** Applies an AI result to [step] as a draft the user can still edit. */
    private fun aiStep(step: WizardStep, transform: (Project) -> Project) =
        edit { transform(it).let { changed -> changed.copy(stepStates = stepChanged(changed.stepStates, step, byAi = true)) } }

    /** "Let AI set it": angle, length, tone and audience from the topic (one AI action). The Free length cap applies. */
    fun suggestBrief() {
        val project = draft.value ?: return
        val max = planState.value.limits.maxVideoSeconds
        val format = project.format?.name?.lowercase()?.replace('_', ' ') ?: "video"
        runAi({ ai.briefSuggestion(project.title, project.brief, format, max) }) { state, text ->
            parseBrief(text, max)?.let { suggestion ->
                aiStep(WizardStep.BRIEF) {
                    it.copy(
                        angle = suggestion.angle.ifBlank { it.angle }, tone = suggestion.tone.ifBlank { it.tone },
                        audience = suggestion.audience.ifBlank { it.audience }, targetSeconds = suggestion.seconds ?: it.targetSeconds,
                    )
                }
                state
            } ?: state.copy(errors = emptyList())
        }
    }

    /** Rewrites one sentence of the script ([how] is the instruction: shorter, longer, a tone, or a plain rewrite). */
    fun rewriteLine(line: String, how: String) {
        val project = draft.value ?: return
        runAi({ ai.rewriteLine(line, how, project.script) }) { state, text ->
            val clean = text.trim().trim('"')
            val latest = draft.value
            val replaced = latest?.let { replaceLine(it.hook, it.structure, it.callToAction, line, clean) }
            if (replaced == null) state.copy(errors = emptyList()) else {
                aiStep(WizardStep.SCRIPT) { it.copy(hook = replaced.first, structure = replaced.second, callToAction = replaced.third) }
                state
            }
        }
    }

    /** A shorter version of the script for the plan's length limit, waiting for the user to accept it. */
    fun shortenScript() {
        val project = draft.value ?: return
        runAi({ ai.shortenScript(project.script, planState.value.limits.maxVideoSeconds) }) { state, result -> state.copy(draft = result) }
    }

    /** One AI picture from [prompt] for the Visuals step (the daily and credit limits are checked first). */
    fun makeImage(prompt: String) {
        val project = draft.value ?: return
        if (imageState.value.busy || prompt.isBlank()) return
        imageState.value = ImageState(busy = true)
        viewModelScope.launch {
            val outcome = aiImages.make(project.id, prompt.trim())
            imageState.value = ImageState(busy = false, outcome = outcome)
            if (outcome is ImageOutcome.Ok) aiStep(WizardStep.FOOTAGE) { it }
        }
    }

    fun dismissImageMessage() { imageState.value = imageState.value.copy(outcome = null) }

    /** Asks the AI for a script from the title and brief. The result waits in [WizardAiState.draft]. */
    fun draftScript() {
        val project = draft.value ?: return
        val format = project.format?.name?.lowercase()?.replace('_', ' ') ?: "video"
        runAi({ ai.scriptDraft(project.title, project.brief, format) }) { state, result -> state.copy(draft = result) }
    }

    fun applyDraft() {
        aiState.value.draft?.let { d -> aiStep(WizardStep.SCRIPT) { it.copy(hook = d.hook, structure = d.structure, callToAction = d.callToAction) } }
        dismissDraft()
    }

    fun dismissDraft() = aiState.update { it.copy(draft = null) }

    fun generatePublishText() {
        val project = draft.value ?: return
        runAi({ ai.publishText(project.title, project.script) }) { state, result -> state.copy(publishText = result) }
    }

    fun dismissPublishText() = aiState.update { it.copy(publishText = null) }

    private fun <T> runAi(call: suspend () -> AiOutcome<T>, onOk: (WizardAiState, T) -> WizardAiState) {
        if (aiState.value.busy) return
        aiState.update { it.copy(busy = true, errors = null) }
        viewModelScope.launch {
            when (val outcome = call()) {
                is AiOutcome.Ok -> aiState.update { onOk(it.copy(busy = false), outcome.value) }
                is AiOutcome.Failed -> aiState.update { it.copy(busy = false, errors = outcome.errors) }
            }
        }
    }

    /** Gives a character a voice preset. Saved on the character, so the Library shows it too. */
    fun assignVoice(character: Character, voiceId: Long?) {
        viewModelScope.launch { characters.save(character.copy(voicePresetId = voiceId)) }
    }

    /** Moves to another step and saves right away. */
    fun goToStep(index: Int) {
        stopPreview()
        aiState.update { it.copy(errors = null) }
        if (index != WizardStep.GENERATE.ordinal) autoRender.value = false
        edit { it.copy(stepStates = if (index > it.step) stepApproved(it.stepStates, WizardStep.entries[it.step.coerceIn(0, WizardStep.entries.lastIndex)]) else it.stepStates, step = index.coerceIn(0, WizardStep.entries.lastIndex)) }
        flush()
    }

    // ------------------------------------------------------------ Auto mode

    /** Runs Brief, Script and the AI pictures in order; a step that cannot run is skipped with its reason, the rest go on. */
    fun runAuto() {
        val run = autoState.value ?: return
        if (run.started) return
        autoState.value = run.copy(started = true, steps = AutoStep.entries.associateWith { AutoStatus.Waiting })
        viewModelScope.launch {
            val project = draft.value ?: return@launch
            val max = planState.value.limits.maxVideoSeconds
            val format = project.format?.name?.lowercase()?.replace('_', ' ') ?: "video"

            setAuto(AutoStep.BRIEF, AutoStatus.Running)
            val briefStatus = when (val outcome = ai.briefSuggestion(project.title, project.brief, format, max)) {
                is AiOutcome.Ok -> parseBrief(outcome.value, max)?.let { s ->
                    aiStep(WizardStep.BRIEF) { it.copy(angle = s.angle, tone = s.tone, audience = s.audience, targetSeconds = s.seconds ?: it.targetSeconds) }
                    AutoStatus.Done
                } ?: AutoStatus.Stopped(emptyList())
                is AiOutcome.Failed -> AutoStatus.Stopped(outcome.errors)
            }
            setAuto(AutoStep.BRIEF, briefStatus)

            setAuto(AutoStep.SCRIPT, AutoStatus.Running)
            val current = draft.value ?: return@launch
            val briefText = listOf(current.brief, current.angle, current.tone, current.audience).filter { it.isNotBlank() }.joinToString("\n")
            val scriptStatus = when (val outcome = ai.scriptDraft(current.title, briefText, format)) {
                is AiOutcome.Ok -> {
                    aiStep(WizardStep.SCRIPT) { it.copy(hook = outcome.value.hook, structure = outcome.value.structure, callToAction = outcome.value.callToAction) }
                    AutoStatus.Done
                }
                is AiOutcome.Failed -> AutoStatus.Stopped(outcome.errors)
            }
            setAuto(AutoStep.SCRIPT, scriptStatus)

            setAuto(AutoStep.IMAGES, AutoStatus.Running)
            val sentences = splitSentences(draft.value?.script.orEmpty())
            val wanted = run.plan.images.coerceAtMost(sentences.size)
            var made = 0
            var stop: AutoStatus.Stopped? = null
            for (i in 0 until wanted) {
                val sentence = sentences[i * sentences.size / wanted]
                when (aiImages.make(project.id, sentence)) {
                    is ImageOutcome.Ok -> made++
                    else -> { stop = AutoStatus.Stopped(emptyList()); break }
                }
            }
            if (made > 0) aiStep(WizardStep.FOOTAGE) { it }
            setAuto(AutoStep.IMAGES, if (stop != null && made == 0) stop else AutoStatus.Done)

            autoState.update { it?.copy(finished = true) }
        }
    }

    private fun setAuto(step: AutoStep, status: AutoStatus) = autoState.update { run -> run?.copy(steps = run.steps + (step to status)) }

    /** Closes the Auto summary and opens the Generate step, which starts the render by itself. */
    fun finishAuto() {
        autoState.value = null
        autoRender.value = true
        goToStep(WizardStep.GENERATE.ordinal)
    }

    /** Leaves Auto mode for step-by-step editing, keeping whatever was made. */
    fun cancelAuto() {
        autoState.value = null
        edit { it.copy(mode = CreateMode.STEP_BY_STEP) }
        flush()
    }

    fun markReady() {
        edit { it.copy(isReady = true) }
        flush()
    }

    /** Saves the current draft now, even if this screen is closing. */
    fun flush() {
        viewModelScope.launch(NonCancellable) { draft.value?.let { projects.save(it) } }
    }

    /** Speaks the start of the script (or [fallbackText]) with [voice]; null is the phone's default voice. */
    fun preview(voice: VoicePreset?, fallbackText: String) {
        val project = draft.value ?: return
        val text = applyPronunciations(project.script.ifBlank { fallbackText }.take(PREVIEW_CHARS), project.pronunciations)
        viewModelScope.launch {
            if (!speech.speak(text, voice?.pitch ?: 1f, voice?.speechRate ?: 1f)) speechUnavailable.value = true
        }
    }

    fun stopPreview() {
        speech.stop()
    }

    override fun onCleared() {
        flush()
        speech.stop()
        speech.release()
    }

    companion object {
        const val ARG_PROJECT_ID = "projectId"
        const val AUTOSAVE_MS = 500L
        const val PREVIEW_CHARS = 240
    }
}
