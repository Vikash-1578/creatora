package com.creatora.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.rememberNavController
import com.creatora.app.ui.navigation.CreatoraNavHost

/** Root composable: owns the NavController and hosts the navigation graph. */
@Composable
fun CreatoraApp() {
    val navController = rememberNavController()
    CreatoraNavHost(navController = navController)
}
