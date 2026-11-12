package com.creatora.app.ui.navigation

import androidx.compose.runtime.Composable
import com.creatora.app.BuildConfig
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
import com.creatora.app.ui.help.HelpScreen
import com.creatora.app.ui.home.HomeScreen
import com.creatora.app.ui.home.NotificationInboxScreen
import com.creatora.app.ui.library.LibraryScreen
import com.creatora.app.ui.licences.LicencesScreen
import com.creatora.app.ui.licences.LicencesViewModel
import com.creatora.app.ui.profile.ProfileScreen
import com.creatora.app.ui.research.CompetitorDetailScreen
import com.creatora.app.ui.research.CompetitorDetailViewModel
import com.creatora.app.ui.research.ResearchScreen
import com.creatora.app.ui.settings.AccountSettingsScreen
import com.creatora.app.ui.settings.AiSettingsScreen
import com.creatora.app.ui.settings.AppearanceSettingsScreen
import com.creatora.app.ui.settings.BillingSettingsScreen
import com.creatora.app.ui.settings.HelpAboutScreen
import com.creatora.app.ui.settings.NotificationSettingsScreen
import com.creatora.app.ui.settings.PrivacyScreen
import com.creatora.app.ui.settings.SettingsDestinations
import com.creatora.app.ui.settings.SettingsScreen
import com.creatora.app.ui.thumbnail.ThumbnailScreen
import com.creatora.app.ui.thumbnail.ThumbnailViewModel

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
    const val SETTINGS_ACCOUNT = "settings/account"
    const val SETTINGS_NOTIFICATIONS = "settings/notifications"
    const val SETTINGS_AI = "settings/ai"
    const val SETTINGS_BILLING = "settings/billing"
    const val SETTINGS_APPEARANCE = "settings/appearance"
    const val SETTINGS_PRIVACY = "settings/privacy"
    const val SETTINGS_HELP = "settings/help"
    const val HELP = "help"
    const val BRAND_KIT = "brand_kit"
    const val WIZARD = "wizard/{projectId}"
    const val AI_PROVIDERS = "ai_providers"
    const val THUMBNAIL = "thumbnail/{projectId}"
    const val COACH = "coach"
    const val NOTIFICATIONS = "notifications"
    const val COMPETITOR = "research/competitor/{channelId}"
    const val LICENCES = "licences?assetId={assetId}"

    fun wizard(projectId: Long) = "wizard/$projectId"

    fun competitor(channelId: String) = "research/competitor/$channelId"

    fun thumbnail(projectId: Long) = "thumbnail/$projectId"

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
                    onNotifications = dropUnlessResumed { navController.navigate(Routes.NOTIFICATIONS) },
                    onOpenCoach = dropUnlessResumed { navController.navigate(Routes.COACH) },
                    onProjectOpened = { id -> navController.navigate(Routes.wizard(id)) },
                    onNewVideo = { navController.navigateToTab(TopLevelTab.CREATE) },
                    onOpenThumbnail = { id -> navController.navigate(Routes.thumbnail(id)) },
                    onConnectChannel = dropUnlessResumed { navController.navigate(Routes.SETTINGS_ACCOUNT) },
                )
            }
        }
        navigation(route = TopLevelTab.RESEARCH.graphRoute, startDestination = Routes.RESEARCH) {
            composable(Routes.RESEARCH) {
                ResearchScreen(
                    onOpenSettings = dropUnlessResumed { navController.navigate(Routes.SETTINGS_ACCOUNT) },
                    onOpenCompetitor = { id -> navController.navigate(Routes.competitor(id)) },
                    onOpenProject = { id -> navController.navigate(Routes.wizard(id)) },
                )
            }
            composable(
                route = Routes.COMPETITOR,
                arguments = listOf(navArgument(CompetitorDetailViewModel.ARG_CHANNEL_ID) { type = NavType.StringType }),
            ) {
                CompetitorDetailScreen(
                    onBack = dropUnlessResumed { navController.popBackStack() },
                    onOpenProject = { id -> navController.navigate(Routes.wizard(id)) },
                )
            }
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
            composable(Routes.GROW) {
                GrowScreen(
                    onConnectChannel = dropUnlessResumed { navController.navigate(Routes.SETTINGS_ACCOUNT) },
                    onOpenCreate = { navController.navigateToTab(TopLevelTab.CREATE) },
                )
            }
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
                onOpenThumbnail = { id -> navController.navigate(Routes.thumbnail(id)) },
            )
        }
        composable(
            route = Routes.THUMBNAIL,
            arguments = listOf(navArgument(ThumbnailViewModel.ARG_PROJECT_ID) { type = NavType.LongType }),
        ) {
            ThumbnailScreen(onBack = dropUnlessResumed { navController.popBackStack() })
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
                onHelpClick = dropUnlessResumed { navController.navigate(Routes.HELP) },
            )
        }
        composable(Routes.HELP) {
            HelpScreen(onBack = dropUnlessResumed { navController.popBackStack() })
        }
        composable(Routes.EARN) {
            EarnScreen(onBack = dropUnlessResumed { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = dropUnlessResumed { navController.popBackStack() },
                destinations = SettingsDestinations(
                    account = dropUnlessResumed { navController.navigate(Routes.SETTINGS_ACCOUNT) },
                    notifications = dropUnlessResumed { navController.navigate(Routes.SETTINGS_NOTIFICATIONS) },
                    ai = dropUnlessResumed { navController.navigate(Routes.SETTINGS_AI) },
                    billing = dropUnlessResumed { navController.navigate(Routes.SETTINGS_BILLING) },
                    appearance = dropUnlessResumed { navController.navigate(Routes.SETTINGS_APPEARANCE) },
                    privacy = dropUnlessResumed { navController.navigate(Routes.SETTINGS_PRIVACY) },
                    help = dropUnlessResumed { navController.navigate(Routes.SETTINGS_HELP) },
                    devAiKeys = dropUnlessResumed { navController.navigate(Routes.AI_PROVIDERS) },
                ),
            )
        }
        composable(Routes.SETTINGS_ACCOUNT) { AccountSettingsScreen(onBack = dropUnlessResumed { navController.popBackStack() }) }
        composable(Routes.SETTINGS_NOTIFICATIONS) { NotificationSettingsScreen(onBack = dropUnlessResumed { navController.popBackStack() }) }
        composable(Routes.SETTINGS_AI) { AiSettingsScreen(onBack = dropUnlessResumed { navController.popBackStack() }) }
        composable(Routes.SETTINGS_BILLING) { BillingSettingsScreen(onBack = dropUnlessResumed { navController.popBackStack() }) }
        composable(Routes.SETTINGS_APPEARANCE) { AppearanceSettingsScreen(onBack = dropUnlessResumed { navController.popBackStack() }) }
        composable(Routes.SETTINGS_PRIVACY) {
            PrivacyScreen(
                onBack = dropUnlessResumed { navController.popBackStack() },
                onOpenAccount = dropUnlessResumed { navController.navigate(Routes.SETTINGS_ACCOUNT) },
            )
        }
        composable(Routes.SETTINGS_HELP) { HelpAboutScreen(onBack = dropUnlessResumed { navController.popBackStack() }) }
        composable(Routes.AI_PROVIDERS) {
            // User-entered keys exist only for developer builds; everyone else is sent to their plan and usage.
            if (BuildConfig.DEV_AI_KEYS) {
                AiProvidersScreen(onBack = dropUnlessResumed { navController.popBackStack() })
            } else {
                BillingSettingsScreen(onBack = dropUnlessResumed { navController.popBackStack() })
            }
        }
        composable(Routes.NOTIFICATIONS) { NotificationInboxScreen(onBack = dropUnlessResumed { navController.popBackStack() }) }
        composable(Routes.COACH) {
            CoachScreen(
                onBack = dropUnlessResumed { navController.popBackStack() },
                onOpenProviders = dropUnlessResumed { navController.navigate(Routes.AI_PROVIDERS) },
            )
        }
    }
}
