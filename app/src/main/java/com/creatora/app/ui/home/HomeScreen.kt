package com.creatora.app.ui.home

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.creatora.app.R
import com.creatora.app.ui.components.HeaderIconButton
import com.creatora.app.ui.components.ScreenHeader
import com.creatora.app.ui.components.TabScreen
import com.creatora.app.ui.theme.CreatoraTheme

/** Home tab. Placeholder content; header has Notifications and Profile buttons. */
@Composable
fun HomeScreen(
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
    onNotificationsClick: () -> Unit = {},
) {
    TabScreen(modifier = modifier) {
        ScreenHeader(
            title = stringResource(R.string.tab_home),
            subtitle = stringResource(R.string.home_subtitle),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
                HeaderIconButton(
                    icon = Icons.Outlined.Notifications,
                    contentDescription = stringResource(R.string.cd_notifications),
                    onClick = onNotificationsClick,
                )
                HeaderIconButton(
                    icon = Icons.Outlined.Person,
                    contentDescription = stringResource(R.string.cd_profile),
                    onClick = onProfileClick,
                )
            }
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun HomeScreenLightPreview() {
    CreatoraTheme(darkTheme = false) { HomeScreen(onProfileClick = {}) }
}

@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeScreenDarkPreview() {
    CreatoraTheme(darkTheme = true) { HomeScreen(onProfileClick = {}) }
}
