package com.creatora.app.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import com.creatora.app.R
import com.creatora.app.domain.account.ChannelPreview
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.account.PlatformSupport
import com.creatora.app.ui.components.PrimaryButton
import com.creatora.app.ui.components.SecondaryButton
import com.creatora.app.ui.theme.CreatoraTheme
import java.text.NumberFormat

fun ConnectPlatform.labelRes(): Int = when (this) {
    ConnectPlatform.YOUTUBE -> R.string.platform_youtube
    ConnectPlatform.INSTAGRAM -> R.string.platform_instagram
    ConnectPlatform.FACEBOOK -> R.string.platform_facebook
    ConnectPlatform.TIKTOK -> R.string.platform_tiktok
    ConnectPlatform.MOJ -> R.string.platform_moj
    ConnectPlatform.JOSH -> R.string.platform_josh
    ConnectPlatform.SHARECHAT -> R.string.platform_sharechat
    ConnectPlatform.SNAPCHAT -> R.string.platform_snapchat
}

@Composable
fun ConnectProblem.message(): String = stringResource(
    when (this) {
        ConnectProblem.NOT_AUTHORIZED -> R.string.connect_problem_not_authorized
        ConnectProblem.NETWORK -> R.string.connect_problem_network
        ConnectProblem.QUOTA -> R.string.connect_problem_quota
        ConnectProblem.NOT_FOUND -> R.string.connect_problem_not_found
        ConnectProblem.NOT_AVAILABLE -> R.string.connect_problem_not_available
        ConnectProblem.UNRECOGNISED -> R.string.connect_problem_unrecognised
        ConnectProblem.SAVE_FAILED -> R.string.connect_problem_save_failed
    },
)

/** Round picture with the first letter as a stand-in. */
@Composable
fun Avatar(photoUrl: String?, name: String, modifier: Modifier = Modifier) {
    val colors = CreatoraTheme.colors
    Box(
        modifier = modifier.size(CreatoraTheme.dimens.avatarSize).clip(CircleShape).background(colors.accentSoft),
        contentAlignment = Alignment.Center,
    ) {
        if (photoUrl != null) {
            AsyncImage(model = photoUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(CreatoraTheme.dimens.avatarSize))
        } else {
            Text(name.firstOrNull()?.uppercase().orEmpty(), style = CreatoraTheme.typography.screenTitle, color = colors.accentText)
        }
    }
}

/** The preview card: photo, name, numbers (only those the platform gave) and "Is this you?". */
@Composable
fun ChannelCard(preview: ChannelPreview, confirmLabel: String, onConfirm: () -> Unit, modifier: Modifier = Modifier) {
    val colors = CreatoraTheme.colors
    val spacing = CreatoraTheme.spacing
    val shape = CreatoraTheme.shapes.radius16
    val numbers = NumberFormat.getIntegerInstance()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(CreatoraTheme.dimens.borderWidth, colors.border, shape)
            .padding(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            Avatar(preview.photoUrl, preview.title)
            Column(Modifier.weight(1f)) {
                Text(preview.title, style = CreatoraTheme.typography.cardTitle, color = colors.text, maxLines = 1)
                preview.handle?.let { Text(it, style = CreatoraTheme.typography.caption, color = colors.textMuted, maxLines = 1) }
                preview.subscribers?.let {
                    Text(
                        pluralStringResource(R.plurals.channel_subscribers, it.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), numbers.format(it)),
                        style = CreatoraTheme.typography.caption, color = colors.textMuted,
                    )
                } ?: Text(stringResource(R.string.channel_subscribers_hidden), style = CreatoraTheme.typography.caption, color = colors.textMuted)
                preview.videoCount?.let {
                    Text(
                        pluralStringResource(R.plurals.channel_videos, it.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), numbers.format(it)),
                        style = CreatoraTheme.typography.caption, color = colors.textMuted,
                    )
                }
            }
        }
        PrimaryButton(confirmLabel, onConfirm, Modifier.fillMaxWidth())
    }
}

/**
 * Connect a platform. YouTube: "Login with Google" finds the channels on the Google account (a pasted link also
 * works). Platforms with a login but no connection yet: their own "Login with …" button, which says it is coming
 * soon. Platforms without an API: paste the link and only the link is saved. Shared by onboarding and Settings.
 */
@Composable
fun AddAccountPanel(
    lookup: LookupUi,
    onLogin: (ConnectPlatform) -> Unit,
    onLookUp: (input: String, platform: ConnectPlatform) -> Unit,
    onConfirm: (ChannelPreview) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CreatoraTheme.colors
    val spacing = CreatoraTheme.spacing
    var platform by rememberSaveable { mutableStateOf(ConnectPlatform.YOUTUBE) }
    var input by rememberSaveable { mutableStateOf("") }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            ConnectPlatform.entries.forEach { entry ->
                com.creatora.app.ui.components.ChoiceChip(stringResource(entry.labelRes()), entry == platform, { platform = entry; onDismiss() })
            }
        }
        val hint = when (platform.support) {
            PlatformSupport.FULL -> R.string.connect_hint_full
            PlatformSupport.COMING_SOON -> if (platform == ConnectPlatform.INSTAGRAM) R.string.connect_hint_instagram else R.string.connect_hint_coming_soon
            PlatformSupport.LINK_ONLY -> R.string.connect_hint_link_only
        }
        Text(stringResource(hint), style = CreatoraTheme.typography.caption, color = colors.textMuted)
        if (platform.support != PlatformSupport.LINK_ONLY) {
            PrimaryButton(
                text = if (platform == ConnectPlatform.YOUTUBE) {
                    stringResource(R.string.connect_login_google)
                } else {
                    stringResource(R.string.connect_login_with, stringResource(platform.labelRes()))
                },
                onClick = { onLogin(platform) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !lookup.busy,
            )
        }
        if (platform.support != PlatformSupport.COMING_SOON) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.connect_input_label)) },
                singleLine = true,
            )
            SecondaryButton(
                text = stringResource(if (platform.support == PlatformSupport.LINK_ONLY) R.string.connect_save_link else R.string.connect_button),
                onClick = { onLookUp(input, platform) },
                modifier = Modifier.fillMaxWidth(),
                enabled = input.isNotBlank() && !lookup.busy,
            )
        }
        if (lookup.busy) CircularProgressIndicator(Modifier.size(CreatoraTheme.dimens.icon))
        lookup.problem?.let { Text(it.message(), style = CreatoraTheme.typography.body, color = colors.danger) }
        lookup.comingSoon?.let {
            Text(stringResource(R.string.connect_coming_soon, stringResource(it.labelRes())), style = CreatoraTheme.typography.body, color = colors.textMuted)
        }
        if (lookup.linkSaved) Text(stringResource(R.string.connect_link_saved), style = CreatoraTheme.typography.body, color = colors.success)
        lookup.planLimitMax?.let {
            Text(stringResource(R.string.connect_plan_limit, it), style = CreatoraTheme.typography.body, color = colors.warning)
        }
        lookup.pasted?.let { ChannelCard(it, stringResource(R.string.connect_is_this_you), { onConfirm(it) }) }
    }
}
