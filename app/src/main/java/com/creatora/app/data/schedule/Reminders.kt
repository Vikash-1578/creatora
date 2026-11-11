package com.creatora.app.data.schedule

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.creatora.app.MainActivity
import com.creatora.app.R
import com.creatora.app.domain.publish.reminderDelayMillis
import com.creatora.app.domain.repository.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val CHANNEL_ID = "post_reminders"
private const val KEY_POST = "post"
private const val KEY_PROJECT = "project"
private const val KEY_TITLE = "title"
private const val KEY_PLATFORM = "platform"

/** Shows "time to post" for one scheduled post. Everything it needs travels in the work's input data. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!allowed) return Result.success()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.reminder_channel_name), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val postId = inputData.getLong(KEY_POST, 0)
        val open = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_PUBLISH_PROJECT_ID, inputData.getLong(KEY_PROJECT, 0))
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val tap = PendingIntent.getActivity(context, postId.toInt(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(context.getString(R.string.reminder_text, inputData.getString(KEY_TITLE).orEmpty(), inputData.getString(KEY_PLATFORM).orEmpty()))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        manager.notify(postId.toInt(), notification)
        return Result.success()
    }
}

class WorkManagerReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReminderScheduler {

    override fun schedule(postId: Long, projectId: Long, title: String, platformName: String, atMillis: Long) {
        val delay = reminderDelayMillis(atMillis, System.currentTimeMillis()) ?: return cancel(postId)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(KEY_POST to postId, KEY_PROJECT to projectId, KEY_TITLE to title, KEY_PLATFORM to platformName))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(postId), ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel(postId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(postId))
    }

    private fun workName(postId: Long) = "post-$postId"
}
