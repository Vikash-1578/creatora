package com.creatora.app.ui.research

import com.creatora.app.domain.home.AccountSelection
import com.creatora.app.testutil.HomeKit
import com.creatora.app.testutil.MainDispatcherRule
import com.creatora.app.testutil.keepActive
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class ResearchViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val kit = HomeKit()

    @Test
    fun theSwitcher_isTheSameChoiceAsHome() = runTest {
        val vm = ResearchViewModel(kit.context)
        keepActive(vm.header)
        assertEquals(ResearchHeader(emptyList(), AccountSelection.All), vm.header.value)

        kit.connect("UC1")
        kit.connect("UC2", "Second")
        assertEquals(2, vm.header.value!!.accounts.size)
        assertEquals(AccountSelection.One("YOUTUBE_UC1"), vm.header.value!!.selection) // the first account until another is chosen

        vm.select(AccountSelection.All)
        assertEquals(AccountSelection.All, vm.header.value!!.selection)
        vm.select(AccountSelection.One("YOUTUBE_UC2"))

        assertEquals(AccountSelection.One("YOUTUBE_UC2"), vm.header.value!!.selection)
        assertEquals("YOUTUBE_UC2", kit.prefs.selectedKey.value) // Home reads the same saved choice
    }

    @Test
    fun beforeAnythingLoads_thereIsNoHeader() {
        assertNull(ResearchViewModel(kit.context).header.value)
    }
}
