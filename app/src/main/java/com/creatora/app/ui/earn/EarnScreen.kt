package com.creatora.app.ui.earn

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.creatora.app.R
import com.creatora.app.domain.grow.Earnings
import com.creatora.app.domain.grow.PartnerProgram
import com.creatora.app.domain.grow.PathKind
import com.creatora.app.domain.grow.Period
import com.creatora.app.domain.grow.Progress
import com.creatora.app.domain.grow.Requirement
import com.creatora.app.domain.grow.TierProgress
import com.creatora.app.ui.components.ChangeChip
import com.creatora.app.ui.components.MetricTile
import com.creatora.app.ui.components.SecondaryButton
import com.creatora.app.ui.components.SectionHeader
import com.creatora.app.ui.components.StatusChip
import com.creatora.app.ui.components.SubScreenScaffold
import com.creatora.app.ui.components.Tone
import com.creatora.app.ui.grow.EarnUiState
import com.creatora.app.ui.grow.EarnViewModel
import com.creatora.app.ui.grow.ExplainCard
import com.creatora.app.ui.grow.Load
import com.creatora.app.ui.grow.Loaded
import com.creatora.app.ui.grow.PeriodChips
import com.creatora.app.ui.home.BodyText
import com.creatora.app.ui.home.HomeCard
import com.creatora.app.ui.home.compactCount
import com.creatora.app.ui.home.message
import com.creatora.app.ui.home.percentText
import com.creatora.app.ui.theme.CreatoraTheme
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

/** Earn: estimated revenue when YouTube returns it, and the monetization journey (an estimate from API data). */
@Composable
fun EarnScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: EarnViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    EarnContent(state, onBack, viewModel::setPeriod, viewModel::makePlan, modifier)
}

@Composable
fun EarnContent(state: EarnUiState, onBack: () -> Unit, onPeriod: (Period) -> Unit, onPlan: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = CreatoraTheme.spacing
    val context = LocalContext.current
    SubScreenScaffold(title = stringResource(R.string.earn_title), onBack = onBack, modifier = modifier) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.xl)) {
            SectionHeader(stringResource(R.string.earn_earnings), icon = Icons.Outlined.Payments, info = stringResource(R.string.earn_earnings_info))
            PeriodChips(state.period, onPeriod)
            when (val earnings = state.earnings) {
                // A refused revenue report means not monetized or no access: say so, never show a zero.
                is Load.Failed -> HomeCard {
                    BodyText(stringResource(R.string.earn_not_available))
                    BodyText(earnings.error.message())
                }
                else -> Loaded(earnings, {}) { EarningsCards(it) }
            }
            SectionHeader(stringResource(R.string.earn_journey), icon = Icons.Outlined.Flag, info = stringResource(R.string.earn_journey_info, PartnerProgram.LAST_CHECKED))
            StatusChip(stringResource(R.string.earn_estimate_label), Tone.WARN)
            Loaded(state.journey, {}) { tiers -> tiers.forEach { TierCard(it) } }
            if (state.journey is Load.Ready) ExplainCard(state.plan, onPlan, R.string.earn_plan)
            SecondaryButton(stringResource(R.string.earn_open_studio), {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://studio.youtube.com/")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun EarningsCards(earnings: Earnings) {
    val locale = Locale.getDefault()
    val money = NumberFormat.getCurrencyInstance(locale).apply { currency = Currency.getInstance("USD") }
    StatusChip(stringResource(R.string.grow_data_as_of, earnings.asOf.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))), Tone.NEUTRAL)
    MetricTile(
        label = stringResource(R.string.earn_revenue), value = money.format(earnings.revenue), icon = Icons.Outlined.Payments, modifier = Modifier.fillMaxWidth(),
        change = {
            earnings.previousRevenue?.takeIf { it > 0 }?.let { before ->
                val percent = (earnings.revenue - before) * 100 / before
                ChangeChip(percent, stringResource(R.string.grow_vs_previous, percentText(percent, locale)))
            }
        },
    )
    HomeCard {
        Text(stringResource(R.string.earn_rpm), style = CreatoraTheme.typography.caption, color = CreatoraTheme.colors.textMuted)
        Text(earnings.rpm?.let { money.format(it) } ?: stringResource(R.string.home_not_available), style = CreatoraTheme.typography.screenTitle, color = CreatoraTheme.colors.text)
        BodyText(stringResource(R.string.earn_rpm_info, compactCount(earnings.views, locale)))
    }
}

private fun Requirement.labelRes() = when (this) {
    Requirement.SUBSCRIBERS -> R.string.earn_req_subscribers
    Requirement.UPLOADS_90 -> R.string.earn_req_uploads
    Requirement.WATCH_HOURS_365 -> R.string.earn_req_watch_hours
    Requirement.SHORTS_VIEWS_90 -> R.string.earn_req_shorts
}

@Composable
private fun ProgressRow(progress: Progress) {
    val locale = Locale.getDefault()
    val colors = CreatoraTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.xs)) {
        Text(stringResource(progress.requirement.labelRes()), style = CreatoraTheme.typography.body, color = colors.text)
        Text(
            progress.have?.let { stringResource(R.string.earn_have_need, compactCount(it.toLong(), locale), compactCount(progress.need.toLong(), locale)) }
                ?: stringResource(R.string.earn_unknown_need, compactCount(progress.need.toLong(), locale)),
            style = CreatoraTheme.typography.caption, color = if (progress.met) colors.success else colors.textMuted,
        )
        Box(Modifier.fillMaxWidth().height(CreatoraTheme.dimens.progressHeight).clip(CreatoraTheme.shapes.radius8).background(colors.surfaceAlt)) {
            Box(Modifier.fillMaxWidth((progress.fraction ?: 0.0).toFloat()).height(CreatoraTheme.dimens.progressHeight).clip(CreatoraTheme.shapes.radius8).background(if (progress.met) colors.success else colors.chartBar))
        }
    }
}

@Composable
private fun TierCard(tier: TierProgress) {
    HomeCard {
        Text(stringResource(if (tier.tier == 1) R.string.earn_tier1 else R.string.earn_tier2), style = CreatoraTheme.typography.cardTitle, color = CreatoraTheme.colors.text)
        if (tier.met) StatusChip(stringResource(R.string.earn_tier_met), Tone.GOOD)
        tier.paths.forEach { path ->
            val closest = tier.closest?.kind == path.kind && !tier.met
            Text(
                stringResource(if (path.kind == PathKind.WATCH_HOURS) R.string.earn_path_watch else R.string.earn_path_shorts) +
                    if (closest) " · " + stringResource(R.string.earn_closest) else "",
                style = CreatoraTheme.typography.caption, color = if (closest) CreatoraTheme.colors.accentText else CreatoraTheme.colors.textMuted,
            )
            path.items.forEach { ProgressRow(it) }
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun EarnScreenLightPreview() {
    CreatoraTheme(darkTheme = false) { EarnContent(EarnUiState(earnings = Load.NoAccount, journey = Load.NoAccount), {}, {}, {}) }
}

@Preview(name = "Dark", showBackground = true)
@Composable
private fun EarnScreenDarkPreview() {
    CreatoraTheme(darkTheme = true) { EarnContent(EarnUiState(earnings = Load.NoAccount, journey = Load.NoAccount), {}, {}, {}) }
}
