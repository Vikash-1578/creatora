package com.creatora.app.domain.model

// Core content models. Plain Kotlin: ids are 0 until saved, times are epoch milliseconds.

data class Project(
    val id: Long = 0,
    val title: String,
    /** Templates are kept apart from projects and copied to start a new one. */
    val isTemplate: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    /** Null until the project was started from the Create tab. */
    val format: VideoFormat? = null,
    val brief: String = "",
    val hook: String = "",
    /** The body of the script. */
    val structure: String = "",
    val callToAction: String = "",
    /** One "word = say it like this" note per line. */
    val pronunciations: String = "",
    /** Characters in the cast. Ids of deleted characters are ignored. */
    val castIds: List<Long> = emptyList(),
    /** Voice preset for the narrator; null means the phone's default voice. */
    val narratorVoiceId: Long? = null,
    /** True: only use files with a licence log entry. False: any imported file. */
    val licensedFootageOnly: Boolean = true,
    /** Index of the wizard step the user is on (see [WizardStep]). */
    val step: Int = 0,
    val isReady: Boolean = false,
    /** file:// URI of the last finished render in app storage, if any. */
    val renderUri: String? = null,
) {
    /** The whole script in reading order. */
    val script: String get() = listOf(hook, structure, callToAction).filter { it.isNotBlank() }.joinToString("\n\n")

    /** Started in the Create flow and not marked ready yet. */
    val isInProgress: Boolean get() = format != null && !isTemplate && !isReady
}

/** The six formats on the Create tab. */
enum class VideoFormat { LONG_VIDEO, SHORTS_REELS, CARTOON_STORY, VIDEO_AD, AVATAR_VIDEO, POST_CAROUSEL }

/** The steps of the Create wizard, in order. The ordinal is the step index stored on the project. */
enum class WizardStep { BRIEF, SCRIPT, CAST, VOICE, FOOTAGE, REVIEW, GENERATE }

data class Idea(
    val id: Long = 0,
    val title: String,
    val notes: String = "",
    /** Set once the idea has been turned into a project. */
    val projectId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

enum class AssetType {
    IMAGE, VIDEO, AUDIO;

    companion object {
        /** The asset type for a MIME type, or null if Creatora can't use the file. */
        fun fromMimeType(mimeType: String): AssetType? = when (mimeType.substringBefore('/')) {
            "image" -> IMAGE
            "video" -> VIDEO
            "audio" -> AUDIO
            else -> null
        }
    }
}

data class Asset(
    val id: Long = 0,
    /** content:// URI of the imported file (persisted permission). */
    val uri: String,
    val type: AssetType,
    val name: String,
    val mimeType: String,
    val projectId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Where an asset came from and under which licence it may be used. */
data class LicenceEntry(
    val id: Long = 0,
    val assetId: Long,
    val source: String,
    val licence: String,
    val author: String = "",
    val url: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

data class VoicePreset(
    val id: Long = 0,
    val name: String,
    /** Android TextToSpeech values: 1.0 is normal. */
    val pitch: Float = 1f,
    val speechRate: Float = 1f,
    val createdAt: Long = System.currentTimeMillis(),
)

data class Character(
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val voicePresetId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

enum class Platform { YOUTUBE, INSTAGRAM }

data class ScheduledPost(
    val id: Long = 0,
    val projectId: Long,
    val platform: Platform,
    val scheduledAt: Long,
    val createdAt: Long = System.currentTimeMillis(),
)

/** One reading of a channel's public statistics. */
data class ChannelSnapshot(
    val id: Long = 0,
    val channelId: String,
    val subscriberCount: Long,
    val viewCount: Long,
    val videoCount: Long,
    val capturedAt: Long = System.currentTimeMillis(),
)

/** The creator's brand: logo, colours (as #RRGGBB) and font files. */
data class BrandKit(
    val logoUri: String? = null,
    val colors: List<String> = emptyList(),
    val fonts: List<FontFile> = emptyList(),
)

data class FontFile(val name: String, val uri: String)

/** A file the user picked, readable by the app. */
data class PickedFile(val uri: String, val name: String, val mimeType: String)
