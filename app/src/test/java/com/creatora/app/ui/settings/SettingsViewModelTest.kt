package com.creatora.app.ui.settings

import com.creatora.app.domain.model.ThemeMode
import com.creatora.app.testutil.FakeSettingsRepository
import com.creatora.app.testutil.MainDispatcherRule
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    @Test
    fun selectingThemeMode_savesItAndUpdatesState() = runTest {
        val settings = FakeSettingsRepository()
        val viewModel = SettingsViewModel(settings)
        backgroundScope.launch { viewModel.themeMode.collect {} }
        assertEquals(ThemeMode.SYSTEM, viewModel.themeMode.value)

        viewModel.onThemeModeSelected(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, settings.themeMode.value)
        assertEquals(ThemeMode.DARK, viewModel.themeMode.value)
    }
}
