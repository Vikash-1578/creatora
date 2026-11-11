package com.creatora.app.data.cloud

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import androidx.activity.result.IntentSenderRequest
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred

/**
 * Gives the data layer the foreground Activity (needed by Google sign-in and Play Billing) and a way to show a
 * Google permission screen and wait for the result. MainActivity attaches itself in onCreate.
 */
@Singleton
class ActivityBridge @Inject constructor() {
    private var activityRef: WeakReference<Activity>? = null
    private var launcher: ((IntentSenderRequest) -> Unit)? = null
    private var pending: CompletableDeferred<Intent?>? = null

    val activity: Activity? get() = activityRef?.get()

    fun attach(activity: Activity, launchIntentSender: (IntentSenderRequest) -> Unit) {
        activityRef = WeakReference(activity)
        launcher = launchIntentSender
    }

    fun detach(activity: Activity) {
        if (activityRef?.get() === activity) {
            activityRef = null
            launcher = null
            pending?.complete(null)
            pending = null
        }
    }

    /** Shows Google's permission screen. Returns the result data, or null if there is no screen to show it on or it was closed. */
    suspend fun requestConsent(intent: PendingIntent): Intent? {
        val launch = launcher ?: return null
        val deferred = CompletableDeferred<Intent?>().also { pending = it }
        launch(IntentSenderRequest.Builder(intent.intentSender).build())
        return deferred.await()
    }

    fun onConsentResult(data: Intent?) {
        pending?.complete(data)
        pending = null
    }
}
