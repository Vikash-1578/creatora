package com.creatora.app.data.cloud

import android.content.Context
import com.creatora.app.domain.repository.AuthorizationOutcome
import com.creatora.app.domain.repository.YouTubeAuthorizer
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/** Google Identity AuthorizationClient. Returns a token silently when the user already agreed, otherwise shows Google's screen. */
class GoogleYouTubeAuthorizer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: CloudConfig,
    private val bridge: ActivityBridge,
) : YouTubeAuthorizer {

    override suspend fun authorize(scopes: List<String>): AuthorizationOutcome {
        if (!config.isConfigured) return AuthorizationOutcome.NotConfigured
        val request = AuthorizationRequest.builder().setRequestedScopes(scopes.map { Scope(it) }).build()
        val client = Identity.getAuthorizationClient(context)
        return try {
            var result = client.authorize(request).await()
            if (result.hasResolution()) {
                val screen = result.pendingIntent ?: return AuthorizationOutcome.Failed
                val data = bridge.requestConsent(screen) ?: return AuthorizationOutcome.Denied
                result = client.getAuthorizationResultFromIntent(data)
            }
            result.accessToken?.let { AuthorizationOutcome.Granted(it) } ?: AuthorizationOutcome.Failed
        } catch (e: ApiException) {
            if (e.statusCode == CommonStatusCodes.CANCELED) AuthorizationOutcome.Denied else AuthorizationOutcome.Failed
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AuthorizationOutcome.Failed
        }
    }
}
