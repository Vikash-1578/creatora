package com.creatora.app.data.instagram

import android.app.Application
import com.creatora.app.data.ai.FakeTransport
import com.creatora.app.data.ai.HttpResponse
import com.creatora.app.data.platform.PlatformApiException
import com.creatora.app.data.platform.redirectParams
import com.creatora.app.domain.account.AdapterError
import com.creatora.app.domain.account.AdapterOutcome
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.account.PlatformRef
import com.creatora.app.domain.platform.LoginOutcome
import com.creatora.app.domain.platform.PublishRequest
import com.creatora.app.domain.platform.PublishSource
import com.creatora.app.domain.platform.PublishStatus
import com.creatora.app.domain.platform.StoredToken
import com.creatora.app.testutil.FakeBrowser
import com.creatora.app.testutil.FakeErrorReporter
import com.creatora.app.testutil.FakeKeys
import com.creatora.app.testutil.FakeTokenStore
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val PROFILE = """{"id":"app-scoped","user_id":"1784","username":"mr.maya","name":"Maya","biography":"Coding Shorts","profile_picture_url":"https://cdn/p.jpg","followers_count":1200,"media_count":40,"account_type":"MEDIA_CREATOR"}"""
private const val MEDIA = """{"data":[
 {"id":"111","caption":"Learn #Python in 60s\nmore text","permalink":"https://instagram.com/p/a","thumbnail_url":"https://cdn/t1.jpg","timestamp":"2026-11-01T10:00:00+0000","like_count":50,"comments_count":4},
 {"id":"222","caption":"Photo day","permalink":"https://instagram.com/p/b","media_url":"https://cdn/m2.jpg","timestamp":"2026-10-20T08:00:00+0000"}]}"""

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class InstagramTest {
    private val transport = FakeTransport()
    private val tokens = FakeTokenStore()
    private val errors = FakeErrorReporter()
    private val keys = FakeKeys()
    private var now = 1_000_000_000_000L
    private val adapter get() = InstagramAdapter(transport, errors, tokens) { now }
    private fun signedIn(expiresAt: Long = 0L, issuedAt: Long = now) = tokens.put(ConnectPlatform.INSTAGRAM, "1784", StoredToken("tok", null, expiresAt, issuedAt))

    // ---------------------------------------------------------------- parsing
    @Test
    fun profile_isReadWithHandle_followers_andPhoto() {
        val p = parseInstagramProfile(PROFILE)
        assertEquals("1784", p.id)
        assertEquals("Maya", p.title)
        assertEquals("@mr.maya", p.handle)
        assertEquals(1200L, p.subscribers)
        assertEquals(40L, p.videoCount)
        assertEquals("https://cdn/p.jpg", p.photoUrl)
        assertEquals("MEDIA_CREATOR", parseInstagramAccountType(PROFILE))
    }

    @Test
    fun media_hasIsoDates_hashtagsAsTags_andNullWhenHidden() {
        val media = parseInstagramMedia(MEDIA)
        val first = media[0].toVideoStats(900)
        assertEquals("2026-11-01T10:00:00Z", first.publishedAt)
        assertEquals("Learn #Python in 60s", first.title)
        assertEquals(listOf("python"), first.tags)
        assertEquals(50L, first.likes)
        assertEquals("https://cdn/t1.jpg", first.thumbnailUrl)
        assertNull(media[1].likes) // not given: unknown, not zero
        assertEquals("https://cdn/m2.jpg", media[1].thumbnailUrl)
    }

    @Test
    fun insights_takeTotals_orAddUpTheDays() {
        val totals = parseInstagramInsights("""{"data":[{"name":"views","total_value":{"value":321}}]}""")
        assertEquals(mapOf("views" to 321L), totals)
        val days = parseInstagramInsights("""{"data":[{"name":"follower_count","values":[{"value":3},{"value":-1},{"value":4}]}]}""")
        assertEquals(mapOf("follower_count" to 6L), days)
        assertTrue(parseInstagramInsights("""{"data":[]}""").isEmpty())
        assertEquals(listOf("nice", "wow"), parseInstagramComments("""{"data":[{"text":"nice"},{"text":"wow"},{"id":"no text"}]}"""))
    }

    // ---------------------------------------------------------------- login
    private fun login(browser: FakeBrowser) = InstagramLogin(keys, browser, transport, errors, tokens) { now }

    private fun answerTokenCalls() {
        transport.postResponses["api.instagram.com/oauth/access_token"] = HttpResponse(200, """{"data":[{"access_token":"short","user_id":"1784","permissions":"x"}]}""")
        transport.getResponses["ig_exchange_token"] = HttpResponse(200, """{"access_token":"long","token_type":"bearer","expires_in":5184000}""")
        transport.getResponses["/me?fields="] = HttpResponse(200, PROFILE)
    }

    @Test
    fun signIn_opensInstagram_exchangesTheCode_andKeepsALongLivedToken() = runTest {
        answerTokenCalls()
        val browser = FakeBrowser()

        val outcome = login(browser).signIn() as LoginOutcome.Signed

        val url = browser.opened.single()
        val query = redirectParams(url)
        assertTrue(url.startsWith("https://www.instagram.com/oauth/authorize?"))
        assertEquals("ig-app", query["client_id"])
        assertEquals(keys.redirectUrl, query["redirect_uri"])
        assertTrue(query.getValue("scope").contains("instagram_business_content_publish"))
        assertTrue(transport.postBodies.single().contains("code=the-code"))
        assertTrue(transport.postBodies.single().contains("client_secret=ig-secret"))
        assertEquals("Maya", outcome.accounts.single().title)
        val saved = tokens.get(ConnectPlatform.INSTAGRAM, "1784")!!
        assertEquals("long", saved.access)
        assertEquals(now + 5_184_000_000L, saved.expiresAt)
    }

    @Test
    fun signIn_keepsTheShortToken_whenTheLongOneCannotBeGot() = runTest {
        answerTokenCalls()
        transport.getResponses["ig_exchange_token"] = HttpResponse(400, """{"error":{"message":"nope","code":100}}""")
        login(FakeBrowser()).signIn()
        assertEquals("short", tokens.get(ConnectPlatform.INSTAGRAM, "1784")!!.access)
    }

    @Test
    fun signIn_withoutSettings_sayswhatIsMissing_andOpensNothing() = runTest {
        keys.all.clear()
        keys.redirectUrl = ""
        val browser = FakeBrowser()
        val outcome = login(browser).signIn()
        assertEquals(LoginOutcome.NotConfigured(listOf("INSTAGRAM_APP_ID", "INSTAGRAM_APP_SECRET", "OAUTH_REDIRECT_URL")), outcome)
        assertTrue(browser.opened.isEmpty())
    }

    @Test
    fun signIn_cancelled_deniedAndRejected_areToldApart() = runTest {
        assertEquals(LoginOutcome.Cancelled, login(FakeBrowser { null }).signIn())
        assertEquals(LoginOutcome.Failed("App not live"), login(FakeBrowser { "creatora://oauth?error=invalid_request&error_description=App+not+live" }).signIn())
        transport.postResponses["oauth/access_token"] = HttpResponse(400, """{"error_type":"OAuthException","code":400,"error_message":"Invalid redirect_uri"}""")
        assertEquals(LoginOutcome.Failed("Invalid redirect_uri"), login(FakeBrowser()).signIn())
        assertTrue(tokens.saved.isEmpty())
        assertTrue(errors.recorded.single() is PlatformApiException) // the real HTTP error reaches Crashlytics
    }

    // ---------------------------------------------------------------- data
    @Test
    fun notSignedIn_isNotAuthorized_andOtherAccountsAreNotAvailable() = runTest {
        assertEquals(AdapterOutcome.Failed(AdapterError.NotAuthorized), adapter.recentVideos("1784", 5))
        val other = adapter.lookup(PlatformRef(ConnectPlatform.INSTAGRAM, "someone_else", null)) as AdapterOutcome.Failed
        assertTrue((other.error as AdapterError.NotAvailable).reason.contains("signed in"))
        assertTrue(transport.getUrls.isEmpty())
    }

    @Test
    fun recentVideos_getViewsFromInsights_andSkipPostsWithoutThem() = runTest {
        signedIn()
        transport.getResponses["me/media"] = HttpResponse(200, MEDIA)
        transport.getResponses["111/insights"] = HttpResponse(200, """{"data":[{"name":"views","total_value":{"value":900}}]}""")
        transport.getResponses["222/insights"] = HttpResponse(400, """{"error":{"message":"Insights not available","code":100}}""")

        val videos = (adapter.recentVideos("1784", 10) as AdapterOutcome.Ok).value

        assertEquals(listOf("111"), videos.map { it.id })
        assertEquals(900L, videos.single().views)
        assertTrue(transport.getUrls.all { it.contains("access_token=tok") })
    }

    @Test
    fun recentVideos_reportTheRealError_whenNothingHasInsights() = runTest {
        signedIn()
        transport.getResponses["me/media"] = HttpResponse(200, MEDIA)
        transport.getResponses["/insights"] = HttpResponse(403, """{"error":{"message":"Insufficient permission","code":10}}""")
        assertEquals(AdapterOutcome.Failed(AdapterError.PermissionMissing), adapter.recentVideos("1784", 10))
    }

    @Test
    fun lookupOfAnOwnAccount_andAnExpiredToken() = runTest {
        signedIn()
        transport.getResponses["/me?fields="] = HttpResponse(200, PROFILE)
        assertEquals("Maya", (adapter.lookup(PlatformRef(ConnectPlatform.INSTAGRAM, "1784", null)) as AdapterOutcome.Ok).value.title)

        tokens.put(ConnectPlatform.INSTAGRAM, "1784", StoredToken("tok", null, now - 1, now - 100))
        assertEquals(AdapterOutcome.Failed(AdapterError.NotAuthorized), adapter.recentVideos("1784", 5))
    }

    @Test
    fun aTokenNearItsEnd_isRefreshedOnce_andSaved() = runTest {
        signedIn(expiresAt = now + 3 * 24 * 3_600_000L, issuedAt = now - 50 * 24 * 3_600_000L)
        transport.getResponses["refresh_access_token"] = HttpResponse(200, """{"access_token":"fresh","token_type":"bearer","expires_in":5184000}""")
        transport.getResponses["me/media"] = HttpResponse(200, """{"data":[]}""")

        adapter.recentVideos("1784", 5)

        assertEquals("fresh", tokens.get(ConnectPlatform.INSTAGRAM, "1784")!!.access)
        assertTrue(transport.getUrls.last().contains("access_token=fresh"))
    }

    @Test
    fun channelMetrics_giveViews_andTheNetFollowerChange_orLeaveFollowersUnknown() = runTest {
        signedIn()
        transport.getResponses["metric=views"] = HttpResponse(200, """{"data":[{"name":"views","total_value":{"value":5000}}]}""")
        transport.getResponses["metric=follower_count"] = HttpResponse(200, """{"data":[{"name":"follower_count","values":[{"value":10},{"value":-4}]}]}""")
        val m = (adapter.channelMetrics("1784", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 28)) as AdapterOutcome.Ok).value
        assertEquals(5000L, m.views)
        assertEquals(6L, m.netSubscribers)
        assertNull(m.watchMinutes) // Instagram has no account watch time: unknown, not zero

        transport.getResponses["metric=follower_count"] = HttpResponse(400, """{"error":{"message":"Needs 100 followers","code":100}}""")
        val noFollowers = (adapter.channelMetrics("1784", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 28)) as AdapterOutcome.Ok).value
        assertNull(noFollowers.netSubscribers)
    }

    @Test
    fun whatInstagramDoesNotShare_saysSo() = runTest {
        val r = adapter.trending("IN", null, 5) as AdapterOutcome.Failed
        assertEquals(AdapterError.NotAvailable(InstagramAdapter.NOT_PUBLIC), r.error)
        assertTrue(adapter.searchChannels("coding", 5) is AdapterOutcome.Failed)
    }

    @Test
    fun comments_areReadPerPost() = runTest {
        signedIn()
        transport.getResponses["111/comments"] = HttpResponse(200, """{"data":[{"text":"great"}]}""")
        assertEquals(listOf("great"), (adapter.comments("1784", "111") as AdapterOutcome.Ok).value)
    }

    // ---------------------------------------------------------------- publishing
    private fun publisher() = InstagramPublisher(transport, errors, tokens) { }
    private val request = PublishRequest("1784", "My caption #tips", PublishSource.RemoteUrl("https://files.example/v.mp4"))

    @Test
    fun publish_makesAReel_waitsForProcessing_thenPublishes() = runTest {
        signedIn()
        transport.postResponses["/me/media_publish"] = HttpResponse(200, """{"id":"media-9"}""")
        transport.postResponses["/me/media"] = HttpResponse(200, """{"id":"container-1"}""")
        transport.getResponses["container-1"] = HttpResponse(200, """{"status_code":"FINISHED"}""")

        assertEquals(AdapterOutcome.Ok("media-9"), publisher().publish(request))

        val created = transport.postBodies.first()
        assertTrue(created.contains("media_type=REELS"))
        assertTrue(created.contains("video_url=https%3A%2F%2Ffiles.example%2Fv.mp4"))
        assertTrue(created.contains("caption=My+caption+%23tips"))
        assertTrue(transport.postBodies.last().contains("creation_id=container-1"))
    }

    @Test
    fun publish_postsNothing_whenInstagramFailsOrIsStillBusy_orTheSourceIsWrong() = runTest {
        signedIn()
        transport.postResponses["/me/media_publish"] = HttpResponse(200, """{"id":"media-9"}""")
        transport.postResponses["/me/media"] = HttpResponse(200, """{"id":"container-1"}""")

        transport.getResponses["container-1"] = HttpResponse(200, """{"status_code":"ERROR"}""")
        assertTrue((publisher().publish(request) as AdapterOutcome.Failed).error.toString().contains("could not process"))

        transport.getResponses["container-1"] = HttpResponse(200, """{"status_code":"IN_PROGRESS"}""")
        assertTrue((publisher().publish(request) as AdapterOutcome.Failed).error.toString().contains("Nothing was posted"))

        assertTrue(transport.postUrls.none { it.contains("media_publish") })

        val file = publisher().publish(request.copy(source = PublishSource.LocalFile(File("v.mp4")))) as AdapterOutcome.Failed
        assertTrue(file.error.toString().contains("public https link"))
        assertEquals(AdapterOutcome.Failed(AdapterError.NotAuthorized), InstagramPublisher(transport, errors, FakeTokenStore()) { }.publish(request))
    }

    @Test
    fun status_returnsTheLivePermalink() = runTest {
        signedIn()
        transport.getResponses["media-9"] = HttpResponse(200, """{"permalink":"https://instagram.com/reel/xyz"}""")
        assertEquals(AdapterOutcome.Ok(PublishStatus.Live("https://instagram.com/reel/xyz")), publisher().status("1784", "media-9"))
    }
}
