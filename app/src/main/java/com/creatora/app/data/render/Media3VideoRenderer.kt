package com.creatora.app.data.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.OverlaySettings
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.creatora.app.domain.render.AspectRatio
import com.creatora.app.domain.render.CaptionCue
import com.creatora.app.domain.render.RenderEvent
import com.creatora.app.domain.render.RenderRequest
import com.creatora.app.domain.render.VideoRenderer
import com.creatora.app.domain.render.captionZone
import com.creatora.app.domain.render.renderFileName
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

/**
 * Renders on the phone with Media3 Transformer: pictures and clips, the narration, optional looping
 * background music at low volume, and burned-in captions, as H.264/AAC MP4.
 */
@OptIn(UnstableApi::class)
class Media3VideoRenderer @Inject constructor(
    @ApplicationContext private val context: Context,
) : VideoRenderer {

    override fun render(request: RenderRequest): Flow<RenderEvent> = callbackFlow {
        val output = File(File(context.filesDir, "renders").apply { mkdirs() }, request.fileName())
        val handler = Handler(Looper.getMainLooper())
        val progress = ProgressHolder()
        var finished = false
        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    finished = true
                    trySend(RenderEvent.Done(Uri.fromFile(output).toString()))
                    close()
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    output.delete()
                    trySend(RenderEvent.Failed(exportException.errorCodeName))
                    close()
                }
            })
            .build()

        val poll = object : Runnable {
            override fun run() {
                if (transformer.getProgress(progress) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    trySend(RenderEvent.Progress(progress.progress))
                }
                handler.postDelayed(this, PROGRESS_INTERVAL_MS)
            }
        }
        try {
            transformer.start(buildComposition(request), output.absolutePath)
            handler.post(poll)
        } catch (e: RuntimeException) {
            output.delete()
            trySend(RenderEvent.Failed(e.javaClass.simpleName))
            close()
        }
        awaitClose {
            handler.removeCallbacks(poll)
            // Cancelling the collector stops the render; only a finished file is kept.
            transformer.cancel()
            if (!finished) output.delete()
        }
    }.flowOn(Dispatchers.Main)

    private fun buildComposition(request: RenderRequest): Composition {
        val fit = Presentation.createForWidthAndHeight(request.ratio.width, request.ratio.height, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP)
        val sceneEffects = Effects(emptyList(), listOf<Effect>(fit))

        val scenes = request.scenes.map { scene ->
            val item = if (scene.isVideo) {
                val clip = MediaItem.ClippingConfiguration.Builder().setEndPositionMs(scene.durationUs / 1_000).build()
                EditedMediaItem.Builder(MediaItem.Builder().setUri(scene.uri).setClippingConfiguration(clip).build())
            } else {
                EditedMediaItem.Builder(MediaItem.fromUri(scene.uri)).setDurationUs(scene.durationUs).setFrameRate(FRAME_RATE)
            }
            item.setRemoveAudio(true).setEffects(sceneEffects).build()
        }
        val narration = request.narration.map {
            EditedMediaItem.Builder(MediaItem.fromUri(it.uri)).setRemoveVideo(true).build()
        }
        val sequences = mutableListOf(
            EditedMediaItemSequence.Builder(*scenes.toTypedArray()).build(),
            EditedMediaItemSequence.Builder(*narration.toTypedArray()).build(),
        )
        request.musicUri?.let { music ->
            val quiet = ChannelMixingAudioProcessor().apply {
                putChannelMixingMatrix(ChannelMixingMatrix.create(1, 2).scaleBy(MUSIC_GAIN))
                putChannelMixingMatrix(ChannelMixingMatrix.create(2, 2).scaleBy(MUSIC_GAIN))
            }
            val item = EditedMediaItem.Builder(MediaItem.fromUri(music))
                .setRemoveVideo(true)
                .setEffects(Effects(listOf(quiet), emptyList()))
                .build()
            sequences += EditedMediaItemSequence.Builder(item).setIsLooping(true).build()
        }

        val builder = Composition.Builder(sequences)
        if (request.cues.isNotEmpty()) {
            builder.setEffects(Effects(emptyList(), listOf<Effect>(OverlayEffect(listOf<TextureOverlay>(CaptionOverlay(request.ratio, request.cues))))))
        }
        return builder.build()
    }

    private fun RenderRequest.fileName(): String =
        renderFileName(title, ratio, SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()))

    private companion object {
        const val FRAME_RATE = 30
        const val PROGRESS_INTERVAL_MS = 500L
        const val MUSIC_GAIN = 0.2f
    }
}

/** Draws the caption for the current time inside the ratio's safe zone, white on a dark rounded box. */
@OptIn(UnstableApi::class)
private class CaptionOverlay(private val ratio: AspectRatio, private val cues: List<CaptionCue>) : BitmapOverlay() {
    private val zone = ratio.captionZone()
    private val zoneHeight = ((zone.bottom - zone.top) * ratio.height).toInt()
    private val blank = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    private val bitmaps = HashMap<CaptionCue, Bitmap>()
    private val settings: OverlaySettings = StaticOverlaySettings.Builder()
        // Anchor the overlay's centre on the middle of the safe zone (frame coordinates run from -1 to 1, top is +1).
        .setBackgroundFrameAnchor(0f, 1f - (zone.top + zone.bottom))
        .setOverlayFrameAnchor(0f, 0f)
        .build()

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings = settings

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val cue = cues.firstOrNull { presentationTimeUs >= it.startUs && presentationTimeUs < it.endUs } ?: return blank
        return bitmaps.getOrPut(cue) { draw(cue.text) }
    }

    private fun draw(text: String): Bitmap {
        val frameWidth = ratio.width
        val textWidth = ((zone.right - zone.left) * frameWidth).toInt()
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = ratio.height * TEXT_HEIGHT_FRACTION
            isFakeBoldText = true
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, textWidth - 2 * PADDING)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(MAX_LINES)
            .build()
        val bitmap = Bitmap.createBitmap(frameWidth, zoneHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val boxHeight = minOf(layout.height + 2 * PADDING, zoneHeight)
        val left = (frameWidth - textWidth) / 2f
        val top = (zoneHeight - boxHeight) / 2f
        canvas.drawRoundRect(
            RectF(left, top, left + textWidth, top + boxHeight),
            PADDING.toFloat(), PADDING.toFloat(),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(BOX_ALPHA, 0, 0, 0) },
        )
        canvas.translate(left + PADDING, top + PADDING)
        layout.draw(canvas)
        return bitmap
    }

    private companion object {
        const val TEXT_HEIGHT_FRACTION = 0.032f
        const val PADDING = 24
        const val MAX_LINES = 4
        const val BOX_ALPHA = 170
    }
}
