package com.creatora.app.domain.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderPlanTest {
    private fun image(name: String) = SceneSource("file:///$name.jpg", isVideo = false, durationMs = null)
    private fun video(name: String, ms: Long?) = SceneSource("file:///$name.mp4", isVideo = true, durationMs = ms)

    @Test
    fun splitSentences_splitsOnEndersAndNewlinesAndKeepsAbbreviationsOfNumbers() {
        assertEquals(
            listOf("Hello there.", "How are you?", "Great!", "New line here", "यह हिंदी है।", "अगला वाक्य"),
            splitSentences("Hello there. How are you? Great!\nNew line here\n\nयह हिंदी है। अगला वाक्य"),
        )
        assertEquals(listOf("Version 3.5 is out"), splitSentences("Version 3.5 is out"))
        assertTrue(splitSentences(" \n ").isEmpty())
    }

    @Test
    fun buildCues_followsTheNarrationBackToBack() {
        val clips = listOf(NarrationClip("a", 2_000_000), NarrationClip("b", 3_500_000))

        assertEquals(
            listOf(CaptionCue(0, 2_000_000, "One."), CaptionCue(2_000_000, 5_500_000, "Two.")),
            buildCues(listOf("One.", "Two."), clips),
        )
    }

    @Test
    fun planScenes_alwaysAddsUpToTheNarrationLength() {
        for (total in listOf(1_000_000L, 3_999_999L, 4_000_000L, 7_300_000L, 23_456_789L, 61_000_000L)) {
            val scenes = planScenes(listOf(image("a"), image("b"), video("c", 2_500)), total)
            assertEquals("total $total", total, scenes.sumOf { it.durationUs })
            assertTrue(scenes.all { it.durationUs > 0 })
        }
    }

    @Test
    fun planScenes_usesAboutFourSecondsAndCyclesThroughTheSources() {
        val scenes = planScenes(listOf(image("a"), image("b")), 12_000_000)

        assertEquals(listOf("a", "b", "a"), scenes.map { it.uri.substringAfterLast('/').substringBefore('.') })
        assertEquals(listOf(4_000_000L, 4_000_000L, 4_000_000L), scenes.map { it.durationUs })
    }

    @Test
    fun planScenes_doesNotLeaveATinyLastScene() {
        val scenes = planScenes(listOf(image("a")), 8_500_000)

        assertEquals(listOf(4_000_000L, 4_500_000L), scenes.map { it.durationUs })
    }

    @Test
    fun planScenes_showsAShortVideoWholeThenMovesOn() {
        val scenes = planScenes(listOf(video("clip", 1_500), image("pic")), 6_000_000)

        assertEquals(1_500_000L, scenes[0].durationUs)
        assertTrue(scenes[0].isVideo)
        assertFalse(scenes[1].isVideo)
        assertEquals(6_000_000L, scenes.sumOf { it.durationUs })
    }

    @Test
    fun planScenes_cutsALongVideoToItsSlot() {
        val scenes = planScenes(listOf(video("long", 60_000)), 10_000_000)

        assertTrue(scenes.all { it.durationUs <= 6_000_000 })
        assertEquals(10_000_000L, scenes.sumOf { it.durationUs })
    }

    @Test
    fun planScenes_skipsVideosWithNoUsableLengthAndHandlesNothingToShow() {
        assertTrue(planScenes(listOf(video("bad", null), video("tiny", 100)), 5_000_000).isEmpty())
        assertTrue(planScenes(emptyList(), 5_000_000).isEmpty())
        assertTrue(planScenes(listOf(image("a")), 0).isEmpty())
        assertEquals(listOf("pic"), planScenes(listOf(video("bad", null), image("pic")), 2_000_000).map { it.uri.substringAfterLast('/').substringBefore('.') })
    }

    @Test
    fun captionZones_stayInsideTheFrameWithSideMargins() {
        AspectRatio.entries.forEach { ratio ->
            val zone = ratio.captionZone()
            assertTrue(ratio.name, zone.left >= 0.05f && zone.right <= 0.95f)
            assertTrue(ratio.name, zone.top < zone.bottom && zone.bottom <= 0.9f)
        }
        // Vertical video keeps captions out of the bottom fifth, where platform buttons sit.
        assertTrue(AspectRatio.PORTRAIT.captionZone().bottom <= 0.8f)
    }

    @Test
    fun renderFileName_isSafeAndShowsTheShape() {
        assertEquals("My-first-video-9x16-1.mp4", renderFileName("  My first / video!  ", AspectRatio.PORTRAIT, "1"))
        assertEquals("creatora-16x9-x.mp4", renderFileName("***", AspectRatio.LANDSCAPE, "x"))
        assertEquals("हिंदी-1x1-t.mp4", renderFileName("हिंदी", AspectRatio.SQUARE, "t"))
        assertEquals(40, renderFileName("a".repeat(100), AspectRatio.SQUARE, "t").substringBefore("-1x1").length)
    }

    @Test
    fun outputSizes_are1080OnTheShortSide() {
        assertEquals(1080 to 1920, AspectRatio.PORTRAIT.width to AspectRatio.PORTRAIT.height)
        assertEquals(1920 to 1080, AspectRatio.LANDSCAPE.width to AspectRatio.LANDSCAPE.height)
        assertEquals(1080 to 1080, AspectRatio.SQUARE.width to AspectRatio.SQUARE.height)
    }
}
