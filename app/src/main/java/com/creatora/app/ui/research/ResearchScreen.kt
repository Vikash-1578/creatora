package com.creatora.app.ui.research

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.creatora.app.R
import com.creatora.app.ui.components.FeatureCard
import com.creatora.app.ui.components.ScreenHeader
import com.creatora.app.ui.components.TabScreen
import com.creatora.app.ui.theme.CreatoraTheme

/** Research tab. Each section says what it will show and what is needed to switch it on. */
@Composable
fun ResearchScreen(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    TabScreen(modifier = modifier) {
        ScreenHeader(
            title = stringResource(R.string.tab_research),
            subtitle = stringResource(R.string.research_subtitle),
        )
        Column(
            modifier = Modifier.padding(horizontal = CreatoraTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.md),
        ) {
            val action = stringResource(R.string.research_action_key)
            FeatureCard(Icons.Outlined.Search, stringResource(R.string.research_keywords), stringResource(R.string.research_keywords_body), action, onOpenSettings)
            FeatureCard(Icons.Outlined.Star, stringResource(R.string.research_outliers), stringResource(R.string.research_outliers_body), action, onOpenSettings)
            FeatureCard(Icons.Outlined.Groups, stringResource(R.string.research_competitors), stringResource(R.string.research_competitors_body), action, onOpenSettings)
            FeatureCard(Icons.Outlined.TrendingUp, stringResource(R.string.research_trends), stringResource(R.string.research_trends_body), action, onOpenSettings)
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun ResearchScreenLightPreview() {
    CreatoraTheme(darkTheme = false) { ResearchScreen(onOpenSettings = {}) }
}

@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ResearchScreenDarkPreview() {
    CreatoraTheme(darkTheme = true) { ResearchScreen(onOpenSettings = {}) }
}
