package com.creatora.app.ui

import com.creatora.app.domain.model.ThemeMode
import com.creatora.app.testutil.FakeAuthService
import com.creatora.app.testutil.FakeSettingsRepository
import com.creatora.app.testutil.MainDispatcherRule
import com.creatora.app.testutil.keepActive
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    @Test
    fun themeMode_followsSavedSetting() {
        val settings = FakeSettingsRepository(ThemeMode.DARK)
        val viewModel = AppViewModel(settings, FakeAuthService())

        assertEquals(ThemeMode.DARK, viewModel.themeMode.value)

        settings.themeMode.value = ThemeMode.LIGHT
        assertEquals(ThemeMode.LIGHT, viewModel.themeMode.value)
    }

    @Test
    fun session_isOnboardingUntilSignedInAndDone_thenHome() = kotlinx.coroutines.test.runTest {
        val settings = FakeSettingsRepository()
        val auth = FakeAuthService()
        val viewModel = AppViewModel(settings, auth)
        keepActive(viewModel.session)
        assertEquals(StartState.ONBOARDING, viewModel.session.value?.start)

        auth.current.value = com.creatora.app.testutil.TestUser
        assertEquals(StartState.ONBOARDING, viewModel.session.value?.start)

        settings.onboardingDone.value = true
        assertEquals(StartState.HOME, viewModel.session.value?.start)

        auth.current.value = null
        assertEquals(StartState.ONBOARDING, viewModel.session.value?.start)
    }

    @Test
    fun session_carriesTheSavedLanguage() = kotlinx.coroutines.test.runTest {
        val settings = FakeSettingsRepository()
        val viewModel = AppViewModel(settings, FakeAuthService())
        keepActive(viewModel.session)

        settings.language.value = com.creatora.app.domain.account.AppLanguage.HINGLISH

        assertEquals(com.creatora.app.domain.account.AppLanguage.HINGLISH, viewModel.session.value?.language)
    }
}
