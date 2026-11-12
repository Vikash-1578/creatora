package com.creatora.app.ui.profile

import com.creatora.app.testutil.FakeAuthService
import com.creatora.app.testutil.FakeConnectedAccountRepository
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
        val viewModel = ProfileViewModel(settings, FakeAuthService(), FakeConnectedAccountRepository())
        keepActive(viewModel.displayName)
        assertEquals("", viewModel.displayName.value)

        viewModel.setDisplayName("  Asha  ")

        assertEquals("Asha", settings.displayName.value)
        assertEquals("Asha", viewModel.displayName.value)
    }

    @Test
    fun channelTitle_isTheConnectedYouTubeChannel() = runTest {
        val auth = FakeAuthService(com.creatora.app.testutil.TestUser)
        val accounts = FakeConnectedAccountRepository()
        val viewModel = ProfileViewModel(FakeSettingsRepository(), auth, accounts)
        keepActive(viewModel.channelTitle)
        assertEquals(null, viewModel.channelTitle.value)

        accounts.save("uid1", com.creatora.app.domain.account.ConnectedAccount(com.creatora.app.domain.account.ConnectPlatform.YOUTUBE, "UC1", "My channel"))

        assertEquals("My channel", viewModel.channelTitle.value)
    }

    @Test
    fun theGoogleNameAndPhoto_areUsedUntilTheUserChoosesOne() = runTest {
        val auth = FakeAuthService(com.creatora.app.testutil.TestUser.copy(photoUrl = "https://img/google.jpg"))
        val settings = FakeSettingsRepository()
        val viewModel = ProfileViewModel(settings, auth, FakeConnectedAccountRepository())
        keepActive(viewModel.displayName)
        keepActive(viewModel.photoUrl)

        assertEquals("Asha Rao", viewModel.displayName.value)
        assertEquals("https://img/google.jpg", viewModel.photoUrl.value)

        viewModel.setDisplayName("Asha")
        assertEquals("Asha", viewModel.displayName.value)
    }

    @Test
    fun withoutAGooglePhoto_theChannelPictureIsUsed() = runTest {
        val auth = FakeAuthService(com.creatora.app.testutil.TestUser)
        val accounts = FakeConnectedAccountRepository()
        val viewModel = ProfileViewModel(FakeSettingsRepository(), auth, accounts)
        keepActive(viewModel.photoUrl)
        assertEquals(null, viewModel.photoUrl.value)

        accounts.save("uid1", com.creatora.app.domain.account.ConnectedAccount(com.creatora.app.domain.account.ConnectPlatform.YOUTUBE, "UC1", "My channel", photoUrl = "https://img/channel.jpg"))

        assertEquals("https://img/channel.jpg", viewModel.photoUrl.value)
    }
}
