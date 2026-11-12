package com.creatora.app.data.publish

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.creatora.app.R
import com.creatora.app.core.withLanguage
import com.creatora.app.data.notify.Notifier
import com.creatora.app.data.youtube.YouTubeUploader
import com.creatora.app.domain.account.AdapterError
import com.creatora.app.domain.account.AdapterOutcome
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.account.NotificationCategory
import com.creatora.app.domain.platform.PostDetails
import com.creatora.app.domain.platform.PublishRequest
import com.creatora.app.domain.platform.PublishSource
import com.creatora.app.domain.platform.PublishStatus
import com.creatora.app.domain.platform.PublisherRegistry
import com.creatora.app.domain.publish.PostStatus
import com.creatora.app.domain.publish.ScheduledPost
import com.creatora.app.domain.publish.canMove
import com.creatora.app.domain.publish.captionWithTags
import com.creatora.app.domain.publish.shouldPollViews
import com.creatora.app.domain.repository.ErrorReporter
import com.creatora.app.domain.repository.NotificationInboxRepository
import com.creatora.app.domain.repository.NotificationPrefsRepository
import com.creatora.app.domain.repository.ProjectRepository
import com.creatora.app.domain.repository.ScheduledPostRepository
import com.creatora.app.domain.repository.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PublishEntryPoint {
    fun posts(): ScheduledPostRepository
    fun projects(): ProjectRepository
    fun publishers(): PublisherRegistry
    fun uploader(): YouTubeUploader
    fun settings(): SettingsRepository
    fun prefs(): NotificationPrefsRepository
    fun inbox(): NotificationInboxRepository
    fun errors(): ErrorReporter
}

private const val KEY_UID = "uid"
private const val KEY_POST = "post"
private const val UPLOAD_CHANNEL = "uploads"
private const val UPLOAD_NOTIFICATION = 7100
private const val FIVE_MINUTES = 5L * 60 * 1000
private const val TWO_HOURS = 2L * 60 * 60 * 1000

/** Starts and cancels the background work of a post. Each piece has a unique name per post so a retry never doubles it. */
object PublishWork {
    fun upload(context: Context, uid: String, postId: String) {
        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(workDataOf(KEY_UID to uid, KEY_POST to postId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("upload-$postId", ExistingWorkPolicy.REPLACE, request)
    }

    /** Reads the post's status (and, in its first 24 hours, its views) after [delayMillis]. */
    fun status(context: Context, uid: String, postId: String, delayMillis: Long) {
        val request = OneTimeWorkRequestBuilder<PostStatusWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(KEY_UID to uid, KEY_POST to postId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("status-$postId", ExistingWorkPolicy.REPLACE, request)
    }

    /** A "time to post" notification for a share-sheet post. */
    fun remind(context: Context, uid: String, postId: String, atMillis: Long) {
        val delay = atMillis - System.currentTimeMillis()
        if (delay <= 0) return cancelReminder(context, postId)
        val request = OneTimeWorkRequestBuilder<ShareReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(KEY_UID to uid, KEY_POST to postId))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("remind-$postId", ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelReminder(context: Context, postId: String) {
        WorkManager.getInstance(context).cancelUniqueWork("remind-$postId")
    }
}

private fun entry(context: Context) = EntryPointAccessors.fromApplication(context, PublishEntryPoint::class.java)

/** A local file from a `file://` URI, or null. */
private fun fileOf(uri: String?): File? = uri?.let { Uri.parse(it).path }?.let(::File)?.takeIf { it.exists() }

/** Saves [post] with [status] when the move is allowed. Returns the saved post. */
private suspend fun move(entry: PublishEntryPoint, uid: String, post: ScheduledPost, status: PostStatus, change: (ScheduledPost) -> ScheduledPost = { it }): ScheduledPost {
    if (status != post.status && !canMove(post.status, status)) return post
    return change(post.copy(status = status)).also { entry.posts().save(uid, it) }
}

private suspend fun notifyOutcome(context: Context, entry: PublishEntryPoint, post: ScheduledPost, ok: Boolean, reason: String?) {
    if (!entry.prefs().prefs.first().isOn(NotificationCategory.POSTS)) return
    val text = context.withLanguage(entry.settings().language.first())
    val title = text.getString(if (ok) R.string.notify_post_live_title else R.string.notify_post_failed_title)
    val body = if (ok) text.getString(R.string.notify_post_live_text, post.title) else text.getString(R.string.notify_post_failed_text, post.title, reason.orEmpty())
    Notifier.post(context, NotificationCategory.POSTS, post.id.hashCode(), title, body)
    entry.inbox().add(NotificationCategory.POSTS, title, body, System.currentTimeMillis())
}

/**
 * Uploads one post to its platform in a foreground job (data sync), so it survives leaving the app or a process restart.
 * A lost connection retries with a back-off; anything the platform refuses is saved as the post's real reason.
 */
class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private fun foreground(title: String): ForegroundInfo {
        NotificationManagerCompat.from(applicationContext).createNotificationChannel(
            NotificationChannelCompat.Builder(UPLOAD_CHANNEL, NotificationManagerCompat.IMPORTANCE_LOW).setName(applicationContext.getString(R.string.upload_channel_name)).build(),
        )
        val notification = NotificationCompat.Builder(applicationContext, UPLOAD_CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher).setContentTitle(applicationContext.getString(R.string.upload_notification_title)).setContentText(title)
            .setOngoing(true).setProgress(0, 0, true).build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ForegroundInfo(UPLOAD_NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(UPLOAD_NOTIFICATION, notification)
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foreground("")

    override suspend fun doWork(): Result {
        val uid = inputData.getString(KEY_UID) ?: return Result.failure()
        val postId = inputData.getString(KEY_POST) ?: return Result.failure()
        val entry = entry(applicationContext)
        val post = entry.posts().get(uid, postId) ?: return Result.success()
        if (post.status.let { it != PostStatus.QUEUED && it != PostStatus.SCHEDULED && it != PostStatus.UPLOADING }) return Result.success()
        runCatching { setForeground(foreground(post.title)) }

        val project = post.projectId?.let { entry.projects().observe(it).first() }
        val video = fileOf(project?.renderUri)
        if (project == null || video == null) return fail(entry, uid, post, "The finished video is missing. Render it again in Create.")
        val publisher = entry.publishers().publisher(post.platform) ?: return fail(entry, uid, post, "This platform has no posting through the API.")

        val uploading = move(entry, uid, post, PostStatus.UPLOADING) { it.copy(error = null) }
        val caption = captionWithTags(project.captions[post.platform.name] ?: project.publishDescription, project.publishHashtags)
        val request = PublishRequest(
            accountId = post.accountKey?.substringAfter('_').orEmpty(),
            caption = caption,
            source = PublishSource.LocalFile(video),
            scheduledAtMillis = post.scheduledAt?.takeIf { it > System.currentTimeMillis() + FIVE_MINUTES },
            makePublic = post.privacy == "public",
            details = PostDetails(project.publishTitle.ifBlank { project.title }, project.publishTags, project.madeForKids, project.aiContent, fileOf(project.thumbnailUri)),
        )
        return when (val result = publisher.publish(request)) {
            is AdapterOutcome.Ok -> {
                val scheduled = request.scheduledAtMillis != null
                val saved = move(entry, uid, uploading, if (scheduled) PostStatus.SCHEDULED else PostStatus.PROCESSING) { it.copy(remoteId = result.value, error = null) }
                PublishWork.status(applicationContext, uid, saved.id, if (scheduled) (saved.scheduledAt!! - System.currentTimeMillis() + FIVE_MINUTES).coerceAtLeast(FIVE_MINUTES) else FIVE_MINUTES)
                Result.success()
            }
            is AdapterOutcome.Failed ->
                if (result.error == AdapterError.Network && runAttemptCount < MAX_ATTEMPTS) Result.retry()
                else fail(entry, uid, uploading, result.error.describe())
        }
    }

    private suspend fun fail(entry: PublishEntryPoint, uid: String, post: ScheduledPost, reason: String): Result {
        val failed = move(entry, uid, post, PostStatus.FAILED) { it.copy(error = reason) }
        notifyOutcome(applicationContext, entry, failed, ok = false, reason = reason)
        return Result.success()
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}

/** The platform's reason in words, as saved on a failed post. */
fun AdapterError.describe(): String = when (this) {
    AdapterError.NotAuthorized -> "Sign in to the platform again, then retry."
    AdapterError.PermissionMissing -> "A permission was not granted."
    AdapterError.Network -> "No connection."
    AdapterError.QuotaExceeded -> "The platform's limit for today was reached."
    AdapterError.NotFound -> "The platform could not find it."
    is AdapterError.NotAvailable -> reason
}

/**
 * Reads where a post stands: Processing, Live, Scheduled or private until the platform approves. In the first 24 hours
 * of a live YouTube post it also reads the views every 2 hours ("views so far, as of ...").
 */
class PostStatusWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val uid = inputData.getString(KEY_UID) ?: return Result.failure()
        val postId = inputData.getString(KEY_POST) ?: return Result.failure()
        val entry = entry(applicationContext)
        var post = entry.posts().get(uid, postId) ?: return Result.success()
        val remote = post.remoteId ?: return Result.success()
        val publisher = entry.publishers().publisher(post.platform) ?: return Result.success()
        val now = System.currentTimeMillis()

        if (post.status != PostStatus.LIVE) {
            when (val result = publisher.status(post.accountKey?.substringAfter('_').orEmpty(), remote)) {
                is AdapterOutcome.Failed -> return if (result.error == AdapterError.Network) Result.retry() else Result.success()
                is AdapterOutcome.Ok -> when (val s = result.value) {
                    is PublishStatus.Processing -> { PublishWork.status(applicationContext, uid, postId, FIVE_MINUTES); return Result.success() }
                    is PublishStatus.Scheduled -> { PublishWork.status(applicationContext, uid, postId, (s.atMillis!! - now + FIVE_MINUTES).coerceAtLeast(FIVE_MINUTES)); return Result.success() }
                    is PublishStatus.PrivateUntilApproved -> {
                        move(entry, uid, post, PostStatus.AWAITING_PUBLIC) { it.copy(privacy = "private", error = null) }
                        return Result.success()
                    }
                    is PublishStatus.Live -> {
                        post = move(entry, uid, post, PostStatus.LIVE) { it.copy(publishedAt = it.publishedAt ?: now, remoteUrl = s.url, error = null) }
                        notifyOutcome(applicationContext, entry, post, ok = true, reason = null)
                    }
                    is PublishStatus.Failed -> {
                        val failed = move(entry, uid, post, PostStatus.FAILED) { it.copy(error = s.reason) }
                        notifyOutcome(applicationContext, entry, failed, ok = false, reason = s.reason)
                        return Result.success()
                    }
                }
            }
        }
        // Views for the first 24 hours (YouTube only), read every 2 hours.
        if (post.platform == ConnectPlatform.YOUTUBE && shouldPollViews(post, now)) {
            (entry.uploader().views(remote) as? AdapterOutcome.Ok)?.let { post = move(entry, uid, post, PostStatus.LIVE) { p -> p.copy(views24h = it.value, viewsAsOf = now) } }
        }
        val liveAt = post.publishedAt
        if (post.status == PostStatus.LIVE && liveAt != null && now - liveAt < 24L * 60 * 60 * 1000) PublishWork.status(applicationContext, uid, postId, TWO_HOURS)
        return Result.success()
    }
}

/** "Time to post" for a post that goes out through the share sheet. Creatora cannot post it for the user, only remind. */
class ShareReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val uid = inputData.getString(KEY_UID) ?: return Result.failure()
        val postId = inputData.getString(KEY_POST) ?: return Result.failure()
        val entry = entry(applicationContext)
        val post = entry.posts().get(uid, postId) ?: return Result.success()
        if (post.status.let { it != PostStatus.SCHEDULED && it != PostStatus.QUEUED }) return Result.success()
        val text = applicationContext.withLanguage(entry.settings().language.first())
        if (entry.prefs().prefs.first().isOn(NotificationCategory.POSTS)) {
            Notifier.post(
                applicationContext, NotificationCategory.POSTS, post.id.hashCode(), text.getString(R.string.reminder_title),
                text.getString(R.string.reminder_text, post.title, post.platform.name.lowercase().replaceFirstChar { it.uppercase() }),
            )
        }
        return Result.success()
    }
}
