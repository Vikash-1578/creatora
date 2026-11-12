package com.creatora.app.ui.research

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.creatora.app.R
import com.creatora.app.domain.account.VideoStats
import com.creatora.app.domain.home.ParsedIdea
import com.creatora.app.domain.model.AiError
import com.creatora.app.ui.ai.message
import com.creatora.app.ui.components.SecondaryButton
import com.creatora.app.ui.home.BodyText
import com.creatora.app.ui.home.HomeCard
import com.creatora.app.ui.home.compactCount
import com.creatora.app.ui.theme.CreatoraTheme

/** A small label such as "Estimate" or "AI estimate", so nothing estimated looks measured. */
@Composable
fun Tag(textRes: Int, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(textRes),
        modifier = modifier
            .clip(CreatoraTheme.shapes.radius8)
            .background(CreatoraTheme.colors.accentSoft)
            .padding(horizontal = CreatoraTheme.spacing.sm, vertical = CreatoraTheme.spacing.xs),
        style = CreatoraTheme.typography.caption,
        color = CreatoraTheme.colors.accentText,
    )
}

/** A card with one line of plain text: the empty and not-available states. */
@Composable
fun MessageCard(textRes: Int) = HomeCard { BodyText(stringResource(textRes)) }

@Composable
fun SectionLabel(textRes: Int, modifier: Modifier = Modifier) {
    Text(stringResource(textRes), modifier = modifier, style = CreatoraTheme.typography.sectionTitle, color = CreatoraTheme.colors.text)
}

/** When the data was fetched, and a Refresh button that says what it costs in YouTube quota units. */
@Composable
fun UpdatedRow(cachedAt: Long, stale: Boolean, quotaUnits: Int, onRefresh: () -> Unit) {
    val ago = DateUtils.getRelativeTimeSpanString(cachedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
    Text(
        stringResource(if (stale) R.string.research_stale else R.string.research_updated, ago),
        style = CreatoraTheme.typography.caption, color = CreatoraTheme.colors.textMuted,
    )
    SecondaryButton(stringResource(R.string.research_refresh, quotaUnits), onRefresh)
}

/** Why an AI call failed. An empty list means the reply could not be read. */
@Composable
fun AiProblem(errors: List<AiError>) {
    if (errors.isEmpty()) {
        Text(stringResource(R.string.research_ai_unreadable), style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.danger)
    } else {
        errors.forEach { Text(it.message(), style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.danger) }
    }
}

/** A suggested idea with its AI-estimate score, a Save and a Create action. */
@Composable
fun IdeaRow(idea: ParsedIdea, saved: Boolean, onSave: () -> Unit, onCreate: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.xs)) {
        Text(idea.title, style = CreatoraTheme.typography.cardTitle, color = CreatoraTheme.colors.text)
        if (idea.reason.isNotBlank()) Text(idea.reason, style = CreatoraTheme.typography.caption, color = CreatoraTheme.colors.textMuted)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
            idea.score?.let { Text(stringResource(R.string.research_potential, it), style = CreatoraTheme.typography.caption, color = CreatoraTheme.colors.textMuted) }
            Tag(R.string.research_ai_estimate)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
            TextButton(onClick = onSave, enabled = !saved, modifier = Modifier.heightIn(min = CreatoraTheme.dimens.minTouchTarget)) {
                Text(stringResource(if (saved) R.string.research_saved else R.string.research_save))
            }
            TextButton(onClick = onCreate, modifier = Modifier.heightIn(min = CreatoraTheme.dimens.minTouchTarget)) { Text(stringResource(R.string.research_create)) }
        }
    }
}

/** A video title with its views; tapping opens it. */
@Composable
fun VideoLine(video: VideoStats, onOpen: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().heightIn(min = CreatoraTheme.dimens.minTouchTarget).clickable(role = Role.Button, onClick = onOpen),
        verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.xs),
    ) {
        Text(video.title, style = CreatoraTheme.typography.cardTitle, color = CreatoraTheme.colors.text, maxLines = 2)
        Text(
            stringResource(R.string.research_views_line, compactCount(video.views), video.channelTitle.ifBlank { "-" }),
            style = CreatoraTheme.typography.caption, color = CreatoraTheme.colors.textMuted,
        )
    }
}

fun openVideo(context: Context, videoId: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$videoId"))) }
}

/** The frame the previews of the tabs sit in. */
@Composable
fun PreviewColumn(content: @Composable () -> Unit) {
    Column(Modifier.background(CreatoraTheme.colors.background).padding(CreatoraTheme.spacing.lg), verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.md)) { content() }
}
