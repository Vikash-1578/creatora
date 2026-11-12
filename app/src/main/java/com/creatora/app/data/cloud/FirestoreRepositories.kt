package com.creatora.app.data.cloud

import com.creatora.app.domain.account.AppLanguage
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.account.ConnectedAccount
import com.creatora.app.domain.account.CreatorProfile
import com.creatora.app.domain.account.Goal
import com.creatora.app.domain.account.GoalMetric
import com.creatora.app.domain.account.Plan
import com.creatora.app.domain.account.ProfileSource
import com.creatora.app.domain.repository.AiHistoryEntry
import com.creatora.app.domain.repository.AiHistoryRepository
import com.creatora.app.domain.repository.AuthUser
import com.creatora.app.domain.repository.ConnectedAccountRepository
import com.creatora.app.domain.repository.CacheRepository
import com.creatora.app.domain.repository.CachedText
import com.creatora.app.domain.repository.CreatorIdeaRepository
import com.creatora.app.domain.repository.GoalRepository
import com.creatora.app.domain.repository.UpcomingPost
import com.creatora.app.domain.repository.UpcomingPostRepository
import com.creatora.app.domain.home.CreatorIdea
import com.creatora.app.domain.home.IdeaStatus
import com.creatora.app.domain.repository.UsageRepository
import com.creatora.app.domain.repository.UserDoc
import com.creatora.app.domain.repository.UserRepository
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

// Data layout: users/{uid}, users/{uid}/usage/{yyyyMM}, users/{uid}/accounts/{key}, users/{uid}/aiHistory/{id}.
// Security rules (each user may only touch their own tree; plan is server-written) are set in the Firebase console.

private fun db() = FirebaseFirestore.getInstance()

private fun userDoc(uid: String) = db().collection("users").document(uid)

/** Emits the document each time it changes; null while it does not exist or cannot be read. */
private fun <T> DocumentReference.flow(map: (DocumentSnapshot) -> T?): Flow<T?> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        trySend(if (error != null || snapshot == null || !snapshot.exists()) null else map(snapshot))
    }
    awaitClose { registration.remove() }
}

private fun <T> Query.listFlow(map: (DocumentSnapshot) -> T?): Flow<List<T>> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        trySend(if (error != null || snapshot == null) emptyList() else snapshot.documents.mapNotNull(map))
    }
    awaitClose { registration.remove() }
}

class FirestoreUserRepository @Inject constructor(private val config: CloudConfig) : UserRepository {

    override fun observe(uid: String): Flow<UserDoc?> =
        if (!config.isConfigured) flowOf(null) else userDoc(uid).flow { it.toUserDoc() }

    override suspend fun createIfMissing(user: AuthUser, language: AppLanguage) {
        val doc = userDoc(user.uid)
        if (doc.get().await().exists()) return
        doc.set(
            mapOf(
                "name" to user.name,
                "email" to user.email,
                "photoUrl" to user.photoUrl,
                "plan" to Plan.FREE.name.lowercase(),
                "language" to language.tag,
                "createdAt" to System.currentTimeMillis(),
            ),
        ).await()
    }

    override suspend fun setLanguage(uid: String, language: AppLanguage) {
        userDoc(uid).set(mapOf("language" to language.tag), SetOptions.merge()).await()
    }

    override suspend fun setCreatorProfile(uid: String, profile: CreatorProfile) {
        val map = mapOf(
            "niche" to profile.niche,
            "formats" to profile.formats,
            "language" to profile.language,
            "summary" to profile.summary,
            "source" to profile.source.name,
            "updatedAt" to profile.updatedAt,
        )
        userDoc(uid).set(mapOf("creatorProfile" to map), SetOptions.merge()).await()
    }

    override suspend fun deleteAll(uid: String) {
        val doc = userDoc(uid)
        for (name in listOf("usage", "accounts", "aiHistory")) {
            doc.collection(name).get().await().documents.forEach { it.reference.delete().await() }
        }
        doc.delete().await()
    }
}

@Suppress("UNCHECKED_CAST")
private fun DocumentSnapshot.toUserDoc(): UserDoc {
    val profile = (get("creatorProfile") as? Map<String, Any?>)?.let { map ->
        CreatorProfile(
            niche = map["niche"] as? String ?: return@let null,
            formats = (map["formats"] as? List<String>).orEmpty(),
            language = map["language"] as? String ?: "",
            summary = map["summary"] as? String ?: "",
            source = ProfileSource.entries.firstOrNull { it.name == map["source"] } ?: ProfileSource.DERIVED,
            updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: 0,
        )
    }
    return UserDoc(
        uid = id,
        name = getString("name").orEmpty(),
        email = getString("email").orEmpty(),
        photoUrl = getString("photoUrl"),
        plan = Plan.fromName(getString("plan")),
        language = getString("language").orEmpty(),
        createdAt = getLong("createdAt") ?: 0,
        creatorProfile = profile,
    )
}

class FirestoreUsageRepository @Inject constructor(private val config: CloudConfig) : UsageRepository {

    private fun doc(uid: String, monthKey: String) = userDoc(uid).collection("usage").document(monthKey)

    override fun observe(uid: String, monthKey: String): Flow<Int> =
        if (!config.isConfigured) flowOf(0) else callbackFlow {
            val registration = doc(uid, monthKey).addSnapshotListener { snapshot, _ ->
                trySend((snapshot?.getLong("aiCalls") ?: 0L).toInt())
            }
            awaitClose { registration.remove() }
        }

    override suspend fun increment(uid: String, monthKey: String) {
        doc(uid, monthKey).set(mapOf("aiCalls" to FieldValue.increment(1)), SetOptions.merge()).await()
    }
}

class FirestoreConnectedAccountRepository @Inject constructor(private val config: CloudConfig) : ConnectedAccountRepository {

    private fun collection(uid: String) = userDoc(uid).collection("accounts")

    override fun observe(uid: String): Flow<List<ConnectedAccount>> =
        if (!config.isConfigured) flowOf(emptyList()) else collection(uid).orderBy("connectedAt").listFlow { it.toAccount() }

    override suspend fun save(uid: String, account: ConnectedAccount) {
        collection(uid).document(account.key).set(
            mapOf(
                "platform" to account.platform.name,
                "id" to account.id,
                "title" to account.title,
                "photoUrl" to account.photoUrl,
                "subscribers" to account.subscribers,
                "videoCount" to account.videoCount,
                "link" to account.link,
                "description" to account.description,
                "connectedAt" to account.connectedAt,
            ),
        ).await()
    }

    override suspend fun remove(uid: String, key: String) {
        collection(uid).document(key).delete().await()
    }
}

private fun DocumentSnapshot.toAccount(): ConnectedAccount? {
    val platform = ConnectPlatform.entries.firstOrNull { it.name == getString("platform") } ?: return null
    return ConnectedAccount(
        platform = platform,
        id = getString("id").orEmpty(),
        title = getString("title").orEmpty(),
        photoUrl = getString("photoUrl"),
        subscribers = getLong("subscribers"),
        videoCount = getLong("videoCount"),
        link = getString("link"),
        description = getString("description").orEmpty(),
        connectedAt = getLong("connectedAt") ?: 0,
    )
}

class FirestoreAiHistoryRepository @Inject constructor(private val config: CloudConfig) : AiHistoryRepository {

    private fun collection(uid: String) = userDoc(uid).collection("aiHistory")

    override fun observe(uid: String): Flow<List<AiHistoryEntry>> =
        if (!config.isConfigured) flowOf(emptyList()) else {
            collection(uid).orderBy("createdAt", Query.Direction.DESCENDING).limit(HISTORY_LIMIT).listFlow {
                AiHistoryEntry(it.id, it.getString("prompt").orEmpty(), it.getLong("createdAt") ?: 0)
            }
        }

    override suspend fun add(uid: String, prompt: String, createdAt: Long) {
        collection(uid).add(mapOf("prompt" to prompt, "createdAt" to createdAt)).await()
    }

    override suspend fun clear(uid: String) {
        collection(uid).get().await().documents.forEach { it.reference.delete().await() }
    }

    private companion object {
        const val HISTORY_LIMIT = 50L
    }
}

class FirestoreGoalRepository @Inject constructor(private val config: CloudConfig) : GoalRepository {

    private fun collection(uid: String) = userDoc(uid).collection("goals")

    override fun observe(uid: String): Flow<List<Goal>> =
        if (!config.isConfigured) flowOf(emptyList()) else collection(uid).orderBy("startedAt", Query.Direction.DESCENDING).listFlow { it.toGoal() }

    override suspend fun save(uid: String, goal: Goal): String {
        val doc = if (goal.id.isBlank()) collection(uid).document() else collection(uid).document(goal.id)
        doc.set(
            mapOf(
                "metric" to goal.metric.name,
                "target" to goal.target,
                "startedAt" to goal.startedAt,
                "deadline" to goal.deadline,
                "startValue" to goal.startValue,
                "accountKey" to goal.accountKey,
                "desire" to goal.desire,
                "uploadsPerWeek" to goal.uploadsPerWeek,
                "primary" to goal.primary,
                "reachedAt" to goal.reachedAt,
            ),
        ).await()
        return doc.id
    }

    override suspend fun delete(uid: String, id: String) {
        collection(uid).document(id).delete().await()
    }
}

private fun DocumentSnapshot.toGoal(): Goal? {
    val metric = GoalMetric.entries.firstOrNull { it.name == getString("metric") } ?: return null
    return Goal(
        id = id,
        metric = metric,
        target = getLong("target") ?: return null,
        startedAt = getLong("startedAt") ?: 0,
        deadline = getLong("deadline") ?: 0,
        startValue = getLong("startValue"),
        accountKey = getString("accountKey"),
        desire = getString("desire").orEmpty(),
        uploadsPerWeek = getLong("uploadsPerWeek")?.toInt(),
        primary = getBoolean("primary") ?: false,
        reachedAt = getLong("reachedAt"),
    )
}

class FirestoreCreatorIdeaRepository @Inject constructor(private val config: CloudConfig) : CreatorIdeaRepository {

    private fun collection(uid: String) = userDoc(uid).collection("ideas")

    override fun observe(uid: String): Flow<List<CreatorIdea>> =
        if (!config.isConfigured) flowOf(emptyList()) else collection(uid).orderBy("createdAt", Query.Direction.DESCENDING).limit(IDEA_LIMIT).listFlow { it.toIdea() }

    override suspend fun save(uid: String, idea: CreatorIdea): String {
        val doc = if (idea.id.isBlank()) collection(uid).document() else collection(uid).document(idea.id)
        doc.set(
            mapOf(
                "title" to idea.title,
                "reason" to idea.reason,
                "score" to idea.score,
                "status" to idea.status.name,
                "source" to idea.source,
                "accountKey" to idea.accountKey,
                "day" to idea.day,
                "createdAt" to idea.createdAt,
                "projectId" to idea.projectId,
            ),
        ).await()
        return doc.id
    }

    override suspend fun setStatus(uid: String, id: String, status: IdeaStatus, projectId: Long?) {
        val update = mutableMapOf<String, Any?>("status" to status.name)
        if (projectId != null) update["projectId"] = projectId
        collection(uid).document(id).set(update, SetOptions.merge()).await()
    }

    private companion object {
        const val IDEA_LIMIT = 300L
    }
}

private fun DocumentSnapshot.toIdea(): CreatorIdea? {
    val title = getString("title") ?: return null
    return CreatorIdea(
        id = id,
        title = title,
        reason = getString("reason").orEmpty(),
        score = getLong("score")?.toInt(),
        status = IdeaStatus.entries.firstOrNull { it.name == getString("status") } ?: IdeaStatus.NEW,
        source = getString("source").orEmpty(),
        accountKey = getString("accountKey"),
        day = getString("day").orEmpty(),
        createdAt = getLong("createdAt") ?: 0,
        projectId = getLong("projectId"),
    )
}

class FirestoreUpcomingPostRepository @Inject constructor(private val config: CloudConfig) : UpcomingPostRepository {
    override fun observe(uid: String): Flow<List<UpcomingPost>> =
        if (!config.isConfigured) flowOf(emptyList()) else userDoc(uid).collection("scheduledPosts").orderBy("scheduledAt").listFlow { doc ->
            UpcomingPost(
                id = doc.id,
                accountKey = doc.getString("accountKey"),
                platform = ConnectPlatform.entries.firstOrNull { it.name == doc.getString("platform") } ?: return@listFlow null,
                title = doc.getString("title").orEmpty(),
                scheduledAt = doc.getLong("scheduledAt") ?: return@listFlow null,
            )
        }
}

class FirestoreCacheRepository @Inject constructor(private val config: CloudConfig) : CacheRepository {
    private fun doc(uid: String, key: String) = userDoc(uid).collection("cache").document(key)

    override suspend fun get(uid: String, key: String): CachedText? {
        if (!config.isConfigured) return null
        val snapshot = runCatching { doc(uid, key).get().await() }.getOrNull() ?: return null
        val text = snapshot.getString("text") ?: return null
        return CachedText(text, snapshot.getLong("cachedAt") ?: 0)
    }

    override suspend fun put(uid: String, key: String, text: String, nowMillis: Long) {
        if (!config.isConfigured) return
        runCatching { doc(uid, key).set(mapOf("text" to text, "cachedAt" to nowMillis)).await() }
    }
}
