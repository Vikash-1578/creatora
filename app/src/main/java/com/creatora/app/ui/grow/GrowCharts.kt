package com.creatora.app.ui.grow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import com.creatora.app.ui.research.compactCount
import com.creatora.app.ui.theme.CreatoraTheme

/** One bar: [value] (null when unknown) and the [label] under it. */
data class Bar(val value: Long?, val label: String)

/**
 * A simple bar chart. The biggest bar uses the accent colour, the others the chart colour. [description]
 * is read out by screen readers instead of the individual bars.
 */
@Composable
fun BarChart(bars: List<Bar>, description: String, modifier: Modifier = Modifier) {
    val colors = CreatoraTheme.colors
    val max = bars.mapNotNull { it.value }.maxOrNull()?.coerceAtLeast(1) ?: 1
    val peak = bars.indices.maxByOrNull { bars[it].value ?: -1 }
    Column(modifier.clearAndSetSemantics { contentDescription = description }, verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(CreatoraTheme.dimens.chartHeight),
            horizontalArrangement = Arrangement.spacedBy(if (bars.size > MANY_BARS) CreatoraTheme.spacing.xs / 4 else CreatoraTheme.spacing.sm),
            verticalAlignment = Alignment.Bottom,
        ) {
            bars.forEachIndexed { index, bar ->
                val fraction = ((bar.value ?: 0).coerceAtLeast(0).toFloat() / max).coerceIn(MIN_BAR_FRACTION, 1f)
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(fraction)
                        .background(
                            if (index == peak && (bar.value ?: 0) > 0) colors.accent else colors.chartBar,
                            CreatoraTheme.shapes.radius8,
                        ),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (bars.size > MANY_BARS) CreatoraTheme.spacing.xs / 4 else CreatoraTheme.spacing.sm)) {
            bars.forEachIndexed { index, bar ->
                val show = bars.size <= MANY_BARS || index == 0 || index == bars.lastIndex || index % LABEL_EVERY == 0
                Text(
                    text = if (show) bar.label else "",
                    style = CreatoraTheme.typography.caption,
                    color = colors.textMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** A short description of the bars for screen readers: "Mon 120, Tue 80, ...". */
fun chartDescription(title: String, bars: List<Bar>): String =
    title + ": " + bars.joinToString(", ") { "${it.label} ${it.value?.let(::compactCount) ?: "no data"}" }

private const val MANY_BARS = 14
private const val LABEL_EVERY = 7
private const val MIN_BAR_FRACTION = 0.03f
