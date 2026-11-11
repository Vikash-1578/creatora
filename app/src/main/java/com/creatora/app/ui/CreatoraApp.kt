package com.creatora.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.creatora.app.domain.account.UsageLevel
import com.creatora.app.ui.navigation.Routes
import com.creatora.app.ui.usage.UsageBanner
import com.creatora.app.ui.usage.UsageDialog
import com.creatora.app.ui.usage.UsageViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.creatora.app.ui.navigation.CreatoraBottomBar
import com.creatora.app.ui.navigation.CreatoraNavHost
import com.creatora.app.ui.navigation.TopLevelTab
import com.creatora.app.ui.navigation.navigateToTab
import com.creatora.app.ui.theme.CreatoraTheme

/**
 * Root composable: owns the NavController, hosts the navigation graph and shows the bottom tab
 * bar while a tab screen is on top. The app is edge-to-edge; each screen pads for system bars.
 */
@Composable
fun CreatoraApp(usageViewModel: UsageViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val currentTab = TopLevelTab.entries.firstOrNull { tab ->
        destination?.hierarchy?.any { it.route == tab.graphRoute } == true
    }
    val showBottomBar = currentTab != null
    val usage by usageViewModel.status.collectAsStateWithLifecycle()
    val showUsageDialog by usageViewModel.showDialog.collectAsStateWithLifecycle()
    val banner = usage?.takeIf { it.level == UsageLevel.BANNER }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CreatoraTheme.colors.background),
    ) {
        // From 91% to 99% of the monthly AI limit a banner stays at the top, with the current percent.
        if (banner != null) {
            UsageBanner(
                banner,
                onOpenPlan = { navController.navigate(Routes.SETTINGS_BILLING) },
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                // The banner already pads for the status bar, so the screens below must not do it twice.
                .then(if (banner != null) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier),
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

    // At 90% a dialog is shown once a month.
    val dialogStatus = usage
    if (showUsageDialog && dialogStatus != null) {
        UsageDialog(
            dialogStatus,
            onSeePlan = { usageViewModel.dialogSeen(); navController.navigate(Routes.SETTINGS_BILLING) },
            onDismiss = usageViewModel::dialogSeen,
        )
    }
}
