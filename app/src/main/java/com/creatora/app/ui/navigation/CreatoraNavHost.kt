package com.creatora.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.creatora.app.ui.ai.AiProvidersScreen
import com.creatora.app.ui.brandkit.BrandKitScreen
import com.creatora.app.ui.coach.CoachScreen
import com.creatora.app.ui.create.CreateScreen
import com.creatora.app.ui.create.WizardScreen
import com.creatora.app.ui.create.WizardViewModel
import com.creatora.app.ui.earn.EarnScreen
import com.creatora.app.ui.grow.GrowScreen
import com.creatora.app.ui.home.HomeScreen
import com.creatora.app.ui.library.LibraryScreen
import com.creatora.app.ui.licences.LicencesScreen
import com.creatora.app.ui.licences.LicencesViewModel
import com.creatora.app.ui.profile.ProfileScreen
import com.creatora.app.ui.research.ResearchScreen
import com.creatora.app.ui.settings.SettingsScreen

/** All screen routes. Tab screens live inside their tab's graph (see [TopLevelTab]). */
object Routes {
    const val HOME = "home"
    const val RESEARCH = "research"
    const val CREATE = "create"
    const val GROW = "grow"
    const val LIBRARY = "library"

    // Screens shown without the bottom bar
    const val PROFILE = "profile"
    const val EARN = "earn"
    const val SETTINGS = "settings"
    const val BRAND_KIT = "brand_kit"
    const val WIZARD = "wizard/{projectId}"
    const val AI_PROVIDERS = "ai_providers"
    const val COACH = "coach"
    const val LICENCES = "licences?assetId={assetId}"

    fun wizard(projectId: Long) = "wizard/$projectId"

    fun licences(assetId: Long?) = "licences?assetId=${assetId ?: LicencesViewModel.ALL}"
}

/**
 * Switches to [tab], saving the current tab's back stack and restoring [tab]'s.
 * Because everything pops up to Home's graph, Back from any other tab returns to Home,
 * and Back on Home exits the app.
 */
fun NavHostController.navigateToTab(tab: TopLevelTab) {
    navigate(tab.graphRoute) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun CreatoraNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = TopLevelTab.HOME.graphRoute,
        modifier = modifier,
    ) {
        navigation(route = TopLevelTab.HOME.graphRoute, startDestination = Routes.HOME) {
            composable(Routes.HOME) {
                HomeScreen(
                    onProfileClick = dropUnlessResumed { navController.navigate(Routes.PROFILE) },
                    onContinue = { id -> navController.navigate(Routes.wizard(id)) },
                    onOpenCoach = dropUnlessResumed { navController.navigate(Routes.COACH) },
                )
            }
        }
        navigation(route = TopLevelTab.RESEARCH.graphRoute, startDestination = Routes.RESEARCH) {
            composable(Routes.RESEARCH) { ResearchScreen() }
        }
        navigation(route = TopLevelTab.CREATE.graphRoute, startDestination = Routes.CREATE) {
            composable(Routes.CREATE) {
                CreateScreen(
                    onProjectStarted = { id -> navController.navigate(Routes.wizard(id)) },
                    onOpenAiProviders = dropUnlessResumed { navController.navigate(Routes.AI_PROVIDERS) },
                )
            }
        }
        navigation(route = TopLevelTab.GROW.graphRoute, startDestination = Routes.GROW) {
            composable(Routes.GROW) { GrowScreen() }
        }
        navigation(route = TopLevelTab.LIBRARY.graphRoute, startDestination = Routes.LIBRARY) {
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    onOpenBrandKit = dropUnlessResumed { navController.navigate(Routes.BRAND_KIT) },
                    onOpenLicences = { assetId -> navController.navigate(Routes.licences(assetId)) },
                    onOpenProject = { id -> navController.navigate(Routes.wizard(id)) },
                )
            }
        }

        composable(
            route = Routes.WIZARD,
            arguments = listOf(navArgument(WizardViewModel.ARG_PROJECT_ID) { type = NavType.LongType }),
        ) {
            WizardScreen(
                onClose = { navController.popBackStack() },
                onOpenLibrary = { navController.navigateToTab(TopLevelTab.LIBRARY) },
                onOpenAiProviders = dropUnlessResumed { navController.navigate(Routes.AI_PROVIDERS) },
            )
        }
        composable(Routes.BRAND_KIT) {
            BrandKitScreen(onBack = dropUnlessResumed { navController.popBackStack() })
        }
        composable(
            route = Routes.LICENCES,
            arguments = listOf(navArgument(LicencesViewModel.ARG_ASSET_ID) { type = NavType.LongType; defaultValue = LicencesViewModel.ALL }),
        ) {
            LicencesScreen(onBack = dropUnlessResumed { navController.popBackStack() })
        }

        composable(Routes.PROFILE) {
            ProfileScreen(
                onBack = dropUnlessResumed { navController.popBackStack() },
                onEarnClick = dropUnlessResumed { navController.navigate(Routes.EARN) },
                onSettingsClick = dropUnlessResumed { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.EARN) {
            EarnScreen(onBack = dropUnlessResumed { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = dropUnlessResumed { navController.popBackStack() },
                onOpenAiProviders = dropUnlessResumed { navController.navigate(Routes.AI_PROVIDERS) },
            )
        }
        composable(Routes.AI_PROVIDERS) {
            AiProvidersScreen(onBack = dropUnlessResumed { navController.popBackStack() })
        }
        composable(Routes.COACH) {
            CoachScreen(
                onBack = dropUnlessResumed { navController.popBackStack() },
                onOpenProviders = dropUnlessResumed { navController.navigate(Routes.AI_PROVIDERS) },
            )
        }
    }
}
