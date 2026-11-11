package com.creatora.app.data.render

import android.app.Application
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AndroidThumbnailRendererTest {
    private val renderer = AndroidThumbnailRenderer(ApplicationProvider.getApplicationContext<Application>())

    @Test
    fun withoutAPicture_drawsA1280x720PngOnAPlainColour() = runTest {
        val uri = renderer.render(imageUri = null, text = "A long title that has to wrap onto more than one line of the thumbnail", fallbackColor = "#336699")

        val file = File(requireNotNull(uri).removePrefix("file://"))
        assertTrue(file.exists())
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        assertEquals(1280, bounds.outWidth)
        assertEquals(720, bounds.outHeight)
        assertEquals("image/png", bounds.outMimeType)
    }

    @Test
    fun anUnreadablePicture_returnsNull() = runTest {
        assertNull(renderer.render("file:///does/not/exist.jpg", "Title", null))
    }

    @Test
    fun anInvalidFallbackColour_stillRenders() = runTest {
        assertNotNull(renderer.render(null, "Title", "not-a-colour"))
    }
}
