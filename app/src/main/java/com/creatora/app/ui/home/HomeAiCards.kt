package com.creatora.app.ui.home

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.TipsAndUpdates
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.creatora.app.R
import com.creatora.app.domain.account.VideoStats
import com.creatora.app.domain.home.CreatorIdea
import com.creatora.app.ui.ai.message
import com.creatora.app.ui.components.PrimaryButton
import com.creatora.app.ui.components.SecondaryButton
import com.creatora.app.ui.components.StatusChip
import com.creatora.app.ui.components.Tone
import com.creatora.app.ui.components.VideoThumbnail
import com.creatora.app.ui.theme.CreatoraTheme

/** "Aaj ka focus": one tip from real numbers, with the numbers it rests on. */
@Composable
fun FocusSection(state: FocusState, onFix: () -> Unit, onLater: () -> Unit) {
    if (state == FocusState.NoAccount) return
    HomeSection(R.string.home_focus_title, icon = Icons.Outlined.TipsAndUpdates) {
        HomeCard {
            when (state) {
                FocusState.NoAccount -> Unit
                FocusState.PickOne -> BodyText(stringResource(R.string.home_pick_one_account))
                FocusState.Loading -> LoadingRow(R.string.home_loading)
                FocusState.Later -> BodyText(stringResource(R.string.home_focus_later))
                FocusState.NotEnoughData -> BodyText(stringResource(R.string.home_focus_not_enough))
                is FocusState.Unavailable -> BodyText(state.error.message())
                is FocusState.AiFailed -> state.errors.forEach { Text(it.message(), style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.danger) }
                is FocusState.Ready -> {
                    BodyText(state.tip.tip, muted = false)
                    if (state.tip.source.isNotBlank()) Text(stringResource(R.string.home_focus_source, state.tip.source), style = CreatoraTheme.typography.caption, color = CreatoraTheme.colors.textMuted)
                    Row(horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
                        PrimaryButton(stringResource(R.string.home_focus_fix), onFix)
                        SecondaryButton(stringResource(R.string.home_focus_later_button), onLater)
                    }
                }
            }
        }
    }
}

/** Daily Ideas: Free 3, Pro 10 a day; each with an AI-estimated potential and a reason that cites real data. */
@Composable
fun IdeasSection(state: IdeasState, onCreate: (CreatorIdea) -> Unit, onSave: (CreatorIdea) -> Unit, onDismiss: (CreatorIdea) -> Unit, onSeeAll: (() -> Unit)? = null) {
    if (state == IdeasState.NoAccount) return
    HomeSection(
        R.string.home_ideas_title, icon = Icons.Outlined.Lightbulb, onSeeAll = onSeeAll,
        info = (state as? IdeasState.Ready)?.let { stringResource(R.string.home_ideas_note, it.limit) },
    ) {
        when (state) {
            IdeasState.NoAccount -> Unit
            IdeasState.PickOne -> HomeCard { BodyText(stringResource(R.string.home_pick_one_account)) }
            IdeasState.Loading -> HomeCard { LoadingRow(R.string.home_loading) }
            IdeasState.NotEnoughData -> HomeCard { BodyText(stringResource(R.string.home_ideas_not_enough)) }
            is IdeasState.Unavailable -> HomeCard { BodyText(state.error.message()) }
            is IdeasState.AiFailed -> HomeCard {
                if (state.errors.isEmpty()) BodyText(stringResource(R.string.home_ideas_bad_reply))
                state.errors.forEach { Text(it.message(), style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.danger) }
            }
            is IdeasState.Ready -> {
                if (state.ideas.isEmpty()) HomeCard { BodyText(stringResource(R.string.home_ideas_done, state.limit)) }
                state.ideas.forEach { idea ->
                    HomeCard {
                        Text(idea.title, style = CreatoraTheme.typography.cardTitle, color = CreatoraTheme.colors.text)
                        idea.score?.let { StatusChip(stringResource(R.string.home_idea_score, it), Tone.NEUTRAL) }
                        if (idea.reason.isNotBlank()) BodyText(idea.reason)
                        Row(horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
                            PrimaryButton(stringResource(R.string.home_idea_create), { onCreate(idea) })
                            SecondaryButton(stringResource(R.string.home_idea_save), { onSave(idea) })
                            TextButton(onClick = { onDismiss(idea) }, modifier = Modifier.heightIn(min = CreatoraTheme.dimens.minTouchTarget)) { Text(stringResource(R.string.home_idea_dismiss)) }
                        }
                    }
                }
            }
        }
    }
}

/** Trending for the user's niche and region. Updated at most once a day to save API quota. */
@Composable
fun TrendingSection(state: TrendingState, onOpen: (VideoStats) -> Unit, onUseAsIdea: (VideoStats) -> Unit, onRefresh: () -> Unit, onSeeAll: (() -> Unit)? = null) {
    HomeSection(R.string.home_trending_title, icon = Icons.Outlined.Bolt, onSeeAll = onSeeAll) {
        when (state) {
            TrendingState.Loading -> HomeCard { LoadingRow(R.string.home_loading) }
            is TrendingState.Failed -> HomeCard {
                BodyText(state.error.message())
                SecondaryButton(stringResource(R.string.action_try_again), onRefresh)
            }
            is TrendingState.Ready -> HomeCard {
                BodyText(
                    if (state.niche != null) stringResource(R.string.home_trending_for, state.niche, state.region) else stringResource(R.string.home_trending_general, state.region),
                )
                if (state.items.isEmpty()) BodyText(stringResource(R.string.home_trending_empty))
                state.items.take(MAX_SHOWN).forEach { video ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = CreatoraTheme.dimens.minTouchTarget),
                        horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.md), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        VideoThumbnail(video.thumbnailUrl, Modifier.clickable(role = Role.Button) { onOpen(video) })
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.xs)) {
                            Text(
                                video.title, style = CreatoraTheme.typography.cardTitle, color = CreatoraTheme.colors.text, maxLines = 2,
                                modifier = Modifier.clickable(role = Role.Button) { onOpen(video) },
                            )
                            Text(
                                stringResource(R.string.home_trending_item, video.channelTitle.ifBlank { "-" }, compactCount(video.views)),
                                style = CreatoraTheme.typography.caption, color = CreatoraTheme.colors.textMuted, maxLines = 1,
                            )
                            TextButton(onClick = { onUseAsIdea(video) }, modifier = Modifier.heightIn(min = CreatoraTheme.dimens.minTouchTarget)) { Text(stringResource(R.string.home_trending_use)) }
                        }
                    }
                }
                Text(
                    stringResource(
                        if (state.stale) R.string.home_trending_stale else R.string.home_trending_updated,
                        DateUtils.getRelativeTimeSpanString(state.cachedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                    ),
                    style = CreatoraTheme.typography.caption, color = CreatoraTheme.colors.textMuted,
                )
                SecondaryButton(stringResource(R.string.home_trending_refresh), onRefresh)
            }
        }
    }
}

private const val MAX_SHOWN = 8
