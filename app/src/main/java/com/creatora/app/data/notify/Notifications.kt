package com.creatora.app.data.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.creatora.app.MainActivity
import com.creatora.app.R
import com.creatora.app.domain.account.NotificationCategory

fun NotificationCategory.nameRes(): Int = when (this) {
    NotificationCategory.DAILY_IDEAS -> R.string.notify_daily_ideas
    NotificationCategory.RENDER_DONE -> R.string.notify_render_done
    NotificationCategory.NEW_COMMENTS -> R.string.notify_new_comments
    NotificationCategory.VIEW_MILESTONES -> R.string.notify_view_milestones
    NotificationCategory.EARNINGS -> R.string.notify_earnings
    NotificationCategory.PLAN_USAGE -> R.string.notify_plan_usage
    NotificationCategory.POSTS -> R.string.notify_posts
}

object NotificationChannels {
    /** One channel per category, so each can also be switched off in the system settings. */
    fun create(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        NotificationCategory.entries.forEach {
            manager.createNotificationChannel(
                NotificationChannelCompat.Builder(it.channelId, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName(context.getString(it.nameRes()))
                    .build(),
            )
        }
    }
}

object Notifier {
    /** Shows a notification that opens the app. Does nothing when the permission (Android 13+) or the app's notifications are off. */
    @SuppressLint("MissingPermission")
    fun post(context: Context, category: NotificationCategory, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        manager.notify(
            id,
            NotificationCompat.Builder(context, category.channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }
}
