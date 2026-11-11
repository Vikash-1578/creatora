package com.creatora.app.domain.repository

import com.creatora.app.domain.account.AppLanguage
import com.creatora.app.domain.account.ConnectedAccount
import com.creatora.app.domain.account.CreatorProfile
import com.creatora.app.domain.account.NotificationPrefs
import com.creatora.app.domain.account.Plan
import kotlinx.coroutines.flow.Flow

/** The signed-in Google account. */
data class AuthUser(val uid: String, val name: String, val email: String, val photoUrl: String?)

sealed interface SignInOutcome {
    data class Ok(val user: AuthUser) : SignInOutcome
    data object Cancelled : SignInOutcome
    /** This build has no Firebase configuration (google-services.json), so cloud sign-in cannot work. */
    data object NotConfigured : SignInOutcome
    data object Failed : SignInOutcome
}

enum class DeleteOutcome { DELETED, NEEDS_RECENT_SIGN_IN, FAILED }

interface AuthService {
    /** Null when nobody is signed in. */
    val user: Flow<AuthUser?>
    suspend fun signInWithGoogle(): SignInOutcome
    suspend fun signOut()

    /** Deletes the sign-in itself. Cloud data is deleted first by the caller. */
    suspend fun deleteAccount(): DeleteOutcome
}

/** The user's cloud profile, `users/{uid}`. */
data class UserDoc(
    val uid: String,
    val name: String,
    val email: String,
    val photoUrl: String?,
    val plan: Plan,
    val language: String,
    val createdAt: Long,
    val creatorProfile: CreatorProfile? = null,
)

interface UserRepository {
    fun observe(uid: String): Flow<UserDoc?>

    /** Creates the document on first sign-in. An existing document (and its plan) is left as it is. */
    suspend fun createIfMissing(user: AuthUser, language: AppLanguage)
    suspend fun setLanguage(uid: String, language: AppLanguage)
    suspend fun setCreatorProfile(uid: String, profile: CreatorProfile)

    /** Removes the user's document and everything under it. */
    suspend fun deleteAll(uid: String)
}

interface UsageRepository {
    /** AI actions used in [monthKey]. */
    fun observe(uid: String, monthKey: String): Flow<Int>
    suspend fun increment(uid: String, monthKey: String)
}

interface ConnectedAccountRepository {
    fun observe(uid: String): Flow<List<ConnectedAccount>>
    suspend fun save(uid: String, account: ConnectedAccount)
    suspend fun remove(uid: String, key: String)
}

data class AiHistoryEntry(val id: String, val prompt: String, val createdAt: Long)

interface AiHistoryRepository {
    /** Newest first. */
    fun observe(uid: String): Flow<List<AiHistoryEntry>>
    suspend fun add(uid: String, prompt: String, createdAt: Long)
    suspend fun clear(uid: String)
}

interface GoalRepository {
    /** Newest first. */
    fun observe(uid: String): Flow<List<com.creatora.app.domain.account.Goal>>

    /** Saves the goal and returns its id (a new id when [goal] has none). */
    suspend fun save(uid: String, goal: com.creatora.app.domain.account.Goal): String
    suspend fun delete(uid: String, id: String)
}

sealed interface AuthorizationOutcome {
    data class Granted(val accessToken: String) : AuthorizationOutcome
    /** The user closed the permission screen or said no. */
    data object Denied : AuthorizationOutcome
    data object NotConfigured : AuthorizationOutcome
    data object Failed : AuthorizationOutcome
}

/** Asks Google for access to the user's YouTube data. Silent when the user has already agreed. */
interface YouTubeAuthorizer {
    suspend fun authorize(scopes: List<String>): AuthorizationOutcome
}

/** Notification choices and the numbers the background check compares against. */
interface NotificationPrefsRepository {
    val prefs: Flow<NotificationPrefs>
    suspend fun setEnabled(category: com.creatora.app.domain.account.NotificationCategory, enabled: Boolean)
    suspend fun setMilestone(views: Long)

    /** A number remembered from the last background check (such as a channel's total views); null before the first check. */
    suspend fun lastValue(key: String): Long?
    suspend fun setLastValue(key: String, value: Long)
}

data class BillingState(
    /** False until Play Billing is ready and the Pro product exists in Play Console. */
    val available: Boolean = false,
    /** The localised price shown on the upgrade button, e.g. "₹199.00". */
    val price: String? = null,
    val hasPro: Boolean = false,
)

enum class LaunchOutcome { STARTED, UNAVAILABLE }

interface BillingRepository {
    val state: Flow<BillingState>
    suspend fun refresh()
    suspend fun launchUpgrade(): LaunchOutcome
}
