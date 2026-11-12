package com.creatora.app.ui.home

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.creatora.app.ui.theme.CreatoraTheme
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Draws Home with sample numbers in light and dark and saves PNGs to `app/build/screenshots/`. CI uploads them as the
 * "screenshots" artifact so a reviewer can look at the layout without building the app. Sample data only.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h1900dp-xxhdpi")
class HomeScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun shoot(name: String, dark: Boolean) {
        compose.setContent { CreatoraTheme(darkTheme = dark) { HomeContent(sampleHomeModel(), HomeActions()) } }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val file = File("build/screenshots/$name.png").also { it.parentFile?.mkdirs() }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(file.length() > 0)
    }

    @Test fun homeLight() = shoot("home-light", dark = false)

    @Test fun homeDark() = shoot("home-dark", dark = true)
}
