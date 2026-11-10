package com.creatora.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.creatora.app.ui.themepreview.ThemePreviewScreen

/** All navigation destinations in the app. Add new screens here. */
object Routes {
    const val THEME_PREVIEW = "theme_preview"
}

@Composable
fun CreatoraNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.THEME_PREVIEW,
        modifier = modifier,
    ) {
        composable(Routes.THEME_PREVIEW) {
            ThemePreviewScreen()
        }
    }
}
