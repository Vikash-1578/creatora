package com.creatora.app.ui.settings

import com.creatora.app.domain.account.AccountConnector
import com.creatora.app.domain.account.AdapterOutcome
import com.creatora.app.domain.account.AppLanguage
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.account.CreatorProfile
import com.creatora.app.domain.account.CreatorProfileService
import com.creatora.app.domain.account.NotificationCategory
import com.creatora.app.domain.account.Plan
import com.creatora.app.domain.account.PlatformRegistry
import com.creatora.app.domain.account.toAccount
import com.creatora.app.domain.ai.AiTasks
import com.creatora.app.domain.repository.AiTone
import com.creatora.app.domain.repository.BillingState
import com.creatora.app.domain.repository.DeleteOutcome
import com.creatora.app.domain.repository.LaunchOutcome
import com.creatora.app.domain.repository.UserDoc
import com.creatora.app.testutil.FakeAiHistoryRepository
import com.creatora.app.testutil.FakeAiService
import com.creatora.app.testutil.FakeAuthService
import com.creatora.app.testutil.FakeBillingRepository
import com.creatora.app.testutil.FakeConnectedAccountRepository
import com.creatora.app.testutil.FakeNotificationPrefs
import com.creatora.app.testutil.FakePlatformAdapter
import com.creatora.app.testutil.FakeSettingsRepository
import com.creatora.app.testutil.FakeUsageRepository
import com.creatora.app.testutil.FakeUserRepository
import com.creatora.app.testutil.MainDispatcherRule
import com.creatora.app.testutil.TestUser
import com.creatora.app.testutil.keepActive
import com.creatora.app.testutil.okReply
import com.creatora.app.testutil.preview
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsViewModelsTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val auth = FakeAuthService(TestUser)
    private val users = FakeUserRepository().apply { put(UserDoc("uid1", "Asha", "a@x.com", null, Plan.FREE, "en", 1)) }
    private val settings = FakeSettingsRepository()
    private val accounts = FakeConnectedAccountRepository()
    private val youtube = FakePlatformAdapter()
    private val billing = FakeBillingRepository()
    private val registry = PlatformRegistry(listOf(youtube))

    // ---- account
    @Test
    fun signOut_resetsOnboarding_soTheNextSignInStartsAgain() = runTest {
        settings.onboardingDone.value = true
        val vm = AccountViewModel(auth, users, settings)
        keepActive(vm.state)
        assertEquals("Asha Rao", vm.state.value.user?.name)

        vm.signOut()

        assertFalse(settings.onboardingDone.value)
        assertNull(auth.current.value)
    }

    @Test
    fun deleteAccount_removesTheCloudDataThenTheSignIn() = runTest {
        settings.onboardingDone.value = true
        val vm = AccountViewModel(auth, users, settings)
        keepActive(vm.state)

        vm.askDelete(true)
        assertTrue(vm.state.value.confirmDelete)
        vm.deleteAccount()

        assertEquals(listOf("uid1"), users.deleted)
        assertNull(auth.current.value)
        assertFalse(settings.onboardingDone.value)
        assertFalse(vm.state.value.working)
    }

    @Test
    fun deleteAccount_asksForAFreshSignIn_whenGoogleDemandsIt() = runTest {
        auth.deleteOutcome = DeleteOutcome.NEEDS_RECENT_SIGN_IN
        val vm = AccountViewModel(auth, users, settings)
        keepActive(vm.state)

        vm.deleteAccount()

        assertTrue(vm.state.value.needsRecentSignIn)
        assertEquals(TestUser, auth.current.value) // still signed in
    }

    @Test
    fun deleteAccount_reportsAFailure() = runTest {
        auth.deleteOutcome = DeleteOutcome.FAILED
        val vm = AccountViewModel(auth, users, settings)
        keepActive(vm.state)

        vm.deleteAccount()

        assertTrue(vm.state.value.deleteFailed)
    }

    // ---- connected accounts
    private fun connected() = ConnectedAccountsViewModel(
        auth, users, accounts, AccountConnector(registry, accounts, users, billing),
        CreatorProfileService(registry, accounts, users, AiTasks(FakeAiService(okReply("NICHE: unknown")))),
    ).also { }

    @Test
    fun connectedAccounts_listsRemovesAndShowsThePlanLimit() = runTest {
        accounts.save("uid1", preview("UC1").toAccount())
        val vm = connected()
        keepActive(vm.state)
        assertEquals(listOf("UC1"), vm.state.value.accounts.map { it.id })
        assertEquals(1, vm.state.value.plan.maxAccounts)

        vm.confirm(preview("UC2", "Second"))
        assertEquals(1, vm.state.value.lookup.planLimitMax)

        vm.remove(vm.state.value.accounts.single())
        assertTrue(vm.state.value.accounts.isEmpty())
    }

    @Test
    fun connectedAccounts_offersTheChannelsOnTheGoogleAccount() = runTest {
        youtube.own = AdapterOutcome.Ok(listOf(preview("UC1")))
        val vm = connected()
        keepActive(vm.state)

        vm.loadOwn()

        assertEquals(listOf("UC1"), vm.state.value.ownChannels.map { it.id })
        assertFalse(vm.state.value.loadingOwn)
    }

    @Test
    fun connectedAccounts_lookUpOfALinkSavesIt() = runTest {
        val vm = connected()
        keepActive(vm.state)

        vm.lookUp("https://snapchat.com/add/maya", ConnectPlatform.SNAPCHAT)

        assertTrue(vm.state.value.lookup.linkSaved)
        assertEquals(ConnectPlatform.SNAPCHAT, vm.state.value.accounts.single().platform)
    }

    // ---- notifications
    @Test
    fun notificationSettings_saveTogglesAndTheMilestone() = runTest {
        val prefs = FakeNotificationPrefs()
        val vm = NotificationSettingsViewModel(prefs)
        keepActive(vm.prefs)

        vm.toggle(NotificationCategory.EARNINGS, false)
        vm.setMilestone(10_000)

        assertFalse(vm.prefs.value.isOn(NotificationCategory.EARNINGS))
        assertEquals(10_000L, vm.prefs.value.milestoneViews)
    }

    // ---- Creatora AI
    @Test
    fun aiSettings_toneAndPersonalizationAreSaved_andTheProfileAndHistoryShow() = runTest {
        users.put(users.docs.value.getValue("uid1").copy(creatorProfile = CreatorProfile("street food")))
        val history = FakeAiHistoryRepository()
        history.add("uid1", "Give me ideas", 1)
        val vm = AiSettingsViewModel(settings, auth, users, history, CreatorProfileService(registry, accounts, users, AiTasks(FakeAiService(okReply("NICHE: unknown")))))
        keepActive(vm.state)

        vm.setTone(AiTone.PROFESSIONAL)
        vm.setPersonalization(false)

        assertEquals(AiTone.PROFESSIONAL, vm.state.value.tone)
        assertFalse(vm.state.value.personalization)
        assertEquals("street food", vm.state.value.profile?.niche)
        assertEquals(listOf("Give me ideas"), vm.state.value.history.map { it.prompt })

        vm.clearHistory()
        assertTrue(vm.state.value.history.isEmpty())
    }

    @Test
    fun aiSettings_refreshingTheProfileTellsWhatHappened() = runTest {
        val history = FakeAiHistoryRepository()
        val vm = AiSettingsViewModel(settings, auth, users, history, CreatorProfileService(registry, accounts, users, AiTasks(FakeAiService(okReply("NICHE: unknown")))))
        keepActive(vm.state)

        vm.refreshProfile()

        assertEquals(com.creatora.app.domain.account.DeriveResult.NothingConnected, vm.state.value.refreshMessage)
        assertFalse(vm.state.value.refreshing)
    }

    // ---- billing
    @Test
    fun billing_showsPlanUsageAndWhetherUpgradeIsPossible() = runTest {
        val usage = FakeUsageRepository().also { it.set("uid1", com.creatora.app.domain.account.monthKey(System.currentTimeMillis()), 46) }
        val vm = BillingViewModel(auth, users, usage, billing)
        keepActive(vm.state)

        assertEquals(Plan.FREE, vm.state.value.plan)
        assertEquals(92, vm.state.value.usage.percent)
        assertFalse(vm.state.value.billing.available)

        vm.upgrade()
        assertTrue(vm.state.value.upgradeUnavailable)

        billing.current.value = BillingState(available = true, price = "₹199", hasPro = true)
        billing.launch = LaunchOutcome.STARTED
        assertEquals(Plan.PRO, vm.state.value.plan)
        assertEquals(1, billing.refreshes)
    }

    // ---- language
    @Test
    fun language_isSavedLocally_andInTheCloudDocument() = runTest {
        val vm = LanguageViewModel(settings, auth, users)
        keepActive(vm.language)

        vm.choose(AppLanguage.HINDI)

        assertEquals(AppLanguage.HINDI, vm.language.value)
        assertEquals("hi", users.docs.value.getValue("uid1").language)
    }
}
