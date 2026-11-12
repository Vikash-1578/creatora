package com.creatora.app.data.youtube

import android.app.Application
import com.creatora.app.data.ai.FakeTransport
import com.creatora.app.data.ai.HttpResponse
import com.creatora.app.domain.account.AdapterError
import com.creatora.app.domain.account.AdapterOutcome
import com.creatora.app.domain.account.YouTubeScopes
import com.creatora.app.domain.repository.AuthorizationOutcome
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

/** Why Home showed "not available": missing permission, a refused metric, a date in the future. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class YouTubeAnalyticsQaTest {
    private val transport = FakeTransport()
    private val authorizer = FakeYouTubeAuthorizer()
    private val reporter = FakeErrorReporter()
    private val adapter = YouTubeAdapter(transport, authorizer, reporter)

    private val core = """{"columnHeaders":[{"name":"video"},{"name":"views"},{"name":"averageViewDuration"},{"name":"averageViewPercentage"}],"rows":[["v1",100,45.5,30.2]]}"""

    @Test
    fun ifTheCtrMetricIsRefused_viewsAndRetentionStillCome_andCtrStaysUnknown() = runTest {
        transport.getResponses["metrics=videoThumbnailImpressions"] = HttpResponse(400, """{"error":{"message":"Unknown identifier (videoThumbnailImpressions) given in field parameters.metrics."}}""")
        transport.getResponses["metrics=views,averageViewDuration"] = HttpResponse(200, core)

        val result = (adapter.videoMetrics("UC1", listOf("v1")) as AdapterOutcome.Ok).value.single()

        assertEquals(30.2, result.averageViewPercent!!, 0.001)
        assertNull(result.ctrPercent) // the screen hides the field rather than writing "not available" on every row
        assertTrue(reporter.recorded.single().message!!.contains("400") && reporter.recorded.single().message!!.contains("Unknown identifier"))
    }

    @Test
    fun ctrIsAddedWhenYouTubeReturnsIt() = runTest {
        transport.getResponses["metrics=videoThumbnailImpressions"] = HttpResponse(
            200, """{"columnHeaders":[{"name":"video"},{"name":"videoThumbnailImpressions"},{"name":"videoThumbnailImpressionsClickRate"}],"rows":[["v1",5000,2.4]]}""",
        )
        transport.getResponses["metrics=views,averageViewDuration"] = HttpResponse(200, core)

        val result = (adapter.videoMetrics("UC1", listOf("v1")) as AdapterOutcome.Ok).value.single()

        assertEquals(2.4, result.ctrPercent!!, 0.001)
    }

    @Test
    fun withoutTheAnalyticsPermission_noReportIsRequested_andTheErrorSaysWhat() = runTest {
        authorizer.outcome = AuthorizationOutcome.Granted("token", setOf(YouTubeScopes.READ))

        val result = adapter.channelMetrics("UC1", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 28))

        assertEquals(AdapterOutcome.Failed(AdapterError.PermissionMissing), result)
        assertTrue(transport.getUrls.isEmpty())
    }

    @Test
    fun withAllPermissions_orWhenGoogleDoesNotSay_reportsAreRequested() = runTest {
        transport.getResponses["metrics=views"] = HttpResponse(200, """{"columnHeaders":[{"name":"views"},{"name":"estimatedMinutesWatched"},{"name":"subscribersGained"},{"name":"subscribersLost"}],"rows":[[10,60,2,1]]}""")

        authorizer.outcome = AuthorizationOutcome.Granted("token", YouTubeScopes.SIGN_IN.toSet())
        assertTrue(adapter.channelMetrics("UC1", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 28)) is AdapterOutcome.Ok)

        val unknown = YouTubeAdapter(transport, FakeYouTubeAuthorizer(AuthorizationOutcome.Granted("token")), reporter)
        assertTrue(unknown.channelMetrics("UC1", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 28)) is AdapterOutcome.Ok)
    }

    @Test
    fun anEndDateInTheFuture_isMovedBackToYesterdayUtc() = runTest {
        transport.getResponses["metrics=views"] = HttpResponse(200, """{"columnHeaders":[{"name":"views"},{"name":"estimatedMinutesWatched"},{"name":"subscribersGained"},{"name":"subscribersLost"}],"rows":[[10,60,2,1]]}""")
        val yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1)

        adapter.channelMetrics("UC1", LocalDate.now().minusDays(27), LocalDate.now().plusDays(1))

        assertTrue("endDate=$yesterday" in transport.getUrls[0])
    }

    @Test
    fun aRealHttpError_isReported_withItsStatusAndReason() = runTest {
        transport.getResponses["metrics=views"] = HttpResponse(403, """{"error":{"message":"Insufficient Permission","errors":[{"reason":"insufficientPermissions"}]}}""")

        val result = adapter.channelMetrics("UC1", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 28))

        assertTrue((result as AdapterOutcome.Failed).error is AdapterError.NotAvailable)
        val reported = reporter.recorded.first()
        assertTrue(reported is YouTubeApiException && reported.code == 403 && "Insufficient Permission" in reported.message!!)
    }

    @Test
    fun theChannelCountryIsRead() {
        val channel = parseChannels("""{"items":[{"id":"UC1","snippet":{"title":"MR.ERROR","country":"IN"},"statistics":{"subscriberCount":"6"}}]}""").single()
        assertEquals("IN", channel.country)
    }
}
