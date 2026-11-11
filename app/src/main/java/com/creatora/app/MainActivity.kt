package com.creatora.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.creatora.app.domain.model.ThemeMode
import com.creatora.app.ui.AppViewModel
import com.creatora.app.ui.CreatoraApp
import com.creatora.app.ui.theme.CreatoraTheme
import dagger.hilt.android.AndroidEntryPoint

/** The single activity of the app. All screens are Compose destinations inside [CreatoraApp]. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    /** Set when the user taps a post reminder; the app then opens that project's Publish screen. */
    private var publishProjectId by mutableStateOf<Long?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        publishProjectId = intent.publishProjectId()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        publishProjectId = intent?.publishProjectId() // the intent is not available yet when the property is created
        enableEdgeToEdge()
        setContent {
            val appViewModel: AppViewModel = hiltViewModel()
            val themeMode by appViewModel.themeMode.collectAsStateWithLifecycle()
            // Wait for the saved setting so the wrong theme is never shown; the window
            // background (system light/dark) is visible for this brief moment.
            val mode = themeMode ?: return@setContent

            val darkTheme = when (mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            // Keep status/navigation bar icons readable for the chosen theme.
            DisposableEffect(darkTheme) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }

            CreatoraTheme(darkTheme = darkTheme) {
                CreatoraApp(
                    openPublishProjectId = publishProjectId,
                    onPublishOpened = { publishProjectId = null },
                )
            }
        }
    }

    companion object {
        const val EXTRA_PUBLISH_PROJECT_ID = "publish_project_id"
    }
}

private fun Intent.publishProjectId(): Long? =
    getLongExtra(MainActivity.EXTRA_PUBLISH_PROJECT_ID, 0L).takeIf { it > 0 }
