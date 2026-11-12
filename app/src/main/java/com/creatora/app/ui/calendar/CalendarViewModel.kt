package com.creatora.app.ui.calendar

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creatora.app.data.publish.PublishWork
import com.creatora.app.data.youtube.YouTubeUploader
import com.creatora.app.domain.account.AdapterOutcome
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.publish.PostStatus
import com.creatora.app.domain.publish.ScheduledPost
import com.creatora.app.domain.publish.canMove
import com.creatora.app.domain.publish.canReschedule
import com.creatora.app.domain.publish.monthGrid
import com.creatora.app.domain.publish.movedTo
import com.creatora.app.domain.publish.postsByDay
import com.creatora.app.domain.publish.weekOf
import com.creatora.app.domain.repository.AuthService
import com.creatora.app.domain.repository.ProjectRepository
import com.creatora.app.domain.repository.ScheduledPostRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CalendarMode { MONTH, WEEK }

/** Why a move or cancel did not happen. */
enum class CalendarProblem { PAST, CANNOT_MOVE, PLATFORM_REFUSED, SAVE_FAILED, OPEN_IN_YOUTUBE }

data class CalendarUiState(
    val month: LocalDate = LocalDate.now().withDayOfMonth(1),
    val mode: CalendarMode = CalendarMode.MONTH,
    val selected: LocalDate = LocalDate.now(),
    val weeks: List<List<LocalDate>> = emptyList(),
    val byDay: Map<LocalDate, List<ScheduledPost>> = emptyMap(),
    /** The post waiting to be dropped on another day (tap Move, then tap the day). */
    val moving: ScheduledPost? = null,
    val problem: CalendarProblem? = null,
    val today: LocalDate = LocalDate.now(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val auth: AuthService,
    private val posts: ScheduledPostRepository,
    private val projects: ProjectRepository,
    private val uploader: YouTubeUploader,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val zone = ZoneId.systemDefault()
    private val firstDay = java.time.temporal.WeekFields.of(Locale.getDefault()).firstDayOfWeek
    private val view = MutableStateFlow(CalendarUiState())

    val state: StateFlow<CalendarUiState> = combine(
        view,
        auth.user.flatMapLatest { user -> if (user == null) flowOf(emptyList()) else posts.observe(user.uid) },
    ) { v, list ->
        v.copy(
            weeks = if (v.mode == CalendarMode.MONTH) monthGrid(v.month, firstDay) else listOf(weekOf(v.selected, firstDay)),
            byDay = postsByDay(list, zone),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    fun setMode(mode: CalendarMode) = view.update { it.copy(mode = mode) }
    fun select(day: LocalDate) = view.update { it.copy(selected = day, month = day.withDayOfMonth(1)) }
    fun step(delta: Int) = view.update {
        if (it.mode == CalendarMode.MONTH) it.copy(month = it.month.plusMonths(delta.toLong()), selected = it.month.plusMonths(delta.toLong()))
        else it.copy(selected = it.selected.plusWeeks(delta.toLong()), month = it.selected.plusWeeks(delta.toLong()).withDayOfMonth(1))
    }

    fun startMove(post: ScheduledPost) = view.update { it.copy(moving = post, problem = null) }
    fun cancelMove() = view.update { it.copy(moving = null) }
    fun dismissProblem() = view.update { it.copy(problem = null) }

    /** Drops the post being moved on [day]: it keeps its time of day. YouTube posts change `publishAt`; reminders are re-set. */
    fun moveTo(day: LocalDate) {
        val post = view.value.moving ?: return
        viewModelScope.launch {
            val user = auth.user.first() ?: return@launch
            val now = System.currentTimeMillis()
            val target = movedTo(post, day, zone)
            when {
                !canReschedule(post, now) -> return@launch problem(CalendarProblem.CANNOT_MOVE)
                target <= now -> return@launch problem(CalendarProblem.PAST)
            }
            if (post.platform == ConnectPlatform.YOUTUBE && post.remoteId != null) {
                val project = post.projectId?.let { projects.observe(it).first() }
                val kids = project?.madeForKids ?: return@launch problem(CalendarProblem.CANNOT_MOVE)
                val synthetic = project.aiContent ?: return@launch problem(CalendarProblem.CANNOT_MOVE)
                if (uploader.reschedule(post.remoteId, target, kids, synthetic) is AdapterOutcome.Failed) return@launch problem(CalendarProblem.PLATFORM_REFUSED)
            } else if (post.remoteId == null && post.status == PostStatus.SCHEDULED) {
                PublishWork.remind(context, user.uid, post.id, target)
            }
            runCatching { posts.save(user.uid, post.copy(scheduledAt = target)) }.onFailure { return@launch problem(CalendarProblem.SAVE_FAILED) }
            view.update { it.copy(moving = null, selected = day, problem = null) }
        }
    }

    /** Cancelling a reminder or a post that has not been uploaded. An uploaded YouTube video is deleted in YouTube, not here. */
    fun cancel(post: ScheduledPost) {
        viewModelScope.launch {
            val user = auth.user.first() ?: return@launch
            if (post.remoteId != null && post.status != PostStatus.FAILED) return@launch problem(CalendarProblem.OPEN_IN_YOUTUBE)
            PublishWork.cancelReminder(context, post.id)
            runCatching { posts.delete(user.uid, post.id) }.onFailure { problem(CalendarProblem.SAVE_FAILED) }
        }
    }

    /** Retry a failed upload: it goes back to Queued and the upload starts again. */
    fun retry(post: ScheduledPost) {
        if (!canMove(post.status, PostStatus.QUEUED)) return
        viewModelScope.launch {
            val user = auth.user.first() ?: return@launch
            posts.save(user.uid, post.copy(status = PostStatus.QUEUED, error = null))
            PublishWork.upload(context, user.uid, post.id)
        }
    }

    private fun problem(problem: CalendarProblem) = view.update { it.copy(problem = problem) }
}
