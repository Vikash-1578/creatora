package com.creatora.app.ui.navigation

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.creatora.app.ui.theme.CreatoraTheme

private const val CREATE_PRESSED_SCALE = 0.94f

/**
 * Bottom tab bar. The bar is [CreatoraDimens.bottomBarHeight] tall plus the system navigation
 * bar inset. Above it is a transparent strip (lift + ring) that only the raised Create button
 * uses; content behind that strip stays visible.
 */
@Composable
fun CreatoraBottomBar(
    selectedTab: TopLevelTab?,
    onTabSelected: (TopLevelTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CreatoraTheme.colors
    val dimens = CreatoraTheme.dimens
    val overhang = bottomBarOverhang()

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(overhang + dimens.bottomBarHeight)
                .drawBehind {
                    val top = overhang.toPx()
                    drawRect(
                        color = colors.navBar,
                        topLeft = Offset(0f, top),
                        size = Size(size.width, size.height - top),
                    )
                    drawRect(
                        color = colors.border,
                        topLeft = Offset(0f, top),
                        size = Size(size.width, dimens.borderWidth.toPx()),
                    )
                }
                .selectableGroup(),
        ) {
            TopLevelTab.entries.forEach { tab ->
                val itemModifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                if (tab.isRaised) {
                    RaisedTabItem(tab, tab == selectedTab, { onTabSelected(tab) }, itemModifier)
                } else {
                    TabItem(tab, tab == selectedTab, { onTabSelected(tab) }, itemModifier)
                }
            }
        }
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.navBar)
                .windowInsetsBottomHeight(WindowInsets.navigationBars),
        )
    }
}

/** Height of the transparent strip above the bar: how far the Create button and its ring stick up. */
@Composable
private fun bottomBarOverhang(): Dp =
    CreatoraTheme.dimens.createButtonLift + CreatoraTheme.dimens.createButtonRing

/** Distance from the top of the tab row (including the overhang) to the top of every label. */
@Composable
private fun labelTop(): Dp =
    bottomBarOverhang() + CreatoraTheme.spacing.md + CreatoraTheme.dimens.icon + CreatoraTheme.spacing.xs

@Composable
private fun TabItem(
    tab: TopLevelTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(tab.contentDescription)
    val tint = tabColor(selected)
    Column(
        modifier = modifier
            // The overhang strip above the bar is not part of the touch area.
            .padding(top = bottomBarOverhang())
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .semantics { contentDescription = description }
            .padding(top = CreatoraTheme.spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CreatoraTheme.spacing.xs),
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(CreatoraTheme.dimens.icon),
        )
        TabLabel(tab = tab, selected = selected)
    }
}

/** The Create tab: accent circle with a plus, ringed in the background colour, raised above the bar. */
@Composable
private fun RaisedTabItem(
    tab: TopLevelTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CreatoraTheme.colors
    val dimens = CreatoraTheme.dimens
    val description = stringResource(tab.contentDescription)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val ringDiameter = dimens.createButtonSize + dimens.createButtonRing * 2

    Column(
        modifier = modifier
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = null,
            )
            .semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(ringDiameter)
                .scale(if (pressed) CREATE_PRESSED_SCALE else 1f)
                .clip(CircleShape)
                .background(colors.background)
                .padding(dimens.createButtonRing)
                .clip(CircleShape)
                .background(colors.accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = null,
                tint = colors.onAccent,
                modifier = Modifier.size(dimens.icon),
            )
        }
        // Line the label up with the labels of the other tabs.
        Spacer(modifier = Modifier.height(labelTop() - ringDiameter))
        TabLabel(tab = tab, selected = selected)
    }
}

@Composable
private fun TabLabel(tab: TopLevelTab, selected: Boolean) {
    Text(
        text = stringResource(tab.label),
        style = CreatoraTheme.typography.tabLabel.copy(
            fontWeight = if (selected) FontWeight.Bold else CreatoraTheme.typography.tabLabel.fontWeight,
        ),
        color = tabColor(selected),
        maxLines = 1,
        // The tab's content description is read instead.
        modifier = Modifier.clearAndSetSemantics {},
    )
}

@Composable
private fun tabColor(selected: Boolean): Color =
    if (selected) CreatoraTheme.colors.accentText else CreatoraTheme.colors.textMuted

@Preview(name = "Light", showBackground = true)
@Composable
private fun BottomBarLightPreview() {
    CreatoraTheme(darkTheme = false) {
        CreatoraBottomBar(selectedTab = TopLevelTab.HOME, onTabSelected = {})
    }
}

@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BottomBarDarkPreview() {
    CreatoraTheme(darkTheme = true) {
        CreatoraBottomBar(selectedTab = TopLevelTab.CREATE, onTabSelected = {})
    }
}
