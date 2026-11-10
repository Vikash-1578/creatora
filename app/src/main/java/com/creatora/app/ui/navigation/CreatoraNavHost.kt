package com.creatora.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.creatora.app.ui.home.HomeScreen

/** All navigation destinations in the app. Add new screens here. */
object Routes {
    const val HOME = "home"
}

@Composable
fun CreatoraNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier,
    ) {
        composable(Routes.HOME) {
            HomeScreen()
        }
    }
}
