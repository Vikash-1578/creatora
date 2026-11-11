package com.creatora.app.ui.navigation

import android.net.Uri
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
import com.creatora.app.ui.calendar.CalendarScreen
import com.creatora.app.ui.coach.CoachScreen
import com.creatora.app.ui.create.CreateScreen
import com.creatora.app.ui.create.WizardScreen
import com.creatora.app.ui.create.WizardViewModel
import com.creatora.app.ui.coach.CoachViewModel
import com.creatora.app.ui.earn.EarnScreen
import com.creatora.app.ui.notifications.NotificationsScreen
import com.creatora.app.ui.grow.GrowScreen
import com.creatora.app.ui.grow.GrowScreen
import com.creatora.app.ui.home.HomeScreen
import com.creatora.app.ui.library.LibraryScreen
import com.creatora.app.ui.licences.LicencesScreen
import com.creatora.app.ui.licences.LicencesViewModel
import com.creatora.app.ui.profile.ProfileScreen
import com.creatora.app.ui.publish.PublishScreen
import com.creatora.app.ui.publish.PublishViewModel
import com.creatora.app.ui.research.ResearchScreen
import com.creatora.app.ui.saved.SavedScreen
import com.creatora.app.ui.study.StudyScreen
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
    const val BRAND_KIT = "brand_kit"
    const val WIZARD = "wizard/{projectId}"
    const val AI_PROVIDERS = "ai_providers"
    const val STUDY = "study"
    const val PUBLISH = "publish/{projectId}"
    const val CALENDAR = "calendar"
    const val SAVED = "saved"
    const val THUMBNAIL = "thumbnail/{projectId}"
    const val COACH = "coach?question={question}"
    const val NOTIFICATIONS = "notifications"
    const val LICENCES = "licences?assetId={assetId}"

    fun coach(question: String? = null) = if (question == null) "coach" else "coach?question=${Uri.encode(question)}"

    fun wizard(projectId: Long) = "wizard/$projectId"

    fun thumbnail(projectId: Long) = "thumbnail/$projectId"

    fun publish(projectId: Long) = "publish/$projectId"

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
                    onNotificationsClick = dropUnlessResumed { navController.navigate(Routes.NOTIFICATIONS) },
                    onContinue = { id -> navController.navigate(Routes.wizard(id)) },
                    onOpenCoach = { question -> navController.navigate(Routes.coach(question)) },
                    onOpenPublish = { id -> navController.navigate(Routes.publish(id)) },
                    onOpenGrow = { navController.navigateToTab(TopLevelTab.GROW) },
                    onOpenIdeas = dropUnlessResumed { navController.navigate(Routes.SAVED) },
                )
            }
        }
        navigation(route = TopLevelTab.RESEARCH.graphRoute, startDestination = Routes.RESEARCH) {
            composable(Routes.RESEARCH) {
                ResearchScreen(
                    onOpenKeys = dropUnlessResumed { navController.navigate(Routes.AI_PROVIDERS) },
                    onOpenStudy = dropUnlessResumed { navController.navigate(Routes.STUDY) },
                    onOpenSaved = dropUnlessResumed { navController.navigate(Routes.SAVED) },
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
                    onOpenKeys = dropUnlessResumed { navController.navigate(Routes.AI_PROVIDERS) },
                    onProjectStarted = { id -> navController.navigate(Routes.wizard(id)) },
                )
            }
        }
        navigation(route = TopLevelTab.LIBRARY.graphRoute, startDestination = Routes.LIBRARY) {
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    onOpenBrandKit = dropUnlessResumed { navController.navigate(Routes.BRAND_KIT) },
                    onOpenLicences = { assetId -> navController.navigate(Routes.licences(assetId)) },
                    onOpenProject = { id -> navController.navigate(Routes.wizard(id)) },
                    onOpenPublish = { id -> navController.navigate(Routes.publish(id)) },
                    onOpenCalendar = dropUnlessResumed { navController.navigate(Routes.CALENDAR) },
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
                onOpenPublish = { id -> navController.navigate(Routes.publish(id)) },
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
        composable(
            route = Routes.PUBLISH,
            arguments = listOf(navArgument(PublishViewModel.ARG_PROJECT_ID) { type = NavType.LongType }),
        ) {
            PublishScreen(
                onBack = dropUnlessResumed { navController.popBackStack() },
                onOpenThumbnail = { id -> navController.navigate(Routes.thumbnail(id)) },
                onOpenKeys = dropUnlessResumed { navController.navigate(Routes.AI_PROVIDERS) },
            )
        }
        composable(Routes.CALENDAR) {
            CalendarScreen(
                onBack = dropUnlessResumed { navController.popBackStack() },
                onOpenPublish = { id -> navController.navigate(Routes.publish(id)) },
            )
        }
        composable(Routes.STUDY) {
            StudyScreen(
                onBack = dropUnlessResumed { navController.popBackStack() },
                onOpenKeys = dropUnlessResumed { navController.navigate(Routes.AI_PROVIDERS) },
            )
        }
        composable(Routes.SAVED) {
            SavedScreen(
                onBack = dropUnlessResumed { navController.popBackStack() },
                onProjectStarted = { id -> navController.navigate(Routes.wizard(id)) },
            )
        }
        composable(Routes.AI_PROVIDERS) {
            AiProvidersScreen(onBack = dropUnlessResumed { navController.popBackStack() })
        }
        composable(Routes.NOTIFICATIONS) {
            NotificationsScreen(
                onBack = dropUnlessResumed { navController.popBackStack() },
                onOpenPublish = { id -> navController.navigate(Routes.publish(id)) },
                onOpenGrow = { navController.navigateToTab(TopLevelTab.GROW) },
            )
        }
        composable(
            route = Routes.COACH,
            arguments = listOf(navArgument(CoachViewModel.ARG_QUESTION) { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) {
            CoachScreen(
                onBack = dropUnlessResumed { navController.popBackStack() },
                onOpenProviders = dropUnlessResumed { navController.navigate(Routes.AI_PROVIDERS) },
            )
        }
    }
}
