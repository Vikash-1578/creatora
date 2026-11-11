package com.creatora.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                CreatoraApp()
            }
        }
    }
}
