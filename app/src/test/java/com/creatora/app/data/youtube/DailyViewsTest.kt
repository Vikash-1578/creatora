package com.creatora.app.data.youtube

import android.app.Application
import com.creatora.app.data.ai.FakeTransport
import com.creatora.app.data.ai.HttpResponse
import com.creatora.app.domain.account.AdapterOutcome
import com.creatora.app.domain.home.sumSeries
import com.creatora.app.testutil.FakeErrorReporter
import com.creatora.app.testutil.FakeYouTubeAuthorizer
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The day-by-day views behind the mini chart on Home. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DailyViewsTest {
    private val transport = FakeTransport()
    private val adapter = YouTubeAdapter(transport, FakeYouTubeAuthorizer(), FakeErrorReporter())
    private val start = LocalDate.of(2026, 10, 1)

    @Test
    fun aDayTheReportLeavesOut_isZero_andTheSeriesIsOldestFirst() {
        val rows = listOf(
            mapOf<String, Any?>("day" to "2026-10-03", "views" to 30.0),
            mapOf<String, Any?>("day" to "2026-10-01", "views" to 10.0),
        )
        assertEquals(listOf(10L, 0L, 30L, 0L), reportToDailyViews(rows, start, LocalDate.of(2026, 10, 4)))
        assertTrue(reportToDailyViews(emptyList(), start, start.minusDays(1)).isEmpty())
    }

    @Test
    fun theAdapterAsksForOneRowPerDay_andEndsNoLaterThanYesterday() = runTest {
        transport.getResponses["dimensions=day"] = HttpResponse(200, """{"columnHeaders":[{"name":"day"},{"name":"views"}],"rows":[["2026-10-01",5],["2026-10-02",7]]}""")

        val series = (adapter.dailyViews("UC1", start, LocalDate.of(2026, 10, 2)) as AdapterOutcome.Ok).value

        assertEquals(listOf(5L, 7L), series)
        assertTrue(transport.getUrls.single().contains("metrics=views&sort=day"))

        // A requested end in the future is cut back to the last day Google has numbers for.
        val future = LocalDate.now(ZoneOffset.UTC).plusDays(5)
        transport.getUrls.clear()
        adapter.dailyViews("UC1", future.minusDays(3), future)
        assertTrue(transport.getUrls.single().contains("endDate=${LocalDate.now(ZoneOffset.UTC).minusDays(1)}"))
    }

    @Test
    fun aRefusedReportGivesNoChart_insteadOfAnInventedOne() = runTest {
        transport.getResponses["dimensions=day"] = HttpResponse(403, """{"error":{"message":"Forbidden"}}""")
        assertTrue(adapter.dailyViews("UC1", start, LocalDate.of(2026, 10, 2)) is AdapterOutcome.Failed)
    }

    @Test
    fun sumSeries_addsDayByDay_andRefusesToGuess() {
        assertEquals(listOf(11L, 22L), sumSeries(listOf(listOf(10L, 20L), listOf(1L, 2L))))
        assertNull(sumSeries(listOf(listOf(1L, 2L), null)))
        assertNull(sumSeries(listOf(listOf(1L, 2L), listOf(1L))))
        assertNull(sumSeries(emptyList()))
    }
}
