package com.creatora.app.domain.repository

import com.creatora.app.domain.research.CompetitorSnapshot
import kotlinx.coroutines.flow.Flow

/** A channel the user follows (`users/{uid}/competitors/{channelId}`). [source] is "auto" (suggested) or "manual" (added by link). */
data class Competitor(
    val channelId: String,
    val title: String,
    val photoUrl: String? = null,
    val subscribers: Long? = null,
    val views: Long? = null,
    val videoCount: Long? = null,
    val addedAt: Long = 0,
    val source: String = "manual",
)

interface CompetitorRepository {
    /** Oldest first. */
    fun observe(uid: String): Flow<List<Competitor>>
    suspend fun add(uid: String, competitor: Competitor)
    suspend fun remove(uid: String, channelId: String)

    /** Oldest first. */
    suspend fun snapshots(uid: String, channelId: String): List<CompetitorSnapshot>

    /** One document per day, so a second write on the same day only replaces the first. */
    suspend fun addSnapshot(uid: String, channelId: String, snapshot: CompetitorSnapshot)
}

/** The user's own keyword searches (`users/{uid}/searches`), kept so rising searches can be worked out later. */
interface SearchLogRepository {
    suspend fun record(uid: String, query: String, at: Long)
}
