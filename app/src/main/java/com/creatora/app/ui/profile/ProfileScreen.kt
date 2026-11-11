package com.creatora.app.ui.profile

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.creatora.app.R
import com.creatora.app.ui.components.InfoListRow
import com.creatora.app.ui.components.ListGroup
import com.creatora.app.ui.components.ListRowDivider
import com.creatora.app.ui.components.NavigationListRow
import com.creatora.app.ui.components.SubScreenScaffold
import com.creatora.app.ui.components.TextInputDialog
import com.creatora.app.ui.theme.CreatoraTheme

@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onEarnClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onHelpClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val name by viewModel.displayName.collectAsStateWithLifecycle()
    ProfileContent(name, viewModel::setDisplayName, appVersion(), onBack, onEarnClick, onSettingsClick, onHelpClick, modifier)
}

@Composable
private fun appVersion(): String {
    val context = LocalContext.current
    return remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
}

@Composable
fun ProfileContent(
    displayName: String,
    onNameChange: (String) -> Unit,
    version: String,
    onBack: () -> Unit,
    onEarnClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onHelpClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    SubScreenScaffold(title = stringResource(R.string.profile_title), onBack = onBack, modifier = modifier) {
        ProfileHeader(displayName, onEdit = { editing = true })
        ListGroup {
            InfoListRow(stringResource(R.string.profile_channel), stringResource(R.string.profile_channel_not_connected))
            ListRowDivider()
            NavigationListRow(title = stringResource(R.string.earn_title), onClick = onEarnClick)
            ListRowDivider()
            NavigationListRow(title = stringResource(R.string.settings_title), onClick = onSettingsClick)
        }
        ListGroup {
            NavigationListRow(title = stringResource(R.string.help_title), onClick = onHelpClick)
            ListRowDivider()
            InfoListRow(stringResource(R.string.profile_about), version.ifEmpty { stringResource(R.string.profile_version_unknown) })
        }
    }
    if (editing) {
        TextInputDialog(
            title = stringResource(R.string.profile_name_title),
            label = stringResource(R.string.profile_name_label),
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = { editing = false; onNameChange(it) },
            onDismiss = { editing = false },
            initialValue = displayName,
        )
    }
}

/** Avatar with the first letter of the name, the name itself and a way to change it. */
@Composable
private fun ProfileHeader(displayName: String, onEdit: () -> Unit) {
    val colors = CreatoraTheme.colors
    val spacing = CreatoraTheme.spacing
    val shape = CreatoraTheme.shapes.radius16
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(CreatoraTheme.dimens.borderWidth, colors.border, shape)
            .clickable(role = Role.Button, onClick = onEdit)
            .heightIn(min = CreatoraTheme.dimens.minTouchTarget)
            .padding(spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        Box(
            modifier = Modifier.size(CreatoraTheme.dimens.avatarSize).clip(CircleShape).background(colors.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            if (displayName.isBlank()) {
                Icon(Icons.Outlined.Person, contentDescription = null, tint = colors.accentText)
            } else {
                Text(displayName.first().uppercase(), style = CreatoraTheme.typography.screenTitle, color = colors.accentText)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                displayName.ifBlank { stringResource(R.string.profile_name_empty) },
                style = CreatoraTheme.typography.sectionTitle,
                color = if (displayName.isBlank()) colors.textMuted else colors.text,
                maxLines = 1,
            )
            Text(stringResource(R.string.profile_name_hint), style = CreatoraTheme.typography.caption, color = colors.textMuted)
        }
        Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.profile_edit_name), tint = colors.textMuted)
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun ProfileScreenLightPreview() {
    CreatoraTheme(darkTheme = false) { ProfileContent("Asha", {}, "0.1.0", {}, {}, {}, {}) }
}

@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileScreenDarkPreview() {
    CreatoraTheme(darkTheme = true) { ProfileContent("", {}, "0.1.0", {}, {}, {}, {}) }
}
