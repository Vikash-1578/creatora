package com.creatora.app.data.home

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.creatora.app.domain.account.AdapterError
import com.creatora.app.domain.account.AdapterOutcome
import com.creatora.app.domain.account.NotificationCategory
import com.creatora.app.domain.account.PlatformRegistry
import com.creatora.app.domain.account.VideoStats
import com.creatora.app.domain.home.StreakUnit
import com.creatora.app.domain.repository.TrendingResult
import com.creatora.app.testutil.FakeCacheRepository
import com.creatora.app.testutil.FakePlatformAdapter
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class HomeDataTest {
    @get:Rule val folder = TemporaryFolder()

    private fun TestScope.store(name: String) = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "$name.preferences_pb") }

    // ---- prefs
    @Test
    fun homePrefs_areRemembered() = runTest {
        val prefs = DataStoreHomePrefs(store("prefs"))
        assertNull(prefs.selectedKey.first())
        assertFalse(prefs.hideAmounts.first())
        assertEquals(StreakUnit.WEEKS, prefs.streakUnit.first())
        assertNull(prefs.region.first())

        prefs.setSelectedKey("YOUTUBE_UC1")
        prefs.setHideAmounts(true)
        prefs.setStreakUnit(StreakUnit.DAYS)
        prefs.setRegion("in")

        assertEquals("YOUTUBE_UC1", prefs.selectedKey.first())
        assertTrue(prefs.hideAmounts.first())
        assertEquals(StreakUnit.DAYS, prefs.streakUnit.first())
        assertEquals("IN", prefs.region.first())
    }

    // ---- inbox
    @Test
    fun inbox_keepsTheNewestFifty_newestFirst_andCanBeCleared() = runTest {
        val inbox = DataStoreNotificationInbox(store("inbox"))
        assertTrue(inbox.entries.first().isEmpty())

        (1..55).forEach { inbox.add(NotificationCategory.NEW_COMMENTS, "Title $it", "Text $it", it.toLong()) }

        val entries = inbox.entries.first()
        assertEquals(50, entries.size)
        assertEquals("Title 55", entries.first().title)
        assertEquals("Title 6", entries.last().title)
        assertEquals(NotificationCategory.NEW_COMMENTS, entries.first().category)

        inbox.clear()
        assertTrue(inbox.entries.first().isEmpty())
    }

    // ---- trending
    private val youtube = FakePlatformAdapter()
    private val cache = FakeCacheRepository()
    private var clock = 10_000_000L
    private val repository = CachedTrendingRepository(PlatformRegistry(listOf(youtube)), cache) { clock }
    private fun video(id: String, views: Long = 100) = VideoStats(id, "Video $id", "2026-01-01T00:00:00Z", views, channelTitle = "Chan $id", thumbnailUrl = "https://img/$id")

    @Test
    fun trending_usesTheCategoryChart_withoutASearch_whenTheChartHasEnough() = runTest {
        youtube.trendingResult = AdapterOutcome.Ok((1..10).map { video("t$it") })

        val result = repository.trending("uid1", "IN", "minecraft gaming") as TrendingResult.Ok

        assertEquals(10, result.items.size)
        assertEquals(listOf("IN" to "20"), youtube.trendingCalls)
        assertTrue(youtube.searchCalls.isEmpty()) // a search costs 100 quota units
        assertFalse(result.fromCache)
    }

    @Test
    fun trending_searchesTheNiche_onlyWhenThereIsNoCategoryOrTheChartIsThin() = runTest {
        youtube.trendingResult = AdapterOutcome.Ok((1..3).map { video("t$it") })
        youtube.searchResult = AdapterOutcome.Ok(listOf(video("s1", 900), video("t1")))

        val noCategory = repository.trending("uid1", "IN", "street food") as TrendingResult.Ok
        assertEquals(listOf("street food"), youtube.searchCalls)
        assertEquals(listOf("s1", "t1", "t2", "t3"), noCategory.items.map { it.id }) // search first, no duplicates

        youtube.searchCalls.clear()
        repository.trending("uid1", "IN", "minecraft gaming", force = true)
        assertEquals(listOf("minecraft gaming"), youtube.searchCalls) // the chart had only 3 matches

        youtube.searchCalls.clear()
        repository.trending("uid1", "IN", null, force = true)
        assertTrue(youtube.searchCalls.isEmpty()) // nothing known about the niche: general chart only
    }

    @Test
    fun trending_isCachedForADay_andRefreshOnlyWhenAskedOrExpired() = runTest {
        youtube.trendingResult = AdapterOutcome.Ok(listOf(video("t1")))
        repository.trending("uid1", "IN", null)
        val again = repository.trending("uid1", "IN", null) as TrendingResult.Ok
        assertTrue(again.fromCache)
        assertEquals(1, youtube.trendingCalls.size)
        assertEquals("t1", again.items.single().id)
        assertEquals("https://img/t1", again.items.single().thumbnailUrl)
        assertEquals("Chan t1", again.items.single().channelTitle)

        clock += 25L * 60 * 60 * 1000
        repository.trending("uid1", "IN", null)
        assertEquals(2, youtube.trendingCalls.size)

        repository.trending("uid1", "IN", null, force = true)
        assertEquals(3, youtube.trendingCalls.size)
    }

    @Test
    fun trending_fallsBackToTheOldCache_orReportsTheError() = runTest {
        youtube.trendingResult = AdapterOutcome.Failed(AdapterError.QuotaExceeded)
        assertEquals(TrendingResult.Failed(AdapterError.QuotaExceeded), repository.trending("uid1", "IN", null))

        youtube.trendingResult = AdapterOutcome.Ok(listOf(video("t1")))
        repository.trending("uid1", "IN", null)
        clock += 30L * 60 * 60 * 1000
        youtube.trendingResult = AdapterOutcome.Failed(AdapterError.QuotaExceeded)

        val stale = repository.trending("uid1", "IN", null) as TrendingResult.Ok
        assertTrue(stale.stale && stale.fromCache)
        assertEquals("t1", stale.items.single().id)
    }
}
