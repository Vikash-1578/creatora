package com.creatora.app.data.cloud

import com.creatora.app.domain.repository.Competitor
import com.creatora.app.domain.repository.CompetitorRepository
import com.creatora.app.domain.repository.SearchLogRepository
import com.creatora.app.domain.research.CompetitorSnapshot
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

class FirestoreCompetitorRepository @Inject constructor(private val config: CloudConfig) : CompetitorRepository {
    private fun collection(uid: String) = userDoc(uid).collection("competitors")

    override fun observe(uid: String): Flow<List<Competitor>> =
        if (!config.isConfigured) flowOf(emptyList()) else collection(uid).orderBy("addedAt").listFlow { it.toCompetitor() }

    override suspend fun add(uid: String, competitor: Competitor) {
        if (!config.isConfigured) return
        collection(uid).document(competitor.channelId).set(
            mapOf(
                "title" to competitor.title, "photoUrl" to competitor.photoUrl, "subscribers" to competitor.subscribers,
                "views" to competitor.views, "videoCount" to competitor.videoCount, "addedAt" to competitor.addedAt, "source" to competitor.source,
            ),
        ).await()
    }

    override suspend fun remove(uid: String, channelId: String) {
        if (!config.isConfigured) return
        collection(uid).document(channelId).delete().await()
    }

    override suspend fun snapshots(uid: String, channelId: String): List<CompetitorSnapshot> {
        if (!config.isConfigured) return emptyList()
        val result = runCatching { collection(uid).document(channelId).collection("snapshots").orderBy("day", Query.Direction.ASCENDING).get().await() }.getOrNull()
        return result?.documents?.mapNotNull { it.toSnapshot() }.orEmpty()
    }

    override suspend fun addSnapshot(uid: String, channelId: String, snapshot: CompetitorSnapshot) {
        if (!config.isConfigured) return
        collection(uid).document(channelId).collection("snapshots").document(snapshot.day).set(
            mapOf("day" to snapshot.day, "subscribers" to snapshot.subscribers, "views" to snapshot.views, "videos" to snapshot.videos),
        ).await()
    }
}

private fun DocumentSnapshot.toCompetitor(): Competitor? {
    val title = getString("title") ?: return null
    return Competitor(
        channelId = id, title = title, photoUrl = getString("photoUrl"), subscribers = getLong("subscribers"), views = getLong("views"),
        videoCount = getLong("videoCount"), addedAt = getLong("addedAt") ?: 0, source = getString("source") ?: "manual",
    )
}

private fun DocumentSnapshot.toSnapshot(): CompetitorSnapshot? {
    val day = getString("day") ?: return null
    return CompetitorSnapshot(day, getLong("subscribers"), getLong("views") ?: 0, getLong("videos") ?: 0)
}

class FirestoreSearchLogRepository @Inject constructor(private val config: CloudConfig) : SearchLogRepository {
    override suspend fun record(uid: String, query: String, at: Long) {
        if (!config.isConfigured) return
        runCatching { userDoc(uid).collection("searches").add(mapOf("query" to query, "at" to at)).await() }
    }
}
