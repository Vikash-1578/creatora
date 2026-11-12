package com.creatora.app.domain.account

import java.util.GregorianCalendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountDomainTest {
    // ---- usage limit
    @Test
    fun usageStatus_roundsDown_andFollowsTheThresholds() {
        assertEquals(88, usageStatus(44, 50).percent)
        assertEquals(UsageLevel.OK, usageStatus(44, 50).level)
        assertFalse(usageStatus(44, 50).warnsWithDialog)

        val ninety = usageStatus(45, 50)
        assertEquals(90, ninety.percent)
        assertTrue(ninety.warnsWithDialog)
        assertEquals(UsageLevel.OK, ninety.level) // the dialog at exactly 90%, the banner starts at 91%

        val ninetyTwo = usageStatus(46, 50)
        assertEquals(92, ninetyTwo.percent)
        assertEquals(UsageLevel.BANNER, ninetyTwo.level)
        assertTrue(ninetyTwo.warnsWithDialog)

        assertEquals(99, usageStatus(99, 100).percent)
        assertEquals(UsageLevel.BANNER, usageStatus(99, 100).level)
    }

    @Test
    fun usageStatus_blocksAtTheLimit_andNeverShowsOverHundred() {
        val full = usageStatus(50, 50)
        assertEquals(UsageLevel.BLOCKED, full.level)
        assertFalse(full.warnsWithDialog)
        assertEquals(100, usageStatus(75, 50).percent)
        assertEquals(UsageLevel.BLOCKED, usageStatus(1, 0).level)
        assertEquals(0, usageStatus(-5, 50).percent)
    }

    @Test
    fun monthKey_isYearAndMonthInUtc() {
        val time = GregorianCalendar(TimeZone.getTimeZone("UTC")).apply { clear(); set(2026, 10, 30, 23, 59) }.timeInMillis
        assertEquals("202611", monthKey(time))
        assertEquals("202612", monthKey(time + 2 * 60 * 1000))
    }

    @Test
    fun plan_fromName_defaultsToFree() {
        assertEquals(Plan.PRO, Plan.fromName("pro"))
        assertEquals(Plan.PRO, Plan.fromName("PRO"))
        assertEquals(Plan.FREE, Plan.fromName(null))
        assertEquals(Plan.FREE, Plan.fromName("gold"))
        assertTrue(Plan.PRO.aiLimit > Plan.FREE.aiLimit && Plan.PRO.maxAccounts > Plan.FREE.maxAccounts)
    }

    // ---- language
    @Test
    fun language_forDevice() {
        assertEquals(AppLanguage.HINDI, AppLanguage.forDevice("hi"))
        assertEquals(AppLanguage.HINGLISH, AppLanguage.forDevice("hi", "Latn"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.forDevice("en"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.forDevice("ta"))
        assertEquals(AppLanguage.HINGLISH, AppLanguage.fromTag("hi-Latn"))
        assertNull(AppLanguage.fromTag("fr"))
        assertEquals(AppLanguage.HINDI, AppLanguage.deviceDefault(java.util.Locale.forLanguageTag("hi-IN")))
        assertEquals(AppLanguage.HINGLISH, AppLanguage.deviceDefault(java.util.Locale.forLanguageTag("hi-Latn-IN")))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.deviceDefault(java.util.Locale.forLanguageTag("en-US")))
    }

    // ---- pasted links
    @Test
    fun parseReference_readsYouTubeLinks() {
        assertEquals(PlatformRef(ConnectPlatform.YOUTUBE, "@mychannel", "https://www.youtube.com/@mychannel"), parseReference("https://www.youtube.com/@mychannel"))
        assertEquals("UCabcdefghijklmnopqrstuv", parseReference("youtube.com/channel/UCabcdefghijklmnopqrstuv")?.handleOrId)
        assertEquals("oldname", parseReference("https://m.youtube.com/user/oldname")?.handleOrId)
        assertNull(parseReference("https://www.youtube.com/watch?v=abc123"))
        assertNull(parseReference("https://youtu.be/abc123"))
    }

    @Test
    fun parseReference_detectsOtherPlatformsFromTheLink() {
        assertEquals(ConnectPlatform.INSTAGRAM, parseReference("https://www.instagram.com/creator_name/")?.platform)
        assertEquals("creator_name", parseReference("instagram.com/creator_name")?.handleOrId)
        assertEquals(ConnectPlatform.FACEBOOK, parseReference("https://facebook.com/mypage")?.platform)
        assertEquals(ConnectPlatform.TIKTOK, parseReference("https://www.tiktok.com/@dancer")?.platform)
        assertEquals("name", parseReference("https://sharechat.com/profile/name")?.handleOrId)
        assertEquals(ConnectPlatform.SNAPCHAT, parseReference("https://www.snapchat.com/add/snapname")?.platform)
        assertEquals(ConnectPlatform.MOJ, parseReference("https://mojapp.in/@someone")?.platform)
    }

    @Test
    fun parseReference_aLinkWinsOverThePickedPlatform_andPlainTextUsesIt() {
        assertEquals(ConnectPlatform.INSTAGRAM, parseReference("https://instagram.com/x", chosen = ConnectPlatform.YOUTUBE)?.platform)
        assertEquals(PlatformRef(ConnectPlatform.TIKTOK, "dancer", null), parseReference("dancer", chosen = ConnectPlatform.TIKTOK))
        assertEquals(ConnectPlatform.YOUTUBE, parseReference("@handle")?.platform)
        assertEquals(ConnectPlatform.YOUTUBE, parseReference("UCabcdefghijklmnopqrstuv")?.platform)
    }

    @Test
    fun parseReference_rejectsNonsense() {
        assertNull(parseReference(""))
        assertNull(parseReference("   "))
        assertNull(parseReference("just some words"))
        assertNull(parseReference("plainword"))
        assertNull(parseReference("https://example.com/profile"))
    }

    @Test
    fun connectedAccountKey_isSafeAndStable() {
        val account = ConnectedAccount(ConnectPlatform.YOUTUBE, "UC/ab c", "T")
        assertEquals("YOUTUBE_UCabc", account.key)
        assertEquals("MOJ_link", ConnectedAccount(ConnectPlatform.MOJ, "///", "T").key)
    }

    // ---- notifications
    @Test
    fun crossedMilestone_returnsTheMultipleThatWasPassed() {
        assertEquals(1_000L, crossedMilestone(950, 1_020, 1_000))
        assertEquals(3_000L, crossedMilestone(1_500, 3_200, 1_000)) // the highest one passed
        assertNull(crossedMilestone(1_010, 1_900, 1_000))
        assertNull(crossedMilestone(2_000, 1_000, 1_000))
        assertNull(crossedMilestone(10, 20, 0))
    }

    @Test
    fun notificationPrefs_defaultToAllOn() {
        val prefs = NotificationPrefs()
        assertTrue(NotificationCategory.entries.all { prefs.isOn(it) })
        assertFalse(prefs.copy(enabled = emptySet()).isOn(NotificationCategory.DAILY_IDEAS))
    }

    // ---- platforms without an API
    @Test
    fun unavailableAdapter_givesItsReason_forEverything() = kotlinx.coroutines.test.runTest {
        val adapter = UnavailableAdapter(ConnectPlatform.MOJ, "no public API")
        assertEquals(AdapterOutcome.Failed(AdapterError.NotAvailable("no public API")), adapter.ownChannels())
        assertEquals(AdapterOutcome.Failed(AdapterError.NotAvailable("no public API")), adapter.recentVideos("x", 5))
        assertEquals(ConnectPlatform.TIKTOK, PlatformRegistry(emptyList()).adapter(ConnectPlatform.TIKTOK).platform)
    }

    @Test
    fun everyPlatformWithAnApiIsFull_andOnlyTheLinkPlatformsAreLinkOnly() {
        val full = ConnectPlatform.entries.filter { it.support == PlatformSupport.FULL }
        assertEquals(listOf(ConnectPlatform.YOUTUBE, ConnectPlatform.INSTAGRAM, ConnectPlatform.FACEBOOK, ConnectPlatform.TIKTOK), full)
    }
}
