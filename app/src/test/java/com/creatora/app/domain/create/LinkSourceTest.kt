package com.creatora.app.domain.create

import com.creatora.app.domain.ai.AiTasks
import com.creatora.app.domain.model.AiError
import com.creatora.app.domain.model.AiOutcome
import com.creatora.app.testutil.FakeAiService
import com.creatora.app.testutil.okReply
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkSourceTest {
    @Test
    fun normalizeLink_acceptsOnlyWebLinks() {
        assertEquals("https://youtu.be/abc", normalizeLink(" https://youtu.be/abc "))
        assertEquals("https://example.com/post", normalizeLink("example.com/post"))
        assertEquals("http://example.com", normalizeLink("http://example.com"))
        assertNull(normalizeLink(""))
        assertNull(normalizeLink("two words.com"))
        assertNull(normalizeLink("ftp://example.com/file"))
        assertNull(normalizeLink("javascript:alert(1)"))
        assertNull(normalizeLink("localhost"))
    }

    @Test
    fun pageMeta_prefersOpenGraph_andDecodesEntities_inAnyAttributeOrder() {
        val html = """<html><head><title>Fallback title</title>
            <meta content="Dal &amp; rice in 10 minutes" property="og:title">
            <meta property='og:description' content='A quick &quot;one pot&quot; dinner'>
            <meta name="keywords" content="dal, rice , quick"></head><body>ignored</body></html>"""

        val info = parsePageMeta("https://x.com/v", html)!!

        assertEquals("Dal & rice in 10 minutes", info.title)
        assertEquals("A quick \"one pot\" dinner", info.description)
        assertEquals(listOf("dal", "rice", "quick"), info.tags)
        assertEquals("https://x.com/v", info.url)
    }

    @Test
    fun pageMeta_fallsBackToTheTitleTag_andIsNullWithoutAnyTitle() {
        val info = parsePageMeta("https://x.com", "<head><title> Plain page </title><meta name=\"description\" content=\"About it\"></head>")!!
        assertEquals("Plain page", info.title)
        assertEquals("About it", info.description)
        assertTrue(info.tags.isEmpty())
        assertNull(parsePageMeta("https://x.com", "<head></head><body>nothing</body>"))
    }

    @Test
    fun sharedWordFraction_measuresCopying() {
        assertEquals(1.0, sharedWordFraction("Dal in ten minutes", "dal IN ten minutes"), 0.001)
        assertEquals(0.0, sharedWordFraction("Quick weeknight dinner", "Mountain hiking guide"), 0.001)
        assertEquals(0.0, sharedWordFraction("", "anything here"), 0.001)
        assertTrue(sharedWordFraction("Best dal recipe ever", "My best dal recipe") > COPY_LIMIT)
    }

    private val source = SourceInfo("https://x.com/v", "Best dal recipe ever", "A dal video", listOf("dal"))

    @Test
    fun aNewConcept_isUsed() = runTest {
        val ai = FakeAiService(okReply("CONCEPT: Cooking a pot of lentils on a tight schedule\nANGLE: Beginners with no time\nHOOK: Can you eat well in ten minutes?"))

        val outcome = ConceptExtractor(AiTasks(ai)).extract(source, topic = "quick dinners")

        val concept = (outcome as ConceptOutcome.Ok).concept
        assertEquals("Cooking a pot of lentils on a tight schedule", concept.concept)
        assertEquals("Beginners with no time", concept.angle)
        val request = ai.requests.single()
        assertTrue("Never copy" in request.system)
        assertTrue("Best dal recipe ever" in request.messages.single().text && "quick dinners" in request.messages.single().text)
    }

    @Test
    fun aConceptTooCloseToTheSource_isRetriedStrictly_thenRefused() = runTest {
        val copy = okReply("CONCEPT: The best dal recipe ever\nANGLE: x\nHOOK: y")
        val ai = FakeAiService(copy)

        assertEquals(ConceptOutcome.TooClose, ConceptExtractor(AiTasks(ai)).extract(source, ""))
        assertEquals(2, ai.requests.size)
        assertTrue("too close" in ai.requests[1].system)
    }

    @Test
    fun aSecondTryThatIsOriginal_isAccepted() = runTest {
        val outcomes = listOf(okReply("CONCEPT: Best dal recipe ever\nANGLE: a\nHOOK: b"), okReply("CONCEPT: Lentils for busy evenings\nANGLE: a\nHOOK: b"))
        val sequenced = object : com.creatora.app.domain.repository.AiService {
            var i = 0
            override suspend fun complete(request: com.creatora.app.domain.model.AiRequest) = outcomes[i++]
        }

        val outcome = ConceptExtractor(AiTasks(sequenced)).extract(source, "")

        assertEquals("Lentils for busy evenings", (outcome as ConceptOutcome.Ok).concept.concept)
        assertEquals(2, sequenced.i)
    }

    @Test
    fun failuresAndUnreadableReplies_areReported() = runTest {
        val failed = FakeAiService(AiOutcome.Failed(listOf(AiError.LimitReached)))
        assertEquals(ConceptOutcome.Failed(listOf(AiError.LimitReached)), ConceptExtractor(AiTasks(failed)).extract(source, ""))

        val unreadable = FakeAiService(okReply("I cannot do that."))
        assertEquals(ConceptOutcome.Failed(emptyList()), ConceptExtractor(AiTasks(unreadable)).extract(source, ""))
    }

    @Test
    fun parseBrief_readsTheFieldsAndCapsTheLength() {
        val brief = parseBrief("ANGLE: A calm start\nLENGTH: about 90 seconds\nTONE: warm, quick\nAUDIENCE: busy cooks", maxSeconds = 30)!!

        assertEquals("A calm start", brief.angle)
        assertEquals(30, brief.seconds) // capped at the plan's limit
        assertEquals("warm, quick", brief.tone)
        assertEquals(20, parseBrief("LENGTH: 20", 30)!!.seconds)
        assertNull(parseBrief("LENGTH: soon", 30)!!.seconds)
        assertNull(parseBrief("nothing useful", 30))
    }

    @Test
    fun replaceLine_changesTheFirstMatchInTheRightPart() {
        assertEquals(Triple("New hook.", "Body one. Body two.", "Subscribe."), replaceLine("Old hook.", "Body one. Body two.", "Subscribe.", "Old hook.", "New hook."))
        assertEquals(Triple("Hook.", "Body one. Fresh two.", "Subscribe."), replaceLine("Hook.", "Body one. Body two.", "Subscribe.", "Body two.", "Fresh two."))
        assertEquals(Triple("Hook.", "Body.", "Follow me."), replaceLine("Hook.", "Body.", "Subscribe.", "Subscribe.", "Follow me."))
        assertNull(replaceLine("Hook.", "Body.", "Subscribe.", "Edited meanwhile.", "x"))
        assertNull(replaceLine("Hook.", "Body.", "Subscribe.", " ", "x"))
    }

    @Test
    fun scriptLines_areTheSentences() {
        assertEquals(listOf("One.", "Two?", "Three!"), scriptLines("One. Two?\nThree!"))
    }
}
