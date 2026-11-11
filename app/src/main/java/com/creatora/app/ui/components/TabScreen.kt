package com.creatora.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.creatora.app.ui.theme.CreatoraTheme

/**
 * Root layout for the five tab screens. Pads for the status bar; the bottom inset is already
 * consumed by the tab bar container in CreatoraApp. Ends with [tabBottomSpace] so the last item can
 * scroll clear of the raised Create button.
 */
@Composable
fun TabScreen(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CreatoraTheme.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState()),
    ) {
        content()
        Spacer(Modifier.height(tabBottomSpace()))
    }
}

/** Room to keep under the last item of a tab screen: the Create button's overhang plus a margin. */
@Composable
fun tabBottomSpace() = CreatoraTheme.dimens.createButtonLift + CreatoraTheme.dimens.createButtonRing + CreatoraTheme.spacing.xxl
