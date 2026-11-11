package com.creatora.app.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.creatora.app.R
import com.creatora.app.ui.theme.CreatoraTheme

/** Shown for a moment while the app reads the saved session: signed in goes to Home, otherwise to the language picker. */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(CreatoraTheme.colors.background), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.app_name), style = CreatoraTheme.typography.screenTitle, color = CreatoraTheme.colors.accentText)
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun SplashLightPreview() {
    CreatoraTheme(darkTheme = false) { SplashScreen() }
}

@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SplashDarkPreview() {
    CreatoraTheme(darkTheme = true) { SplashScreen() }
}
