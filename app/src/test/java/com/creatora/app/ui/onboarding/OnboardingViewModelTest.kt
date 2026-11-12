package com.creatora.app.ui.onboarding

import com.creatora.app.domain.account.AccountConnector
import com.creatora.app.domain.account.AdapterError
import com.creatora.app.domain.account.AdapterOutcome
import com.creatora.app.domain.account.AppLanguage
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.account.CreatorProfileService
import com.creatora.app.domain.account.NotificationCategory
import com.creatora.app.domain.account.PlatformRegistry
import com.creatora.app.domain.ai.AiTasks
import com.creatora.app.domain.repository.LaunchOutcome
import com.creatora.app.domain.repository.SignInOutcome
import com.creatora.app.testutil.FakeAiService
import com.creatora.app.testutil.FakeAuthService
import com.creatora.app.testutil.FakeBillingRepository
import com.creatora.app.testutil.FakeConnectedAccountRepository
import com.creatora.app.testutil.FakeNotificationPrefs
import com.creatora.app.testutil.FakePlatformAdapter
import com.creatora.app.testutil.FakeSettingsRepository
import com.creatora.app.testutil.FakeUserRepository
import com.creatora.app.testutil.MainDispatcherRule
import com.creatora.app.testutil.TestUser
import com.creatora.app.testutil.okReply
import com.creatora.app.testutil.preview
import com.creatora.app.ui.accounts.ConnectProblem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OnboardingViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val settings = FakeSettingsRepository()
    private val auth = FakeAuthService()
    private val users = FakeUserRepository()
    private val accounts = FakeConnectedAccountRepository()
    private val youtube = FakePlatformAdapter()
    private val billing = FakeBillingRepository()
    private val prefs = FakeNotificationPrefs()
    private val goals = com.creatora.app.testutil.FakeGoalRepository()
    private val ai = FakeAiService(okReply("NICHE: street food\nFORMATS: shorts\nLANGUAGE: Hindi\nSUMMARY: Food films."))

    private val reporter = com.creatora.app.testutil.FakeErrorReporter()

    private fun viewModel(): OnboardingViewModel {
        val registry = PlatformRegistry(listOf(youtube))
        return OnboardingViewModel(
            settings, auth, users,
            AccountConnector(registry, accounts, users, billing),
            CreatorProfileService(registry, accounts, users, AiTasks(ai)),
            prefs, billing, goals, accounts, reporter,
        )
    }

    @Test
    fun theLanguageIsPreselectedFromTheDevice_andSaved() = runTest {
        val vm = viewModel()
        // The test JVM's locale is English, so English is chosen and saved.
        assertEquals(AppLanguage.ENGLISH, vm.state.value.language)
        assertEquals(AppLanguage.ENGLISH, settings.language.value)
        assertEquals(OnboardingStep.LANGUAGE, vm.state.value.step)
    }

    @Test
    fun aSavedLanguageWins_andChoosingAnotherAppliesAtOnce() = runTest {
        settings.language.value = AppLanguage.HINDI
        val vm = viewModel()
        assertEquals(AppLanguage.HINDI, vm.state.value.language)

        vm.chooseLanguage(AppLanguage.HINGLISH)

        assertEquals(AppLanguage.HINGLISH, settings.language.value)
        assertEquals(AppLanguage.HINGLISH, vm.state.value.language)
    }

    @Test
    fun stepsAdvanceInOrder() = runTest {
        val vm = viewModel()
        vm.next()
        assertEquals(OnboardingStep.WELCOME, vm.state.value.step)
    }

    @Test
    fun signIn_createsTheUserDocument_andLoadsTheChannels() = runTest {
        youtube.own = AdapterOutcome.Ok(listOf(preview("UC1"), preview("UC2", "Second")))
        val vm = viewModel()

        vm.signIn()

        val doc = users.docs.value.getValue(TestUser.uid)
        assertEquals(Triple("Asha Rao", "asha@example.com", "free"), Triple(doc.name, doc.email, doc.plan.name.lowercase()))
        assertEquals(AppLanguage.ENGLISH.tag, doc.language)
        assertEquals(OnboardingStep.CONNECT, vm.state.value.step)
        assertEquals(listOf("UC1", "UC2"), vm.state.value.channels.map { it.id })
        assertFalse(vm.state.value.loadingChannels)
    }

    @Test
    fun signIn_reportsAMissingConfiguration_andFailures() = runTest {
        val vm = viewModel()

        auth.signInOutcome = SignInOutcome.NotConfigured
        vm.signIn()
        assertTrue(vm.state.value.signInNotConfigured)
        assertEquals(OnboardingStep.LANGUAGE, vm.state.value.step)

        auth.signInOutcome = SignInOutcome.Failed
        vm.signIn()
        assertTrue(vm.state.value.signInFailed)
        assertFalse(vm.state.value.signInNotConfigured)

        auth.signInOutcome = SignInOutcome.Cancelled
        vm.signIn()
        assertFalse(vm.state.value.signingIn || vm.state.value.signInFailed)
    }

    @Test
    fun channelProblems_areShown_withoutAnyMadeUpData() = runTest {
        youtube.own = AdapterOutcome.Failed(AdapterError.NotAuthorized)
        val vm = viewModel()

        vm.signIn()

        assertEquals(ConnectProblem.NOT_AUTHORIZED, vm.state.value.channelsProblem)
        assertTrue(vm.state.value.channels.isEmpty())
    }

    @Test
    fun confirmingAChannel_connectsIt_andWorksOutTheProfileWithoutQuestions() = runTest {
        youtube.own = AdapterOutcome.Ok(listOf(preview("UC1")))
        val vm = viewModel()
        vm.signIn()

        vm.confirmChannel(vm.state.value.channels.single())

        assertEquals(listOf("My channel"), vm.state.value.connectedTitles)
        assertTrue(vm.state.value.channels.isEmpty())
        assertEquals("UC1", accounts.byUser.value.getValue(TestUser.uid).single().id)
        assertEquals("street food", users.docs.value.getValue(TestUser.uid).creatorProfile?.niche)

        vm.finishConnect()
        assertEquals(OnboardingStep.GOAL, vm.state.value.step) // no question: an account is connected
    }

    @Test
    fun theFreePlanAllowsOneAccount() = runTest {
        youtube.own = AdapterOutcome.Ok(listOf(preview("UC1"), preview("UC2", "Second")))
        val vm = viewModel()
        vm.signIn()

        vm.confirmChannel(vm.state.value.channels[0])
        vm.confirmChannel(vm.state.value.channels.single())

        assertEquals(1, vm.state.value.lookup.planLimitMax)
        assertEquals(1, accounts.byUser.value.getValue(TestUser.uid).size)
    }

    @Test
    fun withNothingConnected_oneQuestionIsAsked_andTheAnswerBecomesTheProfile() = runTest {
        val vm = viewModel()
        vm.signIn()
        vm.finishConnect()
        assertEquals(OnboardingStep.ASK, vm.state.value.step)

        vm.answerWhatYouMake("  gaming videos ")

        val profile = users.docs.value.getValue(TestUser.uid).creatorProfile!!
        assertEquals("gaming videos", profile.niche)
        assertEquals(com.creatora.app.domain.account.ProfileSource.ASKED, profile.source)
        assertEquals(OnboardingStep.GOAL, vm.state.value.step)
        assertTrue(ai.requests.isEmpty() || ai.requests.none { "gaming" in it.messages.joinToString { m -> m.text } })
    }

    @Test
    fun skippingTheQuestion_storesNothing() = runTest {
        val vm = viewModel()
        vm.signIn()
        vm.finishConnect()

        vm.answerWhatYouMake("")

        assertNull(users.docs.value.getValue(TestUser.uid).creatorProfile)
        assertEquals(OnboardingStep.GOAL, vm.state.value.step)
    }

    @Test
    fun pastingALinkOnlyLink_savesTheLink_andComingSoonSavesNothing() = runTest {
        val vm = viewModel()
        vm.signIn()

        vm.lookUp("https://sharechat.com/profile/maya", ConnectPlatform.SHARECHAT)
        assertTrue(vm.state.value.lookup.linkSaved)
        val saved = accounts.byUser.value.getValue(TestUser.uid).single()
        assertEquals(ConnectPlatform.SHARECHAT, saved.platform)
        assertEquals("https://sharechat.com/profile/maya", saved.link)

        vm.lookUp("https://instagram.com/maya", ConnectPlatform.INSTAGRAM)
        assertEquals(ConnectPlatform.INSTAGRAM, vm.state.value.lookup.comingSoon)
        assertEquals(1, accounts.byUser.value.getValue(TestUser.uid).size)

        vm.lookUp("nonsense words", ConnectPlatform.YOUTUBE)
        assertEquals(ConnectProblem.UNRECOGNISED, vm.state.value.lookup.problem)
    }

    @Test
    fun theGoalIsSavedForThirtyDays_withTheRealStartingSubscribers() = runTest {
        youtube.own = AdapterOutcome.Ok(listOf(preview("UC1", subs = 1_200)))
        val vm = viewModel()
        vm.signIn()
        vm.confirmChannel(vm.state.value.channels.single())
        vm.finishConnect()
        assertEquals(OnboardingStep.GOAL, vm.state.value.step)

        vm.saveGoal(com.creatora.app.domain.account.GoalMetric.SUBSCRIBERS, 2_000, "  grow my food channel ", 3)

        val goal = goals.byUser.value.getValue(TestUser.uid).single()
        assertEquals(2_000L, goal.target)
        assertEquals(1_200L, goal.startValue)
        assertEquals("grow my food channel", goal.desire)
        assertEquals(3, goal.uploadsPerWeek)
        assertTrue(goal.primary)
        assertEquals(30L * 24 * 60 * 60 * 1000, goal.deadline - goal.startedAt)
        assertEquals(OnboardingStep.NOTIFICATIONS, vm.state.value.step)
    }

    @Test
    fun skippingTheGoal_orAZeroTarget_savesNothing() = runTest {
        val vm = viewModel()
        vm.signIn()
        vm.finishConnect() // nothing connected: the question comes first
        vm.answerWhatYouMake("")
        assertEquals(OnboardingStep.GOAL, vm.state.value.step)

        vm.saveGoal(com.creatora.app.domain.account.GoalMetric.VIEWS, 0, "x", null)
        assertTrue(goals.byUser.value.isEmpty())
        assertEquals(OnboardingStep.NOTIFICATIONS, vm.state.value.step)

        val other = viewModel()
        other.skipGoal()
        assertEquals(OnboardingStep.NOTIFICATIONS, other.state.value.step)
        assertTrue(goals.byUser.value.isEmpty())
    }

    @Test
    fun loginButtons_googleRefetchesChannels_othersSayComingSoon() = runTest {
        val vm = viewModel()
        vm.signIn()
        youtube.own = AdapterOutcome.Ok(listOf(preview("UC7")))

        vm.login(ConnectPlatform.YOUTUBE)
        assertEquals(listOf("UC7"), vm.state.value.channels.map { it.id })

        vm.login(ConnectPlatform.TIKTOK)
        assertEquals(ConnectPlatform.TIKTOK, vm.state.value.lookup.comingSoon)
        assertTrue(accounts.byUser.value.isEmpty())
    }

    @Test
    fun aPastedChannelShowsAPreview_thatMustBeConfirmed() = runTest {
        youtube.lookupResult = AdapterOutcome.Ok(preview("UC9", "Pasted"))
        val vm = viewModel()
        vm.signIn()

        vm.lookUp("@pasted", ConnectPlatform.YOUTUBE)
        assertEquals("Pasted", vm.state.value.lookup.pasted?.title)
        assertTrue(accounts.byUser.value.isEmpty())

        vm.confirmChannel(vm.state.value.lookup.pasted!!)
        assertEquals("UC9", accounts.byUser.value.getValue(TestUser.uid).single().id)
        assertNull(vm.state.value.lookup.pasted)
    }

    @Test
    fun notificationChoices_areSaved() = runTest {
        val vm = viewModel()

        vm.setNotification(NotificationCategory.DAILY_IDEAS, false)
        vm.setMilestone(5_000)

        assertFalse(prefs.current.value.isOn(NotificationCategory.DAILY_IDEAS))
        assertEquals(5_000L, prefs.current.value.milestoneViews)
        assertFalse(vm.state.value.notificationPrefs.isOn(NotificationCategory.DAILY_IDEAS))
    }

    @Test
    fun continuingFree_finishesOnboarding() = runTest {
        val vm = viewModel()
        vm.continueFree()
        assertTrue(settings.onboardingDone.value)
    }

    @Test
    fun tryingPro_saysSoWhenPlayBillingIsNotReady() = runTest {
        val vm = viewModel()
        billing.launch = LaunchOutcome.UNAVAILABLE
        vm.tryPro()
        assertTrue(vm.state.value.proUnavailable)
        assertEquals(1, billing.refreshes)

        billing.launch = LaunchOutcome.STARTED
        vm.tryPro()
        assertNotNull(vm.state.value)
    }

    @Test
    fun anAlreadySignedInUser_resumesAtTheConnectStep() = runTest {
        auth.current.value = TestUser
        youtube.own = AdapterOutcome.Ok(listOf(preview()))

        val vm = viewModel()

        assertEquals(OnboardingStep.CONNECT, vm.state.value.step)
        assertEquals(1, vm.state.value.channels.size)
    }

    // ---------------------------------------------------------------- crash hotfix

    @Test
    fun aChannelThatCannotBeSaved_showsTryAgain_insteadOfCrashing() = runTest {
        youtube.own = AdapterOutcome.Ok(listOf(preview("UC1")))
        val vm = viewModel()
        vm.signIn()
        val channel = vm.state.value.channels.single()
        accounts.failSave = true // the rules refuse the write

        vm.confirmChannel(channel)

        assertEquals(channel, vm.state.value.saveFailed)
        assertEquals(listOf(channel), vm.state.value.channels) // still there to try again
        assertTrue(vm.state.value.connectedTitles.isEmpty())
        assertEquals(1, reporter.recorded.size) // reported to Crashlytics

        accounts.failSave = false
        vm.confirmChannel(channel)

        assertEquals(null, vm.state.value.saveFailed)
        assertEquals(listOf("My channel"), vm.state.value.connectedTitles)
        assertTrue(vm.state.value.channels.isEmpty())
    }

    @Test
    fun theScreenWaitsForTheSavedStep_beforeShowingAnything() = runTest {
        val vm = viewModel()
        assertTrue(vm.state.value.loaded)
    }

    @Test
    fun everyStepIsRemembered_soARestartResumesThere() = runTest {
        val vm = viewModel()

        vm.next()
        assertEquals(OnboardingStep.WELCOME.name, settings.onboardingStep.value)
        vm.skipGoal()
        assertEquals(OnboardingStep.NOTIFICATIONS.name, settings.onboardingStep.value)
    }

    @Test
    fun signedIn_resumesAtTheSavedStep_andNeverGoesBackToLanguageOrWelcome() = runTest {
        auth.current.value = TestUser
        settings.language.value = AppLanguage.ENGLISH
        settings.onboardingStep.value = OnboardingStep.GOAL.name
        assertEquals(OnboardingStep.GOAL, viewModel().state.value.step)

        settings.onboardingStep.value = OnboardingStep.LANGUAGE.name
        assertEquals(OnboardingStep.CONNECT, viewModel().state.value.step)

        settings.onboardingStep.value = null
        assertEquals(OnboardingStep.CONNECT, viewModel().state.value.step) // signed in but nothing saved: the connect step
    }

    @Test
    fun signedOut_neverSkipsSignIn_butKeepsAChosenLanguage() = runTest {
        settings.language.value = AppLanguage.HINDI
        settings.onboardingStep.value = OnboardingStep.PLAN.name
        assertEquals(OnboardingStep.WELCOME, viewModel().state.value.step)

        settings.onboardingStep.value = OnboardingStep.WELCOME.name
        assertEquals(OnboardingStep.WELCOME, viewModel().state.value.step)
    }
}
