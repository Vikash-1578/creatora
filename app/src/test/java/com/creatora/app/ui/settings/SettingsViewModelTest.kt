package com.creatora.app.ui.settings

import com.creatora.app.domain.model.ThemeMode
import com.creatora.app.testutil.FakeSettingsRepository
import com.creatora.app.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    @Test
    fun selectingThemeMode_savesItAndUpdatesState() = runTest {
        val settings = FakeSettingsRepository()
        val viewModel = SettingsViewModel(settings)
        // Collect eagerly so the WhileSubscribed state is live before asserting.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.themeMode.collect {} }
        assertEquals(ThemeMode.SYSTEM, viewModel.themeMode.value)

        viewModel.onThemeModeSelected(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, settings.themeMode.value)
        assertEquals(ThemeMode.DARK, viewModel.themeMode.value)
    }
}
