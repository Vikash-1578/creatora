package com.creatora.app.ui.earn

import android.content.res.Configuration
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.creatora.app.R
import com.creatora.app.ui.components.SubScreenScaffold
import com.creatora.app.ui.theme.CreatoraTheme

/** Placeholder until the Earn feature is built. */
@Composable
fun EarnScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    SubScreenScaffold(
        title = stringResource(R.string.earn_title),
        onBack = onBack,
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.earn_subtitle),
            style = CreatoraTheme.typography.body,
            color = CreatoraTheme.colors.textMuted,
        )
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun EarnScreenLightPreview() {
    CreatoraTheme(darkTheme = false) { EarnScreen(onBack = {}) }
}

@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun EarnScreenDarkPreview() {
    CreatoraTheme(darkTheme = true) { EarnScreen(onBack = {}) }
}
