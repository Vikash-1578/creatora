package com.creatora.app.data.media

import android.content.Context
import android.speech.tts.TextToSpeech
import com.creatora.app.domain.repository.SpeechPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

@Singleton
class AndroidSpeechPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
) : SpeechPlayer {
    private var engine: TextToSpeech? = null

    override suspend fun speak(text: String, pitch: Float, speechRate: Float): Boolean {
        val tts = engine ?: start() ?: return false
        tts.setPitch(pitch)
        tts.setSpeechRate(speechRate)
        return tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "preview") == TextToSpeech.SUCCESS
    }

    override fun stop() {
        engine?.stop()
    }

    override fun release() {
        engine?.shutdown()
        engine = null
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
