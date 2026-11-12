package com.creatora.app.ui.home

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.account.ConnectedAccount
import com.creatora.app.domain.account.VideoAnalytics
import com.creatora.app.domain.account.VideoStats
import com.creatora.app.domain.home.AccountSelection
import com.creatora.app.domain.home.Change
import com.creatora.app.domain.home.Snapshot
import com.creatora.app.domain.home.Streak
import com.creatora.app.domain.home.StreakUnit
import com.creatora.app.domain.home.UploadRow
import com.creatora.app.domain.home.UploadStatus
import com.creatora.app.ui.theme.CreatoraTheme

// Sample data for previews and the screenshot test only. It lives in the debug source set, so none of it is in the release app.

private fun video(id: String, title: String, views: Long) = VideoStats(id, title, "2026-10-20T10:00:00Z", views)

/** A Home screen with one connected channel and its numbers, to look at the layout in light and dark. */
fun sampleHomeModel(): HomeModel = HomeModel(
    home = HomeUiState(
        firstName = "Asha",
        accounts = listOf(ConnectedAccount(ConnectPlatform.YOUTUBE, "UC1", "Asha Codes", subscribers = 1_240)),
        selection = AccountSelection.One("YOUTUBE_UC1"),
    ),
    cards = ChannelCardsState(
        phase = CardsPhase.READY,
        accountCount = 1,
        snapshot = Snapshot(
            subscribers = 1_240,
            views = Change(48_200.0, 40_100.0),
            watchHours = Change(310.0, 340.0),
            subscribersNet = Change(120.0, 80.0),
            earningsUsd = null,
            viewsSeries = listOf(900L, 1_200, 800, 1_500, 2_100, 1_700, 1_900, 2_400, 1_800, 2_200, 2_600, 2_100, 1_600, 1_900, 2_800, 3_100, 2_700, 2_300, 2_500, 2_900, 3_300, 2_800, 2_600, 3_000, 3_400, 3_100, 2_900, 3_500),
        ),
        uploads = listOf(
            UploadRow(video("a", "How I learned to code in 30 days", 12_400), UploadStatus.GOOD, 140),
            UploadRow(video("b", "5 mistakes every beginner makes", 4_100), UploadStatus.AVERAGE, 90),
            UploadRow(video("c", "My desk setup tour", 700), UploadStatus.POOR, 40),
        ),
        uploadMetrics = mapOf("a" to VideoAnalytics("a", ctrPercent = 6.4, averageViewPercent = 58.0), "b" to VideoAnalytics("b", averageViewPercent = 41.0)),
        streak = Streak(current = 3, best = 7, atRisk = true, unit = StreakUnit.WEEKS),
    ),
    focus = FocusState.NotEnoughData,
    ideas = IdeasState.NotEnoughData,
)

@Preview(name = "Home ready, light", showBackground = true, heightDp = 1800)
@Composable
private fun HomeReadyLightPreview() {
    CreatoraTheme(darkTheme = false) { HomeContent(sampleHomeModel(), HomeActions()) }
}

@Preview(name = "Home ready, dark", showBackground = true, heightDp = 1800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeReadyDarkPreview() {
    CreatoraTheme(darkTheme = true) { HomeContent(sampleHomeModel(), HomeActions()) }
}
