package com.creatora.app.ui.grow

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Science
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.creatora.app.R
import com.creatora.app.ui.components.FeatureCard
import com.creatora.app.ui.components.ScreenHeader
import com.creatora.app.ui.components.TabScreen
import com.creatora.app.ui.theme.CreatoraTheme

/** Grow tab. Each section says what it will show and what is needed to switch it on. */
@Composable
fun GrowScreen(onConnectChannel: () -> Unit, onOpenCreate: () -> Unit, modifier: Modifier = Modifier) {
    TabScreen(modifier = modifier) {
        ScreenHeader(
            title = stringResource(R.string.tab_grow),
            subtitle = stringResource(R.string.grow_subtitle),
        )
        Column(
            modifier = Modifier.padding(horizontal = CreatoraTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.md),
        ) {
            val connect = stringResource(R.string.grow_action_connect)
            FeatureCard(Icons.Outlined.BarChart, stringResource(R.string.grow_analytics), stringResource(R.string.grow_analytics_body), connect, onConnectChannel)
            FeatureCard(Icons.Outlined.ChatBubbleOutline, stringResource(R.string.grow_comments), stringResource(R.string.grow_comments_body), connect, onConnectChannel)
            FeatureCard(Icons.Outlined.Science, stringResource(R.string.grow_tests), stringResource(R.string.grow_tests_body), stringResource(R.string.grow_action_create), onOpenCreate)
            FeatureCard(Icons.Outlined.Checklist, stringResource(R.string.grow_audit), stringResource(R.string.grow_audit_body), connect, onConnectChannel)
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun GrowScreenLightPreview() {
    CreatoraTheme(darkTheme = false) { GrowScreen(onConnectChannel = {}, onOpenCreate = {}) }
}

@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GrowScreenDarkPreview() {
    CreatoraTheme(darkTheme = true) { GrowScreen(onConnectChannel = {}, onOpenCreate = {}) }
}
