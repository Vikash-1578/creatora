package com.creatora.app.ui.grow

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.creatora.app.R
import com.creatora.app.ui.components.ScreenHeader
import com.creatora.app.ui.components.TabScreen
import com.creatora.app.ui.theme.CreatoraTheme

/** Grow tab. Placeholder until the feature is built. */
@Composable
fun GrowScreen(modifier: Modifier = Modifier) {
    TabScreen(modifier = modifier) {
        ScreenHeader(
            title = stringResource(R.string.tab_grow),
            subtitle = stringResource(R.string.grow_subtitle),
        )
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun GrowScreenLightPreview() {
    CreatoraTheme(darkTheme = false) { GrowScreen() }
}

@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GrowScreenDarkPreview() {
    CreatoraTheme(darkTheme = true) { GrowScreen() }
}
