package com.creatora.app.data.render

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.creatora.app.domain.render.NarrationClip
import com.creatora.app.domain.repository.MediaProbe
import com.creatora.app.domain.repository.MediaSaver
import com.creatora.app.domain.repository.Narrator
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Android TextToSpeech writing each sentence to a WAV file in the cache. */
class AndroidNarrator @Inject constructor(
    @ApplicationContext private val context: Context,
) : Narrator {
    private var engine: TextToSpeech? = null
    private val dir = File(context.cacheDir, "narration")

    override suspend fun synthesize(text: String, pitch: Float, speechRate: Float): NarrationClip? {
        val tts = engine ?: start() ?: return null
        tts.setPitch(pitch)
        tts.setSpeechRate(speechRate)
        dir.mkdirs()
        val file = File(dir, "${UUID.randomUUID()}.wav")
        val written = suspendCancellableCoroutine { continuation ->
            val id = file.name
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) {
                    if (continuation.isActive) continuation.resume(true)
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (continuation.isActive) continuation.resume(false)
                }
            })
            if (tts.synthesizeToFile(text, null, file, id) != TextToSpeech.SUCCESS && continuation.isActive) continuation.resume(false)
        }
        if (!written) return null
        val durationMs = withContext(Dispatchers.IO) { durationOf { it.setDataSource(file.absolutePath) } } ?: return null
        return NarrationClip(Uri.fromFile(file).toString(), durationMs * 1_000)
    }

    override fun release() {
        engine?.shutdown()
        engine = null
        dir.deleteRecursively()
    }

    private suspend fun start(): TextToSpeech? = suspendCancellableCoroutine { continuation ->
        var created: TextToSpeech? = null
        created = TextToSpeech(context) { status ->
            val ready = status == TextToSpeech.SUCCESS
            if (ready) engine = created else created?.shutdown()
            if (continuation.isActive) continuation.resume(if (ready) created else null)
        }
        continuation.invokeOnCancellation { if (engine == null) created?.shutdown() }
    }
}

class AndroidMediaProbe @Inject constructor(
    @ApplicationContext private val context: Context,
) : MediaProbe {
    override suspend fun videoDurationMs(uri: String): Long? = withContext(Dispatchers.IO) {
        durationOf { it.setDataSource(context, Uri.parse(uri)) }
    }
}

/** Reads a media file's length in milliseconds, or null if it can't be read. */
private fun durationOf(open: (MediaMetadataRetriever) -> Unit): Long? {
    val retriever = MediaMetadataRetriever()
    return try {
        open(retriever)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.takeIf { it > 0 }
    } catch (e: RuntimeException) {
        null
    } finally {
        retriever.release()
    }
}

class AndroidMediaSaver @Inject constructor(
    @ApplicationContext private val context: Context,
) : MediaSaver {

    override suspend fun saveToGallery(fileUri: String, displayName: String, mimeType: String): Boolean = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext false
        val isVideo = mimeType.startsWith("video/")
        val collection = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val folder = if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/Creatora")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val target = resolver.insert(collection, values) ?: return@withContext false
        val copied = copy(fileUri, target.toString())
        if (copied) {
            resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } else {
            resolver.delete(target, null, null)
        }
        copied
    }

    override suspend fun copyTo(fileUri: String, destinationUri: String): Boolean = withContext(Dispatchers.IO) { copy(fileUri, destinationUri) }

    private fun copy(fromUri: String, toUri: String): Boolean = runCatching {
        val resolver = context.contentResolver
        resolver.openInputStream(Uri.parse(fromUri))!!.use { input ->
            resolver.openOutputStream(Uri.parse(toUri))!!.use { output -> input.copyTo(output) }
        }
    }.isSuccess
}
