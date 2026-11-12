package com.creatora.app.domain.account

import com.creatora.app.domain.ai.AiTasks
import com.creatora.app.domain.ai.parseCreatorProfile
import com.creatora.app.domain.model.AiOutcome
import com.creatora.app.domain.repository.BillingState
import com.creatora.app.domain.repository.UserDoc
import com.creatora.app.testutil.FakeAiService
import com.creatora.app.testutil.FakeBillingRepository
import com.creatora.app.testutil.FakeConnectedAccountRepository
import com.creatora.app.testutil.FakePlatformAdapter
import com.creatora.app.testutil.FakeUserRepository
import com.creatora.app.testutil.okReply
import com.creatora.app.testutil.preview
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountConnectorTest {
    private val youtube = FakePlatformAdapter()
    private val accounts = FakeConnectedAccountRepository()
    private val users = FakeUserRepository().apply { put(UserDoc("u", "A", "a@x.com", null, Plan.FREE, "en", 1)) }
    private val billing = FakeBillingRepository()
    private val connector = AccountConnector(PlatformRegistry(listOf(youtube)), accounts, users, billing)

    @Test
    fun lookup_ofALinkOnlyPlatform_savesNothingYet_butSaysSo() = runTest {
        val result = connector.lookup("https://sharechat.com/profile/maya", null)
        assertEquals(PlatformSupport.LINK_ONLY, (result as LookupResult.LinkOnly).ref.platform.support)
        assertTrue(youtube.lookups.isEmpty())
    }

    @Test
    fun lookup_ofAPlatformWithoutAnAdapterHere_failsWithAReason_andNeverSaysComingSoon() = runTest {
        val result = connector.lookup("https://instagram.com/maya", null)
        assertTrue((result as LookupResult.Failed).error is AdapterError.NotAvailable)
    }

    @Test
    fun lookup_ofYouTube_returnsThePreview_orTheAdapterError() = runTest {
        youtube.lookupResult = AdapterOutcome.Ok(preview())
        assertEquals(LookupResult.Found(preview()), connector.lookup("@mine", null))
        assertEquals("@mine", youtube.lookups.single().handleOrId)

        youtube.lookupResult = AdapterOutcome.Failed(AdapterError.NotFound)
        assertEquals(LookupResult.Failed(AdapterError.NotFound), connector.lookup("@nobody", null))
        assertEquals(LookupResult.Unrecognised, connector.lookup("some words", null))
    }

    @Test
    fun connect_respectsThePlanLimit() = runTest {
        assertEquals(ConnectOutcome.Connected, connector.connect("u", preview("UC1").toAccount()))
        assertEquals(ConnectOutcome.AlreadyConnected, connector.connect("u", preview("UC1").toAccount()))
        assertEquals(ConnectOutcome.PlanLimit(1), connector.connect("u", preview("UC2").toAccount()))
        assertEquals(1, accounts.observe("u").first().size)
    }

    @Test
    fun connect_allowsMoreOnPro_fromTheDocumentOrFromPlayPurchases() = runTest {
        users.put(users.docs.value.getValue("u").copy(plan = Plan.PRO))
        assertEquals(ConnectOutcome.Connected, connector.connect("u", preview("UC1").toAccount()))
        assertEquals(ConnectOutcome.Connected, connector.connect("u", preview("UC2").toAccount()))

        users.put(users.docs.value.getValue("u").copy(plan = Plan.FREE))
        accounts.byUser.value = emptyMap()
        billing.current.value = BillingState(hasPro = true)
        assertEquals(ConnectOutcome.Connected, connector.connect("u", preview("UC1").toAccount()))
        assertEquals(ConnectOutcome.Connected, connector.connect("u", preview("UC3").toAccount()))
    }

    @Test
    fun creatorProfileService_derivesFromTheChannelText_andStoresIt() = runTest {
        accounts.save("u", preview().toAccount())
        youtube.videos = AdapterOutcome.Ok(listOf(VideoStats("v1", "Best street food in Delhi", "2026-01-01", 10)))
        val ai = FakeAiService(okReply("NICHE: street food\nFORMATS: shorts, long videos\nLANGUAGE: Hindi\nSUMMARY: Food tours of Delhi."))
        val service = CreatorProfileService(PlatformRegistry(listOf(youtube)), accounts, users, AiTasks(ai))

        val result = service.deriveFor("u")

        val profile = (result as DeriveResult.Updated).profile
        assertEquals(listOf("shorts", "long videos"), profile.formats)
        assertEquals(profile, users.docs.value.getValue("u").creatorProfile)
        val prompt = ai.requests.single().messages.single().text
        assertTrue("Best street food in Delhi" in prompt && "I make short films" in prompt)
    }

    @Test
    fun creatorProfileService_doesNotStoreAGuess() = runTest {
        accounts.save("u", preview().toAccount())
        val service = CreatorProfileService(PlatformRegistry(listOf(youtube)), accounts, users, AiTasks(FakeAiService(okReply("NICHE: unknown"))))

        assertEquals(DeriveResult.NotEnoughData, service.deriveFor("u"))
        assertNull(users.docs.value.getValue("u").creatorProfile)

        assertEquals(DeriveResult.NothingConnected, service.deriveFor("nobody"))
    }

    @Test
    fun parseCreatorProfile_needsANiche() {
        assertNull(parseCreatorProfile("SUMMARY: only a summary"))
        assertNull(parseCreatorProfile("NICHE:   "))
        assertEquals("gaming", parseCreatorProfile("NICHE: gaming")?.niche)
    }

    @Test
    fun profileSamples_useOnlyRealText() {
        val samples = profileSamples(listOf(preview()), listOf(VideoStats("v", "A title", "", 1), VideoStats("w", " ", "", 1)))
        assertEquals(listOf("My channel: I make short films", "A title"), samples)
    }
}
