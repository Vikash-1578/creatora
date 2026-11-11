package com.creatora.app.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import com.creatora.app.R
import com.creatora.app.ui.ai.message
import com.creatora.app.ui.components.SecondaryButton
import com.creatora.app.ui.theme.CreatoraTheme
import java.text.NumberFormat
import java.util.Locale

@Composable
private fun InsightBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = CreatoraTheme.shapes.radius16
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CreatoraTheme.colors.surface)
            .border(CreatoraTheme.dimens.borderWidth, CreatoraTheme.colors.border, shape)
            .padding(CreatoraTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm),
    ) { content() }
}

/** Home's insights block. Every number shown is a fetched metric; missing ones are said to be missing. */
@Composable
fun InsightsSection(state: InsightsUiState, onConnect: () -> Unit, onRefresh: () -> Unit) {
    val colors = CreatoraTheme.colors
    val body = CreatoraTheme.typography.body
    when (state) {
        InsightsUiState.NoAccount -> InsightBox {
            Text(stringResource(R.string.insights_no_account), style = body, color = colors.textMuted)
            SecondaryButton(stringResource(R.string.home_channel_action), onConnect)
        }
        is InsightsUiState.Analysing -> InsightBox {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.md)) {
                CircularProgressIndicator(Modifier.size(CreatoraTheme.dimens.icon))
                Text(stringResource(R.string.insights_analysing), style = body, color = colors.textMuted)
            }
        }
        is InsightsUiState.NotEnoughVideos -> InsightBox {
            Text(stringResource(R.string.insights_not_enough, state.count), style = body, color = colors.textMuted)
        }
        InsightsUiState.AllHealthy -> InsightBox {
            Text(stringResource(R.string.insights_all_healthy), style = body, color = colors.textMuted)
        }
        InsightsUiState.FetchFailed -> InsightBox {
            Text(stringResource(R.string.insights_fetch_failed), style = body, color = colors.danger)
            SecondaryButton(stringResource(R.string.action_try_again), onRefresh)
        }
        is InsightsUiState.Ready -> Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.md)) {
            if (state.analyticsMissing) Text(stringResource(R.string.insights_analytics_missing), style = CreatoraTheme.typography.caption, color = colors.textMuted)
            state.aiErrors?.forEach { Text(it.message(), style = CreatoraTheme.typography.caption, color = colors.danger) }
            state.cards.forEach { card ->
                val numbers = NumberFormat.getIntegerInstance()
                val notAvailable = stringResource(R.string.insights_not_available)
                fun percent(value: Double?) = value?.let { String.format(Locale.US, "%.1f%%", it) } ?: notAvailable
                InsightBox {
                    Text(card.item.video.title, style = CreatoraTheme.typography.cardTitle, color = colors.text, maxLines = 2)
                    Text(
                        stringResource(R.string.insights_views, numbers.format(card.item.video.views), card.item.percentOfMedian, numbers.format(card.item.medianViews)),
                        style = CreatoraTheme.typography.caption, color = colors.textMuted,
                    )
                    Text(
                        stringResource(R.string.insights_metrics, percent(card.analytics?.ctrPercent), percent(card.analytics?.averageViewPercent)),
                        style = CreatoraTheme.typography.caption, color = colors.textMuted,
                    )
                    card.insight?.let {
                        Text(stringResource(R.string.insights_why), style = CreatoraTheme.typography.bodySemiBold, color = colors.text)
                        Text(it.why, style = body, color = colors.textMuted)
                        Text(stringResource(R.string.insights_fix), style = CreatoraTheme.typography.bodySemiBold, color = colors.text)
                        Text(it.fix, style = body, color = colors.textMuted)
                    }
                }
            }
            SecondaryButton(stringResource(R.string.insights_refresh), onRefresh, Modifier.fillMaxWidth())
        }
    }
}
