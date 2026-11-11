package com.creatora.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.creatora.app.ui.navigation.CreatoraBottomBar
import com.creatora.app.ui.navigation.CreatoraNavHost
import com.creatora.app.ui.navigation.Routes
import com.creatora.app.ui.navigation.TopLevelTab
import com.creatora.app.ui.navigation.navigateToTab
import com.creatora.app.ui.theme.CreatoraTheme

/**
 * Root composable: owns the NavController, hosts the navigation graph and shows the bottom tab
 * bar while a tab screen is on top. The app is edge-to-edge; each screen pads for system bars.
 */
@Composable
fun CreatoraApp(
    openPublishProjectId: Long? = null,
    onPublishOpened: () -> Unit = {},
) {
    val navController = rememberNavController()
    LaunchedEffect(openPublishProjectId) {
        openPublishProjectId?.let {
            navController.navigate(Routes.publish(it))
            onPublishOpened()
        }
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val currentTab = TopLevelTab.entries.firstOrNull { tab ->
        destination?.hierarchy?.any { it.route == tab.graphRoute } == true
    }
    val showBottomBar = currentTab != null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CreatoraTheme.colors.background),
    ) {
        CreatoraNavHost(
            navController = navController,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (showBottomBar) {
                        // Reserve the bar + system navigation bar; consumes that inset for screens below.
                        Modifier
                            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                            .padding(bottom = CreatoraTheme.dimens.bottomBarHeight)
                    } else {
                        Modifier
                    },
                ),
        )
        if (showBottomBar) {
            CreatoraBottomBar(
                selectedTab = currentTab,
                onTabSelected = navController::navigateToTab,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
