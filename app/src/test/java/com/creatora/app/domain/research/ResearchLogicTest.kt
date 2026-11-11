package com.creatora.app.domain.research

import com.creatora.app.ui.research.compactCount
import com.creatora.app.ui.research.multipleText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResearchLogicTest {
    private fun video(id: String, channel: String, views: Long) = YtVideo(id, "T$id", channel, "C$channel", views, null)
    private fun channel(id: String, views: Long, videos: Long) = YtChannel(id, "C$id", 1_000, views, videos, null)

    @Test
    fun competition_followsTheNumberOfMatchingVideos() {
        assertEquals(Competition.LOW, competitionFor(0))
        assertEquals(Competition.LOW, competitionFor(9_999))
        assertEquals(Competition.MEDIUM, competitionFor(10_000))
        assertEquals(Competition.MEDIUM, competitionFor(199_999))
        assertEquals(Competition.HIGH, competitionFor(200_000))
        assertEquals(Competition.HIGH, competitionFor(1_000_000))
    }

    @Test
    fun outliers_areVideosWellAboveTheirChannelsAverage_bestFirst() {
        val channels = listOf(channel("a", views = 1_000_000, videos = 100), channel("b", views = 500, videos = 5))
        val videos = listOf(
            video("v1", "a", 25_000), // 2.5x of 10,000
            video("v2", "a", 15_000), // 1.5x: not an outlier
            video("v3", "b", 1_000), // 10x of 100
            video("v4", "unknown", 999_999), // channel not known
            video("v5", "a", 100_000), // 10x
        )

        val outliers = findOutliers(videos, channels)

        assertEquals(listOf("v3", "v5", "v1"), outliers.map { it.video.id }.sortedBy { listOf("v3", "v5", "v1").indexOf(it) })
        assertEquals(10.0, outliers.first().multiple, 0.0001)
        assertEquals(listOf(10.0, 10.0, 2.5), outliers.map { it.multiple })
        assertEquals(10_000L, outliers.last().channelAverage)
    }

    @Test
    fun outliers_ignoreChannelsWithNoVideosAndRespectTheMinimum() {
        val channels = listOf(channel("a", views = 0, videos = 0), channel("b", views = 1_000, videos = 10))
        val videos = listOf(video("v1", "a", 5_000), video("v2", "b", 150))

        assertEquals(emptyList<Outlier>(), findOutliers(videos, channels))
        assertEquals(listOf("v2"), findOutliers(videos, channels, minMultiple = 1.5).map { it.video.id })
    }

    @Test
    fun parseVideoId_readsLinksAndBareIds() {
        val id = "dQw4w9WgXcQ"
        listOf(
            id,
            " $id ",
            "https://youtu.be/$id",
            "https://youtu.be/$id?t=42",
            "https://www.youtube.com/watch?v=$id",
            "https://www.youtube.com/watch?feature=share&v=$id&t=3",
            "https://youtube.com/shorts/$id",
            "https://www.youtube.com/embed/$id",
            "https://m.youtube.com/live/$id",
        ).forEach { assertEquals(it, id, parseVideoId(it)) }
        listOf("", "hello", "https://example.com/watch?v=$id", "tooshort").forEach { assertNull(it, parseVideoId(it)) }
    }

    @Test
    fun parseChannelQuery_tellsIdsHandlesAndNamesApart() {
        val id = "UC" + "a".repeat(22)
        assertEquals(ChannelQuery.Id(id), parseChannelQuery(id))
        assertEquals(ChannelQuery.Id(id), parseChannelQuery("https://www.youtube.com/channel/$id"))
        assertEquals(ChannelQuery.Handle("@creator.name"), parseChannelQuery("@creator.name"))
        assertEquals(ChannelQuery.Handle("@creator"), parseChannelQuery("https://www.youtube.com/@creator"))
        assertEquals(ChannelQuery.Name("Some Channel"), parseChannelQuery("  Some Channel "))
    }

    @Test
    fun channelAverage_isViewsPerVideo() {
        assertEquals(500L, channel("a", 5_000, 10).averageViews)
        assertEquals(0L, channel("a", 5_000, 0).averageViews)
    }

    @Test
    fun compactCount_andMultipleText_areShort() {
        assertEquals("999", compactCount(999))
        assertEquals("1K", compactCount(1_000))
        assertEquals("1.5K", compactCount(1_499))
        assertEquals("12.3M", compactCount(12_340_000))
        assertEquals("2B", compactCount(2_000_000_000))
        assertEquals("2.5", multipleText(2.5))
        assertEquals("3", multipleText(3.0))
        assertEquals("12", multipleText(12.7))
    }
}
