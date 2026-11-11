package com.creatora.app.ui.usage

import com.creatora.app.domain.account.Plan
import com.creatora.app.domain.account.UsageLevel
import com.creatora.app.domain.account.monthKey
import com.creatora.app.domain.repository.BillingState
import com.creatora.app.domain.repository.UserDoc
import com.creatora.app.testutil.FakeAuthService
import com.creatora.app.testutil.FakeBillingRepository
import com.creatora.app.testutil.FakeSettingsRepository
import com.creatora.app.testutil.FakeUsageRepository
import com.creatora.app.testutil.FakeUserRepository
import com.creatora.app.testutil.MainDispatcherRule
import com.creatora.app.testutil.TestUser
import com.creatora.app.testutil.keepActive
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class UsageViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val month = monthKey(System.currentTimeMillis())
    private val auth = FakeAuthService(TestUser)
    private val users = FakeUserRepository().apply { put(UserDoc("uid1", "A", "a@x.com", null, Plan.FREE, "en", 1)) }
    private val usage = FakeUsageRepository()
    private val settings = FakeSettingsRepository()
    private val billing = FakeBillingRepository()

    private fun TestScope.open(): UsageViewModel {
        val vm = UsageViewModel(auth, users, usage, settings, billing)
        keepActive(vm.status)
        keepActive(vm.showDialog)
        return vm
    }

    @Test
    fun statusFollowsUsage_andTheBannerStartsAbove90() = runTest {
        val vm = open()
        assertEquals(0, vm.status.value?.percent)

        usage.set("uid1", month, 45)
        assertEquals(90, vm.status.value?.percent)
        assertEquals(UsageLevel.OK, vm.status.value?.level)

        usage.set("uid1", month, 47)
        assertEquals(UsageLevel.BANNER, vm.status.value?.level)
        assertEquals(94, vm.status.value?.percent)

        usage.set("uid1", month, 50)
        assertEquals(UsageLevel.BLOCKED, vm.status.value?.level)
    }

    @Test
    fun theDialogShowsOncePerMonth_atNinetyPercent() = runTest {
        val vm = open()
        assertFalse(vm.showDialog.value)

        usage.set("uid1", month, 45)
        assertTrue(vm.showDialog.value)

        vm.dialogSeen()
        assertFalse(vm.showDialog.value)
        assertEquals(month, settings.usageDialogMonth.value)

        usage.set("uid1", month, 47) // still above 90, but already shown this month
        assertFalse(vm.showDialog.value)
    }

    @Test
    fun noDialogAtTheLimit_orWhenSignedOut() = runTest {
        val vm = open()
        usage.set("uid1", month, 50)
        assertFalse(vm.showDialog.value)

        auth.current.value = null
        assertNull(vm.status.value)
    }

    @Test
    fun proUsesTheBiggerLimit() = runTest {
        val vm = open()
        usage.set("uid1", month, 46)
        billing.current.value = BillingState(available = true, hasPro = true)

        assertEquals(Plan.PRO.aiLimit, vm.status.value?.limit)
        assertEquals(UsageLevel.OK, vm.status.value?.level)
        assertEquals(1, billing.refreshes)
    }
}
