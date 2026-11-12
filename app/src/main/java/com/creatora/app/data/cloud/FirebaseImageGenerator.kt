package com.creatora.app.data.cloud

import android.content.Context
import com.creatora.app.domain.create.ImageGenerator
import com.creatora.app.domain.create.ImageResult
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.PublicPreviewAPI
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Imagen through Firebase AI Logic: the key stays on Google's servers. This model needs the Firebase project on the paid
 * (Blaze) plan; without it the call fails and the app says the picture could not be made. Not verified against the live API.
 */
@OptIn(PublicPreviewAPI::class)
class FirebaseImageGenerator @Inject constructor(
    private val config: CloudConfig,
    @ApplicationContext private val context: Context,
) : ImageGenerator {

    override suspend fun generate(prompt: String): ImageResult {
        if (!config.isConfigured) return ImageResult.Failed
        return try {
            val model = Firebase.ai(backend = GenerativeBackend.googleAI()).imagenModel(MODEL)
            val bytes = model.generateImages(prompt).images.firstOrNull()?.data ?: return ImageResult.Failed
            val file = withContext(Dispatchers.IO) {
                File(context.filesDir, "ai-images").apply { mkdirs() }.let { dir -> File(dir, "ai-${System.currentTimeMillis()}.png").also { it.writeBytes(bytes) } }
            }
            ImageResult.Ok("file://${file.absolutePath}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ImageResult.Failed
        }
    }

    private companion object {
        const val MODEL = "imagen-3.0-generate-002"
    }
}
