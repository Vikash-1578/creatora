package com.creatora.app.ui.profile

import com.creatora.app.testutil.FakeSettingsRepository
import com.creatora.app.testutil.MainDispatcherRule
import com.creatora.app.testutil.keepActive
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ProfileViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    @Test
    fun displayName_startsEmpty_andIsSavedTrimmed() = runTest {
        val settings = FakeSettingsRepository()
        val viewModel = ProfileViewModel(settings)
        keepActive(viewModel.displayName)
        assertEquals("", viewModel.displayName.value)

        viewModel.setDisplayName("  Asha  ")

        assertEquals("Asha", settings.displayName.value)
        assertEquals("Asha", viewModel.displayName.value)
    }
}
