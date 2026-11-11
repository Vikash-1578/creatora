package com.creatora.app.data.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.creatora.app.domain.repository.ThumbnailRenderer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidThumbnailRenderer @Inject constructor(
    @ApplicationContext private val context: Context,
) : ThumbnailRenderer {

    override suspend fun render(imageUri: String?, text: String, fallbackColor: String?): String? = withContext(Dispatchers.IO) {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val source = imageUri?.let { decode(it) ?: return@withContext null }
        if (source != null) {
            drawCentreCrop(canvas, source)
            source.recycle()
        } else {
            canvas.drawColor(fallbackColor?.let { runCatching { Color.parseColor(it) }.getOrNull() } ?: Color.DKGRAY)
        }
        drawText(canvas, text.trim())

        val dir = File(context.filesDir, "thumbnails").apply { mkdirs() }
        val file = File(dir, "thumbnail-${UUID.randomUUID()}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        Uri.fromFile(file).toString()
    }

    private fun decode(uri: String): Bitmap? = runCatching {
        context.contentResolver.openInputStream(Uri.parse(uri))!!.use { BitmapFactory.decodeStream(it) }
    }.getOrNull()

    private fun drawCentreCrop(canvas: Canvas, source: Bitmap) {
        val scale = maxOf(WIDTH / source.width.toFloat(), HEIGHT / source.height.toFloat())
        val width = source.width * scale
        val height = source.height * scale
        canvas.save()
        canvas.translate((WIDTH - width) / 2f, (HEIGHT - height) / 2f)
        canvas.scale(scale, scale)
        canvas.drawBitmap(source, 0f, 0f, null)
        canvas.restore()
    }

    /** White bold text over a dark gradient at the bottom, so it stays readable on any picture. */
    private fun drawText(canvas: Canvas, text: String) {
        if (text.isEmpty()) return
        val gradient = Paint().apply {
            shader = LinearGradient(0f, HEIGHT * 0.4f, 0f, HEIGHT.toFloat(), Color.TRANSPARENT, Color.argb(200, 0, 0, 0), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, HEIGHT * 0.4f, WIDTH.toFloat(), HEIGHT.toFloat(), gradient)
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = TEXT_SIZE
            isFakeBoldText = true
            setShadowLayer(8f, 0f, 2f, Color.BLACK)
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, WIDTH - 2 * MARGIN)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(MAX_LINES)
            .build()
        canvas.translate(MARGIN.toFloat(), HEIGHT - MARGIN - layout.height.toFloat())
        layout.draw(canvas)
    }

    private companion object {
        const val WIDTH = 1280
        const val HEIGHT = 720
        const val MARGIN = 56
        const val TEXT_SIZE = 84f
        const val MAX_LINES = 3
    }
}
