package com.creatora.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.creatora.app.domain.model.Asset
import com.creatora.app.domain.model.AssetType
import com.creatora.app.domain.model.ChannelSnapshot
import com.creatora.app.domain.model.Character
import com.creatora.app.domain.model.Idea
import com.creatora.app.domain.model.LicenceEntry
import com.creatora.app.domain.model.Platform
import com.creatora.app.domain.create.decodeStates
import com.creatora.app.domain.create.encodeStates
import com.creatora.app.domain.model.CreateMode
import com.creatora.app.domain.model.Project
import com.creatora.app.domain.model.StartSource
import com.creatora.app.domain.model.VideoFormat
import com.creatora.app.domain.model.ScheduledPost
import com.creatora.app.domain.model.VoicePreset

// Room tables. Each entity mirrors its domain model; enums are stored by name.

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val title: String,
    @ColumnInfo(defaultValue = "0") val isTemplate: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    // Create wizard. Text columns are nullable so Room can add them to existing rows without defaults.
    val format: VideoFormat?,
    val brief: String?,
    val hook: String?,
    val structure: String?,
    val callToAction: String?,
    val pronunciations: String?,
    /** Character ids, comma-separated. */
    val castIds: String?,
    val narratorVoiceId: Long?,
    @ColumnInfo(defaultValue = "1") val licensedFootageOnly: Boolean,
    @ColumnInfo(defaultValue = "0") val step: Int,
    @ColumnInfo(defaultValue = "0") val isReady: Boolean,
    val renderUri: String?,
    // Phase D. Text columns are nullable and the others have defaults, so Room can add them to existing rows.
    val mode: String?,
    val startedFrom: String?,
    val sourceLink: String?,
    val concept: String?,
    val angle: String?,
    val tone: String?,
    val audience: String?,
    @ColumnInfo(defaultValue = "0") val targetSeconds: Int,
    val stepStates: String?,
    @ColumnInfo(defaultValue = "0") val counted: Boolean,
    // Phase E. Everything nullable so Room can add the columns to existing rows.
    val publishTitle: String?,
    val publishDescription: String?,
    /** Hashtags and tags, newline-separated. */
    val publishHashtags: String?,
    val publishTags: String?,
    val thumbnailUri: String?,
    val aiContent: Boolean?,
    val madeForKids: Boolean?,
    /** Captions by platform as JSON. */
    val captions: String?,
)

@Entity(
    tableName = "ideas",
    foreignKeys = [ForeignKey(ProjectEntity::class, ["id"], ["projectId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("projectId")],
)
data class IdeaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val title: String,
    val notes: String,
    val projectId: Long?,
    val createdAt: Long,
)

@Entity(
    tableName = "assets",
    foreignKeys = [ForeignKey(ProjectEntity::class, ["id"], ["projectId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("projectId")],
)
data class AssetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val uri: String,
    val type: AssetType,
    val name: String,
    val mimeType: String,
    val projectId: Long?,
    val createdAt: Long,
)

@Entity(
    tableName = "licence_entries",
    foreignKeys = [ForeignKey(AssetEntity::class, ["id"], ["assetId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("assetId")],
)
data class LicenceEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val assetId: Long,
    val source: String,
    val licence: String,
    val author: String,
    val url: String,
    val createdAt: Long,
)

@Entity(tableName = "voice_presets")
data class VoicePresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val name: String,
    val pitch: Float,
    val speechRate: Float,
    val createdAt: Long,
)

@Entity(
    tableName = "characters",
    foreignKeys = [ForeignKey(VoicePresetEntity::class, ["id"], ["voicePresetId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("voicePresetId")],
)
data class CharacterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val name: String,
    val description: String,
    val voicePresetId: Long?,
    val createdAt: Long,
)

@Entity(
    tableName = "scheduled_posts",
    foreignKeys = [ForeignKey(ProjectEntity::class, ["id"], ["projectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("projectId")],
)
data class ScheduledPostEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val projectId: Long,
    val platform: Platform,
    val scheduledAt: Long,
    val createdAt: Long,
)

@Entity(tableName = "channel_snapshots", indices = [Index("channelId", "capturedAt")])
data class ChannelSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val channelId: String,
    val subscriberCount: Long,
    val viewCount: Long,
    val videoCount: Long,
    val capturedAt: Long,
)

fun ProjectEntity.toModel() = Project(
    id, title, isTemplate, createdAt, updatedAt, format,
    brief.orEmpty(), hook.orEmpty(), structure.orEmpty(), callToAction.orEmpty(), pronunciations.orEmpty(),
    castIds.orEmpty().split(',').mapNotNull { it.toLongOrNull() },
    narratorVoiceId, licensedFootageOnly, step, isReady, renderUri,
    CreateMode.entries.firstOrNull { it.name == mode } ?: CreateMode.STEP_BY_STEP,
    StartSource.entries.firstOrNull { it.name == startedFrom },
    sourceLink.orEmpty(), concept.orEmpty(), angle.orEmpty(), tone.orEmpty(), audience.orEmpty(), targetSeconds,
    decodeStates(stepStates), counted,
    publishTitle.orEmpty(), publishDescription.orEmpty(), publishHashtags.orEmpty().lines().filter { it.isNotBlank() },
    publishTags.orEmpty().lines().filter { it.isNotBlank() }, thumbnailUri, aiContent, madeForKids, decodeCaptions(captions),
)

fun Project.toEntity() = ProjectEntity(
    id, title, isTemplate, createdAt, updatedAt, format,
    brief, hook, structure, callToAction, pronunciations, castIds.joinToString(","),
    narratorVoiceId, licensedFootageOnly, step, isReady, renderUri,
    mode.name, startedFrom?.name, sourceLink, concept, angle, tone, audience, targetSeconds, encodeStates(stepStates), counted,
    publishTitle, publishDescription, publishHashtags.joinToString("\n"), publishTags.joinToString("\n"), thumbnailUri, aiContent, madeForKids,
    encodeCaptions(captions),
)

private fun encodeCaptions(captions: Map<String, String>): String? =
    if (captions.isEmpty()) null else org.json.JSONObject(captions as Map<*, *>).toString()

private fun decodeCaptions(json: String?): Map<String, String> = runCatching {
    val o = org.json.JSONObject(json ?: return emptyMap())
    o.keys().asSequence().associateWith { o.getString(it) }
}.getOrDefault(emptyMap())

fun IdeaEntity.toModel() = Idea(id, title, notes, projectId, createdAt)
fun Idea.toEntity() = IdeaEntity(id, title, notes, projectId, createdAt)

fun AssetEntity.toModel() = Asset(id, uri, type, name, mimeType, projectId, createdAt)
fun Asset.toEntity() = AssetEntity(id, uri, type, name, mimeType, projectId, createdAt)

fun LicenceEntryEntity.toModel() = LicenceEntry(id, assetId, source, licence, author, url, createdAt)
fun LicenceEntry.toEntity() = LicenceEntryEntity(id, assetId, source, licence, author, url, createdAt)

fun VoicePresetEntity.toModel() = VoicePreset(id, name, pitch, speechRate, createdAt)
fun VoicePreset.toEntity() = VoicePresetEntity(id, name, pitch, speechRate, createdAt)

fun CharacterEntity.toModel() = Character(id, name, description, voicePresetId, createdAt)
fun Character.toEntity() = CharacterEntity(id, name, description, voicePresetId, createdAt)

fun ScheduledPostEntity.toModel() = ScheduledPost(id, projectId, platform, scheduledAt, createdAt)
fun ScheduledPost.toEntity() = ScheduledPostEntity(id, projectId, platform, scheduledAt, createdAt)

fun ChannelSnapshotEntity.toModel() =
    ChannelSnapshot(id, channelId, subscriberCount, viewCount, videoCount, capturedAt)
fun ChannelSnapshot.toEntity() =
    ChannelSnapshotEntity(id, channelId, subscriberCount, viewCount, videoCount, capturedAt)
