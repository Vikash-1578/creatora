package com.creatora.app.ui.brandkit

import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.creatora.app.R
import com.creatora.app.domain.model.BrandKit
import com.creatora.app.domain.model.FontFile
import com.creatora.app.ui.components.PrimaryButton
import com.creatora.app.ui.components.SectionTitle
import com.creatora.app.ui.components.SubScreenScaffold
import com.creatora.app.ui.theme.CreatoraTheme

@Composable
fun BrandKitScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BrandKitViewModel = hiltViewModel(),
) {
    val kit by viewModel.brandKit.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    message?.let {
        val text = stringResource(
            when (it) {
                BrandKitMessage.INVALID_LOGO -> R.string.brand_kit_invalid_logo
                BrandKitMessage.INVALID_FONT -> R.string.brand_kit_invalid_font
                BrandKitMessage.IMPORT_FAILED -> R.string.brand_kit_import_failed
            },
        )
        LaunchedEffect(it) {
            snackbar.showSnackbar(text)
            viewModel.messageShown()
        }
    }

    val pickLogo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.setLogo(it.toString()) }
    }
    val pickFont = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.addFont(it.toString()) }
    }

    BrandKitContent(
        kit = kit,
        snackbar = snackbar,
        onBack = onBack,
        onPickLogo = { pickLogo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onRemoveLogo = viewModel::removeLogo,
        onAddColor = viewModel::addColor,
        onRemoveColor = viewModel::removeColor,
        onPickFont = { pickFont.launch(arrayOf("*/*")) },
        onRemoveFont = viewModel::removeFont,
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BrandKitContent(
    kit: BrandKit,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onPickLogo: () -> Unit,
    onRemoveLogo: () -> Unit,
    onAddColor: (String) -> Boolean,
    onRemoveColor: (String) -> Unit,
    onPickFont: () -> Unit,
    onRemoveFont: (FontFile) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = CreatoraTheme.spacing
    Box(modifier) {
        SubScreenScaffold(title = stringResource(R.string.brand_kit_title), onBack = onBack) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                SectionTitle(stringResource(R.string.brand_kit_logo))
                if (kit.logoUri == null) {
                    Muted(stringResource(R.string.brand_kit_logo_empty))
                } else {
                    AsyncImage(
                        model = kit.logoUri,
                        contentDescription = stringResource(R.string.brand_kit_logo_description),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = CreatoraTheme.dimens.logoPreviewMaxHeight)
                            .clip(CreatoraTheme.shapes.radius16)
                            .background(CreatoraTheme.colors.surfaceAlt),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    PrimaryButton(stringResource(R.string.brand_kit_logo_pick), onPickLogo)
                    if (kit.logoUri != null) {
                        PrimaryButton(stringResource(R.string.action_remove), onRemoveLogo)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                SectionTitle(stringResource(R.string.brand_kit_colors))
                if (kit.colors.isEmpty()) Muted(stringResource(R.string.brand_kit_colors_empty))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    kit.colors.forEach { hex -> ColorChip(hex, onRemove = { onRemoveColor(hex) }) }
                }
                AddColorRow(onAddColor)
            }

            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                SectionTitle(stringResource(R.string.brand_kit_fonts))
                if (kit.fonts.isEmpty()) Muted(stringResource(R.string.brand_kit_fonts_empty))
                kit.fonts.forEach { font ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            font.name,
                            style = CreatoraTheme.typography.body,
                            color = CreatoraTheme.colors.text,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                        )
                        RemoveButton(stringResource(R.string.brand_kit_remove_font, font.name)) { onRemoveFont(font) }
                    }
                }
                PrimaryButton(stringResource(R.string.brand_kit_font_pick), onPickFont)
            }
        }
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun Muted(text: String) {
    Text(text, style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.textMuted)
}

@Composable
private fun RemoveButton(description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(CreatoraTheme.dimens.minTouchTarget)) {
        Icon(Icons.Outlined.Close, contentDescription = description, tint = CreatoraTheme.colors.textMuted)
    }
}

/** Swatch with its hex code and a remove button. */
@Composable
private fun ColorChip(hex: String, onRemove: () -> Unit) {
    val swatch = remember(hex) { Color(android.graphics.Color.parseColor(hex)) }
    Row(
        modifier = Modifier
            .clip(CreatoraTheme.shapes.radius12)
            .background(CreatoraTheme.colors.surface)
            .border(CreatoraTheme.dimens.borderWidth, CreatoraTheme.colors.border, CreatoraTheme.shapes.radius12)
            .padding(start = CreatoraTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(CreatoraTheme.spacing.xl)
                .clip(CircleShape)
                .background(swatch)
                .border(CreatoraTheme.dimens.borderWidth, CreatoraTheme.colors.border, CircleShape),
        )
        Text(
            hex,
            style = CreatoraTheme.typography.bodySemiBold,
            color = CreatoraTheme.colors.text,
            modifier = Modifier.padding(horizontal = CreatoraTheme.spacing.sm),
        )
        RemoveButton(stringResource(R.string.brand_kit_remove_color, hex), onRemove)
    }
}

@Composable
private fun AddColorRow(onAddColor: (String) -> Boolean) {
    var text by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; error = false },
            label = { Text(stringResource(R.string.brand_kit_color_label)) },
            isError = error,
            supportingText = if (error) {
                { Text(stringResource(R.string.brand_kit_color_error)) }
            } else {
                null
            },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        PrimaryButton(
            text = stringResource(R.string.action_add),
            onClick = {
                if (onAddColor(text)) text = "" else error = true
            },
            modifier = Modifier.padding(top = CreatoraTheme.spacing.sm),
        )
    }
}

@Composable
private fun BrandKitPreview(darkTheme: Boolean, kit: BrandKit) {
    CreatoraTheme(darkTheme = darkTheme) {
        BrandKitContent(
            kit = kit,
            snackbar = remember { SnackbarHostState() },
            onBack = {}, onPickLogo = {}, onRemoveLogo = {}, onAddColor = { true },
            onRemoveColor = {}, onPickFont = {}, onRemoveFont = {},
        )
    }
}

private val PreviewKit = BrandKit(colors = listOf("#5B47F0", "#14151A"), fonts = listOf(FontFile("Brand.ttf", "file:///x")))

@Preview(name = "Light", showBackground = true)
@Composable
private fun BrandKitLightPreview() = BrandKitPreview(false, PreviewKit)

@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BrandKitDarkPreview() = BrandKitPreview(true, PreviewKit)

@Preview(name = "Empty, light", showBackground = true)
@Composable
private fun BrandKitEmptyLightPreview() = BrandKitPreview(false, BrandKit())
