package com.creatora.app.domain.ai

import com.creatora.app.domain.account.AppLanguage
import com.creatora.app.domain.account.CreatorProfile
import com.creatora.app.domain.account.Plan
import com.creatora.app.domain.account.monthKey
import com.creatora.app.domain.model.AiError
import com.creatora.app.domain.model.AiOutcome
import com.creatora.app.domain.model.AiRequest
import com.creatora.app.domain.model.ChatMessage
import com.creatora.app.domain.model.ChatRole
import com.creatora.app.domain.repository.AiTone
import com.creatora.app.domain.repository.BillingState
import com.creatora.app.domain.repository.UserDoc
import com.creatora.app.testutil.FakeAiHistoryRepository
import com.creatora.app.testutil.FakeAiService
import com.creatora.app.testutil.FakeAuthService
import com.creatora.app.testutil.FakeBillingRepository
import com.creatora.app.testutil.FakeSettingsRepository
import com.creatora.app.testutil.FakeUsageRepository
import com.creatora.app.testutil.FakeUserRepository
import com.creatora.app.testutil.TestUser
import com.creatora.app.testutil.okReply
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeteredAiServiceTest {
    private val now = 1_800_000_000_000L
    private val month = monthKey(now)
    private val delegate = FakeAiService(okReply("answer"))
    private val auth = FakeAuthService(TestUser)
    private val users = FakeUserRepository().apply { put(UserDoc("uid1", "Asha", "a@x.com", null, Plan.FREE, "en", 1)) }
    private val usage = FakeUsageRepository()
    private val history = FakeAiHistoryRepository()
    private val settings = FakeSettingsRepository()
    private val billing = FakeBillingRepository()
    private val request = AiRequest("Be brief.", listOf(ChatMessage(ChatRole.USER, "Give me ideas")))

    private fun service() = MeteredAiService(delegate, auth, users, usage, history, settings, billing, now = { now })

    @Test
    fun successfulCalls_areCounted_andLogged() = runTest {
        val outcome = service().complete(request)

        assertEquals("answer", (outcome as AiOutcome.Ok).value)
        assertEquals(1, usage.counts.value["uid1/$month"])
        assertEquals("Give me ideas", history.entries.value.single().prompt)
    }

    @Test
    fun failedCalls_areNotCounted() = runTest {
        delegate.outcome = AiOutcome.Failed(listOf(AiError.CloudUnavailable))

        service().complete(request)

        assertEquals(null, usage.counts.value["uid1/$month"])
        assertTrue(history.entries.value.isEmpty())
    }

    @Test
    fun atTheLimit_theAiIsNotCalled() = runTest {
        usage.set("uid1", month, Plan.FREE.aiLimit)

        val outcome = service().complete(request)

        assertEquals(AiOutcome.Failed(listOf(AiError.LimitReached)), outcome)
        assertTrue(delegate.requests.isEmpty())
    }

    @Test
    fun aProPlan_hasTheHigherLimit_evenWhenTheDocumentSaysFree() = runTest {
        usage.set("uid1", month, Plan.FREE.aiLimit)
        billing.current.value = BillingState(available = true, hasPro = true)

        assertTrue(service().complete(request) is AiOutcome.Ok)
    }

    @Test
    fun signedOut_asksToSignIn() = runTest {
        auth.current.value = null
        assertEquals(AiOutcome.Failed(listOf(AiError.SignInRequired)), service().complete(request))
    }

    @Test
    fun theRequestCarriesLanguageToneAndProfile_whenPersonalizationIsOn() = runTest {
        settings.language.value = AppLanguage.HINGLISH
        settings.aiTone.value = AiTone.FUNNY
        users.put(users.docs.value.getValue("uid1").copy(creatorProfile = CreatorProfile("street food", listOf("shorts"), "Hindi", "Short food films.")))

        service().complete(request)

        val system = delegate.requests.single().system
        assertTrue(system.startsWith("Be brief."))
        assertTrue("Hinglish" in system && "funny" in system.lowercase() && "street food" in system && "Short food films." in system)
        assertTrue("Never invent numbers" in system)
    }

    @Test
    fun personalizationOff_keepsTheProfileOut() = runTest {
        settings.personalization.value = false
        users.put(users.docs.value.getValue("uid1").copy(creatorProfile = CreatorProfile("street food")))

        service().complete(request)

        assertFalse("street food" in delegate.requests.single().system)
    }
}
