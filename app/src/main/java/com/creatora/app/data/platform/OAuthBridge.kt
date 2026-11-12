package com.creatora.app.data.platform

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.creatora.app.data.cloud.ActivityBridge
import com.creatora.app.domain.platform.OAuthBrowser
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred

/**
 * Opens a platform's sign-in page in the browser (or the platform's own app) and waits for the redirect page to send
 * the user back through `creatora://oauth?...`. MainActivity calls [deliver] with that link, and [onResumed] when it
 * comes to the front, so closing the browser without finishing ends the wait instead of hanging.
 */
@Singleton
class OAuthBridge @Inject constructor(private val bridge: ActivityBridge) : OAuthBrowser {
    private var pending: CompletableDeferred<String?>? = null
    private val handler = Handler(Looper.getMainLooper())

    override suspend fun authorize(url: String): String? {
        val activity = bridge.activity ?: return null
        pending?.complete(null)
        val waiting = CompletableDeferred<String?>().also { pending = it }
        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        return try {
            waiting.await()
        } finally {
            if (pending === waiting) pending = null
        }
    }

    fun deliver(link: Uri?) {
        pending?.complete(link?.toString())
        pending = null
    }

    /** The app came back to the front. If the redirect has not arrived a moment later, the user closed the browser. */
    fun onResumed() {
        val waiting = pending ?: return
        handler.postDelayed({ if (pending === waiting) { waiting.complete(null); pending = null } }, GRACE_MILLIS)
    }

    private companion object {
        const val GRACE_MILLIS = 1_500L
    }
}
