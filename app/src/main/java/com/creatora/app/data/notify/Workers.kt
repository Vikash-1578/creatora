package com.creatora.app.data.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.creatora.app.R
import com.creatora.app.core.withLanguage
import com.creatora.app.domain.account.AdapterError
import com.creatora.app.domain.account.AdapterOutcome
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.account.NotificationCategory
import com.creatora.app.domain.account.PlatformRef
import com.creatora.app.domain.account.PlatformRegistry
import com.creatora.app.domain.account.crossedMilestone
import com.creatora.app.domain.repository.AuthService
import com.creatora.app.domain.repository.ConnectedAccountRepository
import com.creatora.app.domain.repository.NotificationPrefsRepository
import com.creatora.app.domain.repository.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.text.NumberFormat
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PollEntryPoint {
    fun auth(): AuthService
    fun accounts(): ConnectedAccountRepository
    fun prefs(): NotificationPrefsRepository
    fun settings(): SettingsRepository
    fun platforms(): PlatformRegistry
}

/**
 * Every few hours: looks at the connected YouTube channel and sends a notification when its total views pass the
 * user's milestone, or when recent videos have new comments. Comments, milestones and earnings move to Cloud
 * Functions later; earnings are not checked here.
 */
class YouTubePollWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val entry = EntryPointAccessors.fromApplication(applicationContext, PollEntryPoint::class.java)
        val user = entry.auth().user.first() ?: return Result.success()
        val account = entry.accounts().observe(user.uid).first().firstOrNull { it.platform == ConnectPlatform.YOUTUBE }
            ?: return Result.success()
        val prefs = entry.prefs()
        val settings = prefs.prefs.first()
        val adapter = entry.platforms().adapter(ConnectPlatform.YOUTUBE)
        val text = applicationContext.withLanguage(entry.settings().language.first())
        val numbers = NumberFormat.getIntegerInstance()

        val channel = when (val result = adapter.lookup(PlatformRef(ConnectPlatform.YOUTUBE, account.id, null))) {
            is AdapterOutcome.Ok -> result.value
            is AdapterOutcome.Failed -> return if (result.error == AdapterError.Network) Result.retry() else Result.success()
        }

        val views = channel.viewCount
        if (views != null) {
            val key = "views_${account.id}"
            val last = prefs.lastValue(key)
            if (last != null && settings.isOn(NotificationCategory.VIEW_MILESTONES)) {
                crossedMilestone(last, views, settings.milestoneViews)?.let { milestone ->
                    Notifier.post(
                        applicationContext, NotificationCategory.VIEW_MILESTONES, MILESTONE_ID,
                        text.getString(R.string.notify_milestone_title, numbers.format(milestone)),
                        text.getString(R.string.notify_milestone_text, channel.title, numbers.format(views)),
                    )
                }
            }
            prefs.setLastValue(key, views)
        }

        val videos = adapter.recentVideos(account.id, COMMENT_VIDEOS)
        if (videos is AdapterOutcome.Ok) {
            val total = videos.value.sumOf { it.comments ?: 0L }
            val key = "comments_${account.id}"
            val last = prefs.lastValue(key)
            if (last != null && total > last && settings.isOn(NotificationCategory.NEW_COMMENTS)) {
                val added = (total - last).toInt()
                Notifier.post(
                    applicationContext, NotificationCategory.NEW_COMMENTS, COMMENTS_ID,
                    text.resources.getQuantityString(R.plurals.notify_comments_title, added, added),
                    text.getString(R.string.notify_comments_text),
                )
            }
            prefs.setLastValue(key, total)
        }
        return Result.success()
    }

    private companion object {
        const val MILESTONE_ID = 2001
        const val COMMENTS_ID = 2002
        const val COMMENT_VIDEOS = 15
    }
}

/** Once a day, a reminder to ask for ideas. It does not call the AI or claim to contain ideas. */
class DailyIdeasWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val entry = EntryPointAccessors.fromApplication(applicationContext, PollEntryPoint::class.java)
        if (entry.auth().user.first() == null || !entry.prefs().prefs.first().isOn(NotificationCategory.DAILY_IDEAS)) return Result.success()
        val text = applicationContext.withLanguage(entry.settings().language.first())
        Notifier.post(
            applicationContext, NotificationCategory.DAILY_IDEAS, IDEAS_ID,
            text.getString(R.string.notify_ideas_title), text.getString(R.string.notify_ideas_text),
        )
        return Result.success()
    }

    private companion object {
        const val IDEAS_ID = 2003
    }
}

object PollScheduler {
    fun schedule(context: Context) {
        val work = WorkManager.getInstance(context)
        val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        work.enqueueUniquePeriodicWork(
            "youtube-poll",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<YouTubePollWorker>(6, TimeUnit.HOURS).setConstraints(online).build(),
        )
        work.enqueueUniquePeriodicWork(
            "daily-ideas",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DailyIdeasWorker>(1, TimeUnit.DAYS).setInitialDelay(millisUntilNineAm(), TimeUnit.MILLISECONDS).build(),
        )
    }

    private fun millisUntilNineAm(now: Calendar = Calendar.getInstance()): Long {
        val next = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        return next.timeInMillis - now.timeInMillis
    }
}
