package com.creatora.app.ui.profile

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.creatora.app.R
import com.creatora.app.ui.components.ListGroup
import com.creatora.app.ui.components.ListRowDivider
import com.creatora.app.ui.components.NavigationListRow
import com.creatora.app.ui.components.SubScreenScaffold
import com.creatora.app.ui.theme.CreatoraTheme

@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onEarnClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SubScreenScaffold(
        title = stringResource(R.string.profile_title),
        onBack = onBack,
        modifier = modifier,
    ) {
        ListGroup {
            NavigationListRow(title = stringResource(R.string.earn_title), onClick = onEarnClick)
            ListRowDivider()
            NavigationListRow(title = stringResource(R.string.settings_title), onClick = onSettingsClick)
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun ProfileScreenLightPreview() {
    CreatoraTheme(darkTheme = false) { ProfileScreen(onBack = {}, onEarnClick = {}, onSettingsClick = {}) }
}

@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileScreenDarkPreview() {
    CreatoraTheme(darkTheme = true) { ProfileScreen(onBack = {}, onEarnClick = {}, onSettingsClick = {}) }
}
