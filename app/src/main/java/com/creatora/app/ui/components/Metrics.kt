package com.creatora.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import com.creatora.app.R
import com.creatora.app.ui.theme.CreatoraTheme

// Shared building blocks for data screens: a header with an icon, an optional ⓘ and "See all"; metric tiles with a
// change chip and mini chart; status chips; video thumbnails. Colours and sizes come from the theme only.

/** How good or bad something is, shown by colour and always with words, never colour alone. */
enum class Tone { GOOD, WARN, BAD, NEUTRAL }

@Composable
private fun Tone.color(): Color = when (this) {
    Tone.GOOD -> CreatoraTheme.colors.success
    Tone.WARN -> CreatoraTheme.colors.warning
    Tone.BAD -> CreatoraTheme.colors.danger
    Tone.NEUTRAL -> CreatoraTheme.colors.textMuted
}

/** The values scaled to 0..1 for drawing (lowest 0, highest 1); empty with fewer than two values; a flat series sits in the middle. */
fun sparkPoints(values: List<Long>): List<Float> {
    if (values.size < 2) return emptyList()
    val low = values.min()
    val range = values.max() - low
    return if (range == 0L) values.map { 0.5f } else values.map { (it - low).toFloat() / range }
}

/** A small icon in a soft square, the visual anchor of a section or tile. */
@Composable
fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(CreatoraTheme.dimens.icon + CreatoraTheme.spacing.sm)
            .clip(CreatoraTheme.shapes.radius8)
            .background(CreatoraTheme.colors.accentSoft),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = CreatoraTheme.colors.accentText)
    }
}

/**
 * The top of a section: [icon], [title], then optionally an ⓘ that opens the longer explanation under the title ([info])
 * and "See all". Long explanations live behind the ⓘ so the numbers stay in front.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    info: String? = null,
    onSeeAll: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    var showInfo by rememberSaveable { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
            icon?.let { IconBadge(it) }
            Text(title, style = CreatoraTheme.typography.sectionTitle, color = CreatoraTheme.colors.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            trailing()
            if (info != null) {
                IconButton(onClick = { showInfo = !showInfo }, modifier = Modifier.size(CreatoraTheme.dimens.minTouchTarget)) {
                    Icon(Icons.Outlined.Info, stringResource(R.string.info_toggle, title), tint = if (showInfo) CreatoraTheme.colors.accentText else CreatoraTheme.colors.textMuted)
                }
            }
            if (onSeeAll != null) {
                TextButton(onClick = onSeeAll, modifier = Modifier.heightIn(min = CreatoraTheme.dimens.minTouchTarget)) { Text(stringResource(R.string.see_all)) }
            }
        }
        if (info != null && showInfo) Text(info, style = CreatoraTheme.typography.caption, color = CreatoraTheme.colors.textMuted)
    }
}

/** A small pill with a coloured dot and words: "Good", "Needs work". */
@Composable
fun StatusChip(text: String, tone: Tone, modifier: Modifier = Modifier) {
    val color = tone.color()
    Row(
        modifier = modifier
            .clip(CreatoraTheme.shapes.radius8)
            .background(color.copy(alpha = CHIP_ALPHA))
            .padding(horizontal = CreatoraTheme.spacing.sm, vertical = CreatoraTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.xs),
    ) {
        Box(Modifier.size(CreatoraTheme.spacing.sm).clip(CreatoraTheme.shapes.radius8).background(color))
        Text(text, style = CreatoraTheme.typography.caption, color = CreatoraTheme.colors.text, maxLines = 1)
    }
}

/** "↑ +12%" or "↓ -3%": green for up, red for down, grey for no change; [text] carries the number. */
@Composable
fun ChangeChip(delta: Double, text: String, modifier: Modifier = Modifier) {
    val tone = if (delta > 0) Tone.GOOD else if (delta < 0) Tone.BAD else Tone.NEUTRAL
    val color = tone.color()
    Row(
        modifier = modifier
            .clip(CreatoraTheme.shapes.radius8)
            .background(color.copy(alpha = CHIP_ALPHA))
            .padding(horizontal = CreatoraTheme.spacing.sm, vertical = CreatoraTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.xs),
    ) {
        if (delta != 0.0) {
            Icon(if (delta > 0) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward, contentDescription = null, tint = color, modifier = Modifier.size(CreatoraTheme.spacing.lg))
        }
        Text(text, style = CreatoraTheme.typography.caption, color = color, maxLines = 1)
    }
}

/** An icon and a short figure: "👁 1.2K". */
@Composable
fun MetricChip(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.xs)) {
        Icon(icon, contentDescription = null, tint = CreatoraTheme.colors.textMuted, modifier = Modifier.size(CreatoraTheme.spacing.lg))
        Text(text, style = CreatoraTheme.typography.caption, color = CreatoraTheme.colors.textMuted, maxLines = 1)
    }
}

/** A mini line chart of [values] (oldest first). Draws nothing for fewer than two points; [description] is read by screen readers. */
@Composable
fun Sparkline(values: List<Long>, description: String, modifier: Modifier = Modifier) {
    val points = sparkPoints(values)
    if (points.isEmpty()) return
    val line = CreatoraTheme.colors.accent
    val fill = CreatoraTheme.colors.accentSoft
    val stroke = CreatoraTheme.dimens.borderWidth * 2
    Canvas(modifier.fillMaxWidth().height(CreatoraTheme.dimens.sparklineHeight).semantics { contentDescription = description }) {
        val inset = stroke.toPx()
        val w = size.width
        val h = size.height - 2 * inset
        fun at(i: Int) = Offset(w * i / (points.size - 1), inset + h * (1f - points[i]))
        val path = Path().apply { moveTo(at(0).x, at(0).y); for (i in 1 until points.size) lineTo(at(i).x, at(i).y) }
        val area = Path().apply { addPath(path); lineTo(w, size.height); lineTo(0f, size.height); close() }
        drawPath(area, fill.copy(alpha = AREA_ALPHA))
        drawPath(path, line, style = Stroke(width = stroke.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** A figure with its label above, an optional change chip and mini chart: the building block of a metric grid. */
@Composable
fun MetricTile(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    chart: List<Long>? = null,
    chartDescription: String = "",
    change: @Composable (() -> Unit)? = null,
) {
    val colors = CreatoraTheme.colors
    val shape = CreatoraTheme.shapes.radius16
    Column(
        modifier = modifier
            .clip(shape)
            .background(colors.surface)
            .border(CreatoraTheme.dimens.borderWidth, colors.border, shape)
            .padding(CreatoraTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.sm)) {
            IconBadge(icon)
            Text(label, style = CreatoraTheme.typography.caption, color = colors.textMuted, modifier = Modifier.weight(1f), maxLines = 2)
        }
        Text(value, style = CreatoraTheme.typography.screenTitle, color = colors.text, maxLines = 1)
        change?.invoke()
        chart?.let { Sparkline(it, chartDescription) }
    }
}

/** A video's picture at 16:9, or a film icon on a soft background when there is none. */
@Composable
fun VideoThumbnail(url: String?, modifier: Modifier = Modifier) {
    val shape = CreatoraTheme.shapes.radius8
    Box(
        modifier = modifier.size(CreatoraTheme.dimens.thumbnailWidth, CreatoraTheme.dimens.thumbnailWidth * 9 / 16).clip(shape).background(CreatoraTheme.colors.surfaceAlt),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        } else {
            Icon(Icons.Outlined.Movie, contentDescription = null, tint = CreatoraTheme.colors.textMuted)
        }
    }
}

private const val CHIP_ALPHA = 0.14f
private const val AREA_ALPHA = 0.5f
