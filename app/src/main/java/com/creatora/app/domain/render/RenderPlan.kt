package com.creatora.app.domain.render

import kotlinx.coroutines.flow.Flow

/** Output sizes. Each is 1080 pixels on its short side. */
enum class AspectRatio(val width: Int, val height: Int) {
    PORTRAIT(1080, 1920),
    LANDSCAPE(1920, 1080),
    SQUARE(1080, 1080),
}

/** A region of the frame as fractions of its width and height (0 is left/top, 1 is right/bottom). */
data class Zone(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * Where captions go. Vertical video keeps clear of the platform buttons at the bottom and right edge;
 * every ratio keeps a margin from the sides.
 */
fun AspectRatio.captionZone(): Zone = when (this) {
    AspectRatio.PORTRAIT -> Zone(0.08f, 0.58f, 0.86f, 0.76f)
    AspectRatio.LANDSCAPE -> Zone(0.10f, 0.74f, 0.90f, 0.90f)
    AspectRatio.SQUARE -> Zone(0.08f, 0.66f, 0.92f, 0.86f)
}

/** One caption, shown from [startUs] up to [endUs]. */
data class CaptionCue(val startUs: Long, val endUs: Long, val text: String)

/** A narration audio file made by text-to-speech. */
data class NarrationClip(val uri: String, val durationUs: Long)

/** A picture or video the user imported. [durationMs] is only known (and needed) for video. */
data class SceneSource(val uri: String, val isVideo: Boolean, val durationMs: Long?)

/** One shot in the video: a picture, or the start of a video, for [durationUs]. */
data class Scene(val uri: String, val isVideo: Boolean, val durationUs: Long)

data class RenderRequest(
    val title: String,
    val ratio: AspectRatio,
    val scenes: List<Scene>,
    val narration: List<NarrationClip>,
    /** Empty when captions are turned off. */
    val cues: List<CaptionCue>,
    val musicUri: String?,
)

sealed interface RenderEvent {
    data class Progress(val percent: Int) : RenderEvent
    data class Done(val uri: String) : RenderEvent
    data class Failed(val message: String) : RenderEvent
}

fun interface VideoRenderer {
    /** Renders [request] to an MP4 in app storage. Cancelling the collector cancels the render. */
    fun render(request: RenderRequest): Flow<RenderEvent>
}

private val SENTENCE_BREAK = Regex("(?<=[.!?…।])\\s+|\\n+")

/** Splits a script into sentences, one caption and one narration clip each. */
fun splitSentences(script: String): List<String> =
    script.split(SENTENCE_BREAK).map { it.trim() }.filter { it.isNotEmpty() }

/** Caption cues for sentences that are spoken one after the other. */
fun buildCues(sentences: List<String>, clips: List<NarrationClip>): List<CaptionCue> {
    var start = 0L
    return sentences.zip(clips).map { (text, clip) ->
        CaptionCue(start, start + clip.durationUs, text).also { start += clip.durationUs }
    }
}

private const val TARGET_SCENE_US = 4_000_000L
private const val MIN_SCENE_US = 1_000_000L
private const val MIN_VIDEO_MS = 500L
private const val MAX_SCENES = 2_000

/**
 * Spreads [sources] over [totalUs] in order, about four seconds each, and starts again from the first
 * when they run out. A video shorter than its slot is shown whole and the next source takes over, so the
 * scenes always add up to exactly [totalUs]. Videos with no usable length are skipped.
 */
fun planScenes(sources: List<SceneSource>, totalUs: Long): List<Scene> {
    val usable = sources.filter { !it.isVideo || (it.durationMs ?: 0L) >= MIN_VIDEO_MS }
    if (usable.isEmpty() || totalUs <= 0) return emptyList()
    val scenes = mutableListOf<Scene>()
    var remaining = totalUs
    var index = 0
    while (remaining > 0 && scenes.size < MAX_SCENES) {
        val source = usable[index % usable.size]
        // Don't leave a tiny last scene: the final slot takes everything that is left.
        val slot = if (remaining - TARGET_SCENE_US < MIN_SCENE_US) remaining else TARGET_SCENE_US
        val duration = if (source.isVideo) minOf(slot, source.durationMs!! * 1_000) else slot
        scenes += Scene(source.uri, source.isVideo, duration)
        remaining -= duration
        index++
    }
    // Only reachable when MAX_SCENES is hit by many very short videos: stretch the last scene to fill.
    if (remaining > 0 && scenes.isNotEmpty()) {
        val last = scenes.removeAt(scenes.lastIndex)
        scenes += last.copy(durationUs = last.durationUs + remaining)
    }
    return scenes
}

/** A safe file name for a render, like "My-video-9x16-20261111-1230.mp4". */
fun renderFileName(title: String, ratio: AspectRatio, timestampLabel: String): String {
    val base = title.trim().replace(Regex("[^\\p{L}\\p{M}\\p{N}]+"), "-").trim('-').take(40).ifEmpty { "creatora" }
    val shape = when (ratio) {
        AspectRatio.PORTRAIT -> "9x16"
        AspectRatio.LANDSCAPE -> "16x9"
        AspectRatio.SQUARE -> "1x1"
    }
    return "$base-$shape-$timestampLabel.mp4"
}
