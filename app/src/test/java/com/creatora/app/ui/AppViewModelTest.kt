package com.creatora.app.ui

import com.creatora.app.domain.model.ThemeMode
import com.creatora.app.testutil.FakeSettingsRepository
import com.creatora.app.testutil.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    @Test
    fun themeMode_followsSavedSetting() {
        val settings = FakeSettingsRepository(ThemeMode.DARK)
        val viewModel = AppViewModel(settings)

        assertEquals(ThemeMode.DARK, viewModel.themeMode.value)

        settings.themeMode.value = ThemeMode.LIGHT
        assertEquals(ThemeMode.LIGHT, viewModel.themeMode.value)
    }
}
