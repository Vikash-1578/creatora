package com.creatora.app.domain.ai

import com.creatora.app.domain.model.AiError
import com.creatora.app.domain.model.AiOutcome
import com.creatora.app.domain.model.ChatMessage
import com.creatora.app.domain.model.ChatRole
import com.creatora.app.testutil.FakeAiService
import com.creatora.app.testutil.okReply
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTasksTest {
    @Test
    fun parseIdeas_readsBulletsAndNumbersAndStripsMarkers() {
        val text = "Here you go:\n- **Cheap gear tour**\n* \"Studio on a budget\"\n3. Editing in 10 minutes\n- Cheap gear tour"
        assertEquals(listOf("Cheap gear tour", "Studio on a budget", "Editing in 10 minutes"), parseIdeas(text, max = 5))
    }

    @Test
    fun parseIdeas_fallsBackToLinesAndRespectsMax() {
        assertEquals(listOf("One", "Two"), parseIdeas("One\n\nTwo\nThree", max = 2))
        assertTrue(parseIdeas("  \n ", max = 3).isEmpty())
    }

    @Test
    fun parseScript_splitsOnMarkersIgnoringCaseAndBold() {
        val draft = parseScript("Sure!\n**HOOK:** Stop scrolling.\nHere is why.\nstructure: Point one.\nPoint two.\nCTA: Follow for more.")
        assertEquals("Stop scrolling.\nHere is why.", draft.hook)
        assertEquals("Point one.\nPoint two.", draft.structure)
        assertEquals("Follow for more.", draft.callToAction)
    }

    @Test
    fun parseScript_withoutMarkersKeepsTheWholeReplyAsTheBody() {
        val draft = parseScript("Just a plain script.")
        assertEquals("", draft.hook)
        assertEquals("Just a plain script.", draft.structure)
        assertEquals("", draft.callToAction)
    }

    @Test
    fun parsePublishText_readsAllFourSections() {
        val text = """
            TITLES:
            - First title
            - Second title
            DESCRIPTION: A short description.
            HASHTAGS: #one #two, #one nothashtag
            CHAPTERS:
            00:00 Intro
            - 01:10 Main part
        """.trimIndent()

        val result = parsePublishText(text)

        assertEquals(listOf("First title", "Second title"), result.titles)
        assertEquals("A short description.", result.description)
        assertEquals(listOf("#one", "#two"), result.hashtags)
        assertEquals(listOf("00:00 Intro", "01:10 Main part"), result.chapters)
    }

    @Test
    fun coach_sendsTheConversationWithTheCoachInstructions() = runTest {
        val service = FakeAiService(okReply("Post weekly."))
        val history = listOf(ChatMessage(ChatRole.USER, "How often?"))

        val outcome = AiTasks(service).coach(history)

        assertEquals("Post weekly.", (outcome as AiOutcome.Ok).value)
        assertEquals(history, service.requests.single().messages)
        assertEquals(AiTasks.COACH_SYSTEM, service.requests.single().system)
    }

    @Test
    fun ideas_asksForTheTopicAndParsesTheReply() = runTest {
        val service = FakeAiService(okReply("- A\n- B"))

        val outcome = AiTasks(service).ideas("woodworking")

        assertEquals(listOf("A", "B"), (outcome as AiOutcome.Ok).value)
        assertTrue(service.requests.single().messages.single().text.contains("woodworking"))
    }

    @Test
    fun scriptDraft_putsTitleBriefAndFormatInThePrompt() = runTest {
        val service = FakeAiService(okReply("HOOK: h\nSTRUCTURE: s\nCTA: c"))

        val outcome = AiTasks(service).scriptDraft("My title", "My brief", "long video")

        assertEquals("c", (outcome as AiOutcome.Ok).value.callToAction)
        val prompt = service.requests.single().messages.single().text
        assertTrue("My title" in prompt && "My brief" in prompt && "long video" in prompt)
    }

    @Test
    fun replyDraft_asksForTheToneAndStripsQuotes() = runTest {
        val service = FakeAiService(okReply("  \"Thank you!\"  "))

        val outcome = AiTasks(service).replyDraft("Loved it", "friendly")

        assertEquals("Thank you!", (outcome as AiOutcome.Ok).value)
        assertTrue("friendly" in service.requests.single().system)
        assertTrue("Loved it" in service.requests.single().messages.single().text)
    }

    @Test
    fun nextTopic_readsTopicAndReason_orKeepsTheWholeReply() = runTest {
        val parsed = AiTasks(FakeAiService(okReply("TOPIC: A new idea\nWHY: Because."))).nextTopic("Chan", "stats", listOf("Top (10 views)"), listOf("A comment"))
        assertEquals(NextTopic("A new idea", "Because."), (parsed as AiOutcome.Ok).value)

        val plain = AiTasks(FakeAiService(okReply("Just make a vlog"))).nextTopic("Chan", "", emptyList(), emptyList())
        assertEquals(NextTopic("Just make a vlog", ""), (plain as AiOutcome.Ok).value)
    }

    @Test
    fun errors_passThroughUnchanged() = runTest {
        val failed = AiOutcome.Failed(listOf(AiError.NoKey))

        assertEquals(failed, AiTasks(FakeAiService(failed)).publishText("t", "script"))
    }
}
