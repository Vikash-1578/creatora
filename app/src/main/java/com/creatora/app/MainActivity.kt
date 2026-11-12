package com.creatora.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.creatora.app.core.localized
import android.content.Intent
import com.creatora.app.data.cloud.ActivityBridge
import com.creatora.app.data.platform.OAuthBridge
import com.creatora.app.domain.model.ThemeMode
import com.creatora.app.ui.AppViewModel
import com.creatora.app.ui.CreatoraApp
import com.creatora.app.ui.SplashScreen
import com.creatora.app.ui.StartState
import com.creatora.app.ui.onboarding.OnboardingScreen
import com.creatora.app.ui.theme.CreatoraTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** The single activity of the app. All screens are Compose destinations inside [CreatoraApp]. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var bridge: ActivityBridge
    @Inject lateinit var oauth: OAuthBridge

    // Shows Google's permission screen on request of the data layer and hands the result back.
    private val consent = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        bridge.onConsentResult(result.data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        bridge.attach(this) { consent.launch(it) }
        setContent {
            val appViewModel: AppViewModel = hiltViewModel()
            val session by appViewModel.session.collectAsStateWithLifecycle()

            // Splash: shown while the saved settings and sign-in are read.
            val current = session
            if (current == null) {
                CreatoraTheme(darkTheme = isSystemInDarkTheme()) { SplashScreen() }
                return@setContent
            }

            val darkTheme = when (current.themeMode) {
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

            // The in-app language replaces the device's: every stringResource below reads these resources.
            val base = LocalContext.current
            val localized = remember(base, current.language) { base.localized(current.language) }
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalConfiguration provides localized.resources.configuration,
            ) {
                CreatoraTheme(darkTheme = darkTheme) {
                    when (current.start) {
                        StartState.ONBOARDING -> OnboardingScreen()
                        StartState.HOME -> CreatoraApp()
                    }
                }
            }
        }
    }

    // The sign-in redirect page opens creatora://oauth?...; the platform sign-in that is waiting reads it.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.data?.scheme == "creatora") oauth.deliver(intent.data)
    }

    override fun onResume() {
        super.onResume()
        oauth.onResumed()
    }

    override fun onDestroy() {
        bridge.detach(this)
        super.onDestroy()
    }
}
