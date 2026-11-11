package com.creatora.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.creatora.app.domain.grow.AbTest
import com.creatora.app.domain.grow.CommentDraft
import com.creatora.app.domain.grow.DraftStatus
import com.creatora.app.domain.grow.TestKind
import com.creatora.app.domain.grow.TestVariant
import com.creatora.app.domain.model.Asset
import com.creatora.app.domain.model.AssetType
import com.creatora.app.domain.model.ChannelSnapshot
import com.creatora.app.domain.model.Character
import com.creatora.app.domain.model.Idea
import com.creatora.app.domain.model.LicenceEntry
import com.creatora.app.domain.model.Platform
import com.creatora.app.domain.model.Project
import com.creatora.app.domain.model.VideoFormat
import com.creatora.app.domain.model.ScheduledPost
import com.creatora.app.domain.model.VoicePreset
import com.creatora.app.domain.research.Competitor
import com.creatora.app.domain.research.SwipeItem

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
    @ColumnInfo(defaultValue = "0") val captionsIncluded: Boolean,
    val publishTitle: String?,
    val publishDescription: String?,
    val thumbnailUri: String?,
    val aiContent: Boolean?,
    val madeForKids: Boolean?,
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

@Entity(tableName = "competitors", indices = [Index("channelId", unique = true)])
data class CompetitorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val channelId: String,
    val title: String,
    val subscribers: Long?,
    val views: Long,
    val videos: Long,
    val updatedAt: Long,
)

@Entity(tableName = "swipe_items")
data class SwipeItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val title: String,
    val url: String,
    val channelTitle: String,
    val note: String,
    val createdAt: Long,
)

@Entity(tableName = "comment_drafts")
data class CommentDraftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val comment: String,
    val reply: String,
    val status: DraftStatus,
    val createdAt: Long,
)

@Entity(tableName = "ab_tests")
data class AbTestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val name: String,
    val kind: TestKind,
    val variantA: String,
    val variantB: String,
    val notes: String,
    val winner: TestVariant?,
    val startedAt: Long,
    val endedAt: Long?,
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
    narratorVoiceId, licensedFootageOnly, step, isReady, renderUri, captionsIncluded,
    publishTitle.orEmpty(), publishDescription.orEmpty(), thumbnailUri, aiContent, madeForKids,
)

fun Project.toEntity() = ProjectEntity(
    id, title, isTemplate, createdAt, updatedAt, format,
    brief, hook, structure, callToAction, pronunciations, castIds.joinToString(","),
    narratorVoiceId, licensedFootageOnly, step, isReady, renderUri, captionsIncluded,
    publishTitle, publishDescription, thumbnailUri, aiContent, madeForKids,
)

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

fun CompetitorEntity.toModel() = Competitor(id, channelId, title, subscribers, views, videos, updatedAt)
fun Competitor.toEntity() = CompetitorEntity(id, channelId, title, subscribers, views, videos, updatedAt)

fun SwipeItemEntity.toModel() = SwipeItem(id, title, url, channelTitle, note, createdAt)
fun SwipeItem.toEntity() = SwipeItemEntity(id, title, url, channelTitle, note, createdAt)

fun CommentDraftEntity.toModel() = CommentDraft(id, comment, reply, status, createdAt)
fun CommentDraft.toEntity() = CommentDraftEntity(id, comment, reply, status, createdAt)

fun AbTestEntity.toModel() = AbTest(id, name, kind, variantA, variantB, notes, winner, startedAt, endedAt)
fun AbTest.toEntity() = AbTestEntity(id, name, kind, variantA, variantB, notes, winner, startedAt, endedAt)

fun ChannelSnapshotEntity.toModel() =
    ChannelSnapshot(id, channelId, subscriberCount, viewCount, videoCount, capturedAt)
fun ChannelSnapshot.toEntity() =
    ChannelSnapshotEntity(id, channelId, subscriberCount, viewCount, videoCount, capturedAt)
