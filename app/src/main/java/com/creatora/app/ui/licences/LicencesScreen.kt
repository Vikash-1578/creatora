package com.creatora.app.ui.licences

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.creatora.app.R
import com.creatora.app.domain.model.Asset
import com.creatora.app.domain.model.AssetType
import com.creatora.app.domain.model.LicenceEntry
import com.creatora.app.ui.components.EmptyState
import com.creatora.app.ui.components.ListGroup
import com.creatora.app.ui.components.ListRowDivider
import com.creatora.app.ui.components.PrimaryButton
import com.creatora.app.ui.components.SubScreenScaffold
import com.creatora.app.ui.theme.CreatoraTheme

@Composable
fun LicencesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LicencesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LicencesContent(state, onBack, viewModel::addEntry, viewModel::delete, modifier)
}

@Composable
fun LicencesContent(
    state: LicencesUiState,
    onBack: () -> Unit,
    onAdd: (source: String, licence: String, author: String, url: String) -> Unit,
    onDelete: (LicenceEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    var adding by rememberSaveable { mutableStateOf(false) }
    val asset = state.asset
    SubScreenScaffold(
        title = stringResource(if (asset == null) R.string.licences_title else R.string.licences_title_asset),
        onBack = onBack,
        modifier = modifier,
    ) {
        if (asset != null) {
            Text(asset.name, style = CreatoraTheme.typography.cardTitle, color = CreatoraTheme.colors.text)
            PrimaryButton(stringResource(R.string.licences_add), { adding = true })
        }
        if (state.rows.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.Description,
                title = stringResource(R.string.licences_title),
                body = stringResource(if (asset == null) R.string.licences_empty_all else R.string.licences_empty_asset),
            )
        } else {
            ListGroup {
                state.rows.forEachIndexed { index, row ->
                    if (index > 0) ListRowDivider()
                    EntryRow(row, showAsset = asset == null, onDelete = { onDelete(row.entry) })
                }
            }
        }
    }
    if (adding) {
        AddEntryDialog(
            onAdd = { source, licence, author, url ->
                onAdd(source, licence, author, url)
                adding = false
            },
            onDismiss = { adding = false },
        )
    }
}

@Composable
private fun EntryRow(row: LicenceRow, showAsset: Boolean, onDelete: () -> Unit) {
    val entry = row.entry
    val colors = CreatoraTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = CreatoraTheme.spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = CreatoraTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.xs),
        ) {
            if (showAsset) {
                Text(
                    row.assetName ?: stringResource(R.string.licences_unknown_asset),
                    style = CreatoraTheme.typography.bodyBold,
                    color = colors.text,
                )
            }
            Text(entry.licence, style = CreatoraTheme.typography.bodySemiBold, color = colors.accentText)
            Text(entry.source, style = CreatoraTheme.typography.body, color = colors.text)
            val details = listOfNotNull(
                entry.author.takeIf { it.isNotBlank() }?.let { stringResource(R.string.licences_entry_by, it) },
                entry.url.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (details.isNotEmpty()) {
                Text(details, style = CreatoraTheme.typography.caption, color = colors.textMuted)
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(CreatoraTheme.dimens.minTouchTarget)) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = stringResource(R.string.licences_delete_entry),
                tint = colors.textMuted,
            )
        }
    }
}

@Composable
private fun AddEntryDialog(
    onAdd: (String, String, String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var source by rememberSaveable { mutableStateOf("") }
    var licence by rememberSaveable { mutableStateOf("") }
    var author by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.licences_add_title), style = CreatoraTheme.typography.sectionTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
                OutlinedTextField(source, { source = it }, label = { Text(stringResource(R.string.licences_source_label)) }, singleLine = true)
                OutlinedTextField(licence, { licence = it }, label = { Text(stringResource(R.string.licences_licence_label)) }, singleLine = true)
                OutlinedTextField(author, { author = it }, label = { Text(stringResource(R.string.licences_author_label)) }, singleLine = true)
                OutlinedTextField(url, { url = it }, label = { Text(stringResource(R.string.licences_url_label)) }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(source, licence, author, url) },
                enabled = source.isNotBlank() && licence.isNotBlank(),
            ) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        containerColor = CreatoraTheme.colors.surface,
    )
}

private val PreviewAsset = Asset(id = 1, uri = "file:///a", type = AssetType.IMAGE, name = "cover.jpg", mimeType = "image/jpeg")
private val PreviewRows = listOf(
    LicenceRow(LicenceEntry(id = 1, assetId = 1, source = "Own photo", licence = "Owned", author = "Me"), "cover.jpg"),
)

@Composable
private fun LicencesPreview(darkTheme: Boolean, state: LicencesUiState) {
    CreatoraTheme(darkTheme = darkTheme) { LicencesContent(state, {}, { _, _, _, _ -> }, {}) }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun LicencesLightPreview() = LicencesPreview(false, LicencesUiState(PreviewAsset, PreviewRows))

@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LicencesDarkPreview() = LicencesPreview(true, LicencesUiState(PreviewAsset, PreviewRows))

@Preview(name = "Empty, light", showBackground = true)
@Composable
private fun LicencesEmptyLightPreview() = LicencesPreview(false, LicencesUiState())
