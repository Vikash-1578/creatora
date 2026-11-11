package com.creatora.app.data.cloud

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.creatora.app.domain.repository.AuthService
import com.creatora.app.domain.repository.AuthUser
import com.creatora.app.domain.repository.DeleteOutcome
import com.creatora.app.domain.repository.SignInOutcome
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

/** Google sign-in through Credential Manager, then Firebase Auth. */
class FirebaseAuthService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: CloudConfig,
    private val bridge: ActivityBridge,
) : AuthService {

    override val user: Flow<AuthUser?> = if (!config.isConfigured) flowOf(null) else callbackFlow {
        val auth = FirebaseAuth.getInstance()
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.toAuthUser()) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override suspend fun signInWithGoogle(): SignInOutcome {
        val clientId = config.webClientId
        if (!config.isConfigured || clientId == null) return SignInOutcome.NotConfigured
        val activity = bridge.activity ?: return SignInOutcome.Failed
        return try {
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build())
                .build()
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val signedIn = FirebaseAuth.getInstance().signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
                signedIn.user?.toAuthUser()?.let { SignInOutcome.Ok(it) } ?: SignInOutcome.Failed
            } else {
                SignInOutcome.Failed
            }
        } catch (e: GetCredentialCancellationException) {
            SignInOutcome.Cancelled
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SignInOutcome.Failed
        }
    }

    override suspend fun signOut() {
        if (!config.isConfigured) return
        FirebaseAuth.getInstance().signOut()
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }

    override suspend fun deleteAccount(): DeleteOutcome {
        val current = if (config.isConfigured) FirebaseAuth.getInstance().currentUser else null
        if (current == null) return DeleteOutcome.FAILED
        return try {
            current.delete().await()
            DeleteOutcome.DELETED
        } catch (e: FirebaseAuthRecentLoginRequiredException) {
            DeleteOutcome.NEEDS_RECENT_SIGN_IN
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DeleteOutcome.FAILED
        }
    }
}

private fun FirebaseUser.toAuthUser() = AuthUser(uid, displayName.orEmpty(), email.orEmpty(), photoUrl?.toString())
