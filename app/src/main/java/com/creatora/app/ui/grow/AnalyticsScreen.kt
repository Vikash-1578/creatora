package com.creatora.app.ui.grow

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.creatora.app.R
import com.creatora.app.domain.account.PlanLimits
import com.creatora.app.domain.grow.AudienceReport
import com.creatora.app.domain.grow.ContentReport
import com.creatora.app.domain.grow.Overview
import com.creatora.app.domain.grow.Period
import com.creatora.app.domain.grow.Series
import com.creatora.app.domain.grow.Share
import com.creatora.app.domain.grow.changePercent
import com.creatora.app.domain.grow.notEnoughData
import com.creatora.app.domain.grow.usual
import com.creatora.app.ui.ai.message
import com.creatora.app.ui.components.ChangeChip
import com.creatora.app.ui.components.ChoiceChip
import com.creatora.app.ui.components.MetricTile
import com.creatora.app.ui.components.SecondaryButton
import com.creatora.app.ui.components.SectionHeader
import com.creatora.app.ui.components.StatusChip
import com.creatora.app.ui.components.SubScreenScaffold
import com.creatora.app.ui.components.Tone
import com.creatora.app.ui.home.BodyText
import com.creatora.app.ui.home.HomeCard
import com.creatora.app.ui.home.LoadingRow
import com.creatora.app.ui.home.compactCount
import com.creatora.app.ui.home.message
import com.creatora.app.ui.home.percentText
import com.creatora.app.ui.theme.CreatoraTheme
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** Grow, Analytics: the account's own YouTube numbers for 7, 28 or 90 days, in Creatora's cards. */
@Composable
fun AnalyticsScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: AnalyticsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AnalyticsContent(state, onBack, viewModel::setPeriod, viewModel::setTab, viewModel::refresh, viewModel::explain, modifier)
}

@Composable
fun PeriodChips(period: Period, onPeriod: (Period) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
        Period.entries.forEach { p -> ChoiceChip(pluralStringResource(R.plurals.grow_days, p.days, p.days), p == period, { onPeriod(p) }) }
    }
}

@Composable
fun AnalyticsContent(
    state: AnalyticsUiState, onBack: () -> Unit, onPeriod: (Period) -> Unit, onTab: (AnalyticsTab) -> Unit, onRefresh: () -> Unit, onExplain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = CreatoraTheme.spacing
    SubScreenScaffold(title = stringResource(R.string.grow_analytics), onBack = onBack, modifier = modifier) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.xl)) {
            PeriodChips(state.period, onPeriod)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                ChoiceChip(stringResource(R.string.grow_tab_overview), state.tab == AnalyticsTab.OVERVIEW, { onTab(AnalyticsTab.OVERVIEW) })
                ChoiceChip(stringResource(R.string.grow_tab_content), state.tab == AnalyticsTab.CONTENT, { onTab(AnalyticsTab.CONTENT) })
                ChoiceChip(stringResource(R.string.grow_tab_audience), state.tab == AnalyticsTab.AUDIENCE, { onTab(AnalyticsTab.AUDIENCE) })
            }
            when (state.tab) {
                AnalyticsTab.OVERVIEW -> Loaded(state.overview, onRefresh) { OverviewCards(it, state.period) }
                AnalyticsTab.CONTENT -> Loaded(state.content, onRefresh) { ContentCards(it) }
                AnalyticsTab.AUDIENCE -> Loaded(state.audience, onRefresh) { AudienceCards(it) }
            }
            ExplainCard(state.explain, onExplain, R.string.grow_explain)
        }
    }
}

/** The loading, missing-account and failed states every report shares; [ready] draws the data. */
@Composable
fun <T> Loaded(load: Load<T>, onRetry: () -> Unit, ready: @Composable (T) -> Unit) {
    when (load) {
        Load.Loading -> LoadingRow(R.string.home_loading)
        Load.NoAccount -> HomeCard { BodyText(stringResource(R.string.grow_no_account)) }
        Load.PickOne -> HomeCard { BodyText(stringResource(R.string.home_pick_one_account)) }
        is Load.Failed -> HomeCard {
            BodyText(load.error.message())
            SecondaryButton(stringResource(R.string.action_try_again), onRetry)
        }
        is Load.Ready -> ready(load.value)
    }
}

/** One AI action for the cards above; the cost is shown before the tap, and the cards work without it. */
@Composable
fun ExplainCard(explain: ExplainState, onExplain: () -> Unit, labelRes: Int) {
    val cost = PlanLimits.costs.text
    HomeCard {
        explain.text?.let { BodyText(it, muted = false) }
        explain.errors.forEach { Text(it.message(), style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.danger) }
        if (explain.busy) LoadingRow(R.string.home_loading) else SecondaryButton(stringResource(labelRes), onExplain, Modifier.fillMaxWidth())
        Text(pluralStringResource(R.plurals.create_credit_cost, cost, cost), style = CreatoraTheme.typography.caption, color = CreatoraTheme.colors.textMuted)
    }
}

@Composable
private fun SeriesTile(label: String, icon: ImageVector, series: Series, value: String, chartText: String) {
    val locale = Locale.getDefault()
    MetricTile(
        label = label, value = value, icon = icon, modifier = Modifier.fillMaxWidth(), chart = series.daily, chartDescription = chartText,
        change = {
            changePercent(series.sums)?.let { ChangeChip(it, stringResource(R.string.grow_vs_previous, percentText(it, locale))) }
                ?: Text(stringResource(R.string.home_change_no_previous), style = CreatoraTheme.typography.caption, color = CreatoraTheme.colors.textMuted)
            // "Your usual" is the median of the six periods before, and says so; it is left out when there are too few.
            usual(series.sums)?.let { Text(stringResource(R.string.grow_vs_usual, compactCount(it.toLong(), locale)), style = CreatoraTheme.typography.caption, color = CreatoraTheme.colors.textMuted) }
        },
    )
}

@Composable
private fun OverviewCards(overview: Overview, period: Period) {
    val locale = Locale.getDefault()
    val chart = stringResource(R.string.grow_chart_daily)
    StatusChip(stringResource(R.string.grow_data_as_of, overview.asOf.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))), Tone.NEUTRAL)
    if (notEnoughData(overview.views.total, overview.daysOfData)) {
        HomeCard { BodyText(stringResource(R.string.grow_not_enough, overview.views.total, overview.daysOfData, period.days)) }
    }
    SeriesTile(stringResource(R.string.grow_views), Icons.Outlined.PlayCircleOutline, overview.views, compactCount(overview.views.total, locale), chart)
    SeriesTile(stringResource(R.string.home_watch_time), Icons.Outlined.Schedule, overview.watchMinutes, stringResource(R.string.home_hours, compactCount(overview.watchMinutes.total / 60, locale)), chart)
    SeriesTile(stringResource(R.string.grow_subscribers_net), Icons.Outlined.Group, overview.subscribersNet, com.creatora.app.ui.home.signedCompact(overview.subscribersNet.total, locale), chart)
    HomeCard { BodyText(stringResource(R.string.grow_no_realtime)) }
}

/** A ranked list with bars: the label, its percent and a bar as wide as its share. Empty lists say the report is not available. */
@Composable
fun ShareList(titleRes: Int, icon: ImageVector, shares: List<Share>, label: @Composable (String) -> String = { it }, info: String? = null) {
    val colors = CreatoraTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.md)) {
        SectionHeader(stringResource(titleRes), icon = icon, info = info)
        HomeCard {
            if (shares.isEmpty()) BodyText(stringResource(R.string.grow_report_empty))
            shares.forEach { share ->
                Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.xs)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
                        Text(label(share.label), style = CreatoraTheme.typography.body, color = colors.text, modifier = Modifier.weight(1f), maxLines = 1)
                        Text(String.format(Locale.getDefault(), "%.0f%%", share.percent), style = CreatoraTheme.typography.cardTitle, color = colors.text)
                    }
                    Box(Modifier.fillMaxWidth().height(CreatoraTheme.dimens.progressHeight).clip(CreatoraTheme.shapes.radius8).background(colors.surfaceAlt)) {
                        Box(Modifier.fillMaxWidth((share.percent / 100).toFloat().coerceIn(0f, 1f)).height(CreatoraTheme.dimens.progressHeight).clip(CreatoraTheme.shapes.radius8).background(colors.chartBar))
                    }
                }
            }
        }
    }
}

private val TrafficLabels = mapOf(
    "YT_SEARCH" to R.string.traffic_search, "RELATED_VIDEO" to R.string.traffic_suggested, "SUGGESTED_VIDEO" to R.string.traffic_suggested,
    "SHORTS" to R.string.traffic_shorts, "SUBSCRIBER" to R.string.traffic_subscribers, "EXT_URL" to R.string.traffic_external,
    "NOTIFICATION" to R.string.traffic_notifications, "PLAYLIST" to R.string.traffic_playlists, "YT_CHANNEL" to R.string.traffic_channel,
    "YT_OTHER_PAGE" to R.string.traffic_other, "NO_LINK_OTHER" to R.string.traffic_direct, "other" to R.string.traffic_other,
)

/** The API's source types in plain words; a type this table does not know is shown as YouTube names it. */
@Composable
private fun trafficLabel(type: String): String = TrafficLabels[type]?.let { stringResource(it) } ?: type.lowercase().replace('_', ' ')

@Composable
private fun ContentCards(content: ContentReport) {
    val locale = Locale.getDefault()
    ShareList(R.string.grow_traffic, Icons.Outlined.Insights, content.traffic, { trafficLabel(it) })
    ShareList(R.string.grow_search_terms, Icons.Outlined.Search, content.searchTerms, { if (it == "other") stringResource(R.string.traffic_other) else it })
    Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.md)) {
        SectionHeader(stringResource(R.string.grow_top_videos), icon = Icons.Outlined.VideoLibrary)
        HomeCard {
            if (content.topVideos.isEmpty()) BodyText(stringResource(R.string.grow_report_empty))
            content.topVideos.forEach { (title, views) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
                    Text(title, style = CreatoraTheme.typography.body, color = CreatoraTheme.colors.text, modifier = Modifier.weight(1f), maxLines = 2)
                    Text(compactCount(views, locale), style = CreatoraTheme.typography.cardTitle, color = CreatoraTheme.colors.text)
                }
            }
        }
    }
    ShareList(R.string.grow_content_type, Icons.Outlined.PlayCircleOutline, content.shortsShare, { it.lowercase().replace('_', ' ') })
}

@Composable
private fun AudienceCards(audience: AudienceReport) {
    ShareList(R.string.grow_age_gender, Icons.Outlined.Group, audience.ageGender, { it.removePrefix("age").replace('_', ' ') })
    ShareList(R.string.grow_countries, Icons.Outlined.Public, audience.countries, { if (it == "other") stringResource(R.string.traffic_other) else Locale("", it).displayCountry.ifBlank { it } })
    ShareList(R.string.grow_devices, Icons.Outlined.Devices, audience.devices, { it.lowercase().replace('_', ' ') })
    ShareList(
        R.string.grow_subscribed, Icons.Outlined.Group, audience.subscribed,
        { stringResource(if (it.equals("SUBSCRIBED", ignoreCase = true)) R.string.grow_subscribed_yes else R.string.grow_subscribed_no) },
    )
    // YouTube shares no hours when viewers are online: only the weekday pattern, said plainly.
    ShareList(
        R.string.grow_weekdays, Icons.Outlined.Today, audience.weekdays,
        { runCatching { DayOfWeek.valueOf(it).getDisplayName(TextStyle.FULL, Locale.getDefault()) }.getOrDefault(it) },
        info = stringResource(R.string.grow_weekdays_info),
    )
    HomeCard { BodyText(stringResource(R.string.grow_audience_missing)) }
}

@Preview(name = "Analytics light", showBackground = true)
@Composable
private fun AnalyticsLightPreview() {
    CreatoraTheme(darkTheme = false) { AnalyticsContent(AnalyticsUiState(overview = Load.NoAccount), {}, {}, {}, {}, {}) }
}

@Preview(name = "Analytics dark", showBackground = true)
@Composable
private fun AnalyticsDarkPreview() {
    CreatoraTheme(darkTheme = true) { AnalyticsContent(AnalyticsUiState(overview = Load.NoAccount), {}, {}, {}, {}, {}) }
}
