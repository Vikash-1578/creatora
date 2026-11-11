package com.creatora.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.graphics.vector.ImageVector
import com.creatora.app.R

/**
 * The five bottom-bar tabs, in display order. Each tab is its own nested navigation graph
 * ([graphRoute]) so it keeps its own back stack; [startRoute] is the tab's first screen.
 */
enum class TopLevelTab(
    val graphRoute: String,
    val startRoute: String,
    @StringRes val label: Int,
    @StringRes val contentDescription: Int,
    val icon: ImageVector,
    /** The Create tab is drawn as a raised accent circle. */
    val isRaised: Boolean = false,
) {
    HOME("home_tab", Routes.HOME, R.string.tab_home, R.string.cd_tab_home, Icons.Outlined.Home),
    RESEARCH("research_tab", Routes.RESEARCH, R.string.tab_research, R.string.cd_tab_research, Icons.Outlined.Search),
    CREATE("create_tab", Routes.CREATE, R.string.tab_create, R.string.cd_tab_create, Icons.Outlined.Add, isRaised = true),
    GROW("grow_tab", Routes.GROW, R.string.tab_grow, R.string.cd_tab_grow, Icons.AutoMirrored.Outlined.TrendingUp),
    LIBRARY("library_tab", Routes.LIBRARY, R.string.tab_library, R.string.cd_tab_library, Icons.Outlined.Collections),
}
