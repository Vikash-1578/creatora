package com.creatora.app.data.platform

import android.app.Application
import com.creatora.app.domain.account.AdapterError
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.platform.PlatformKeys
import com.creatora.app.domain.platform.missingSettings
import com.creatora.app.testutil.FakeBrowser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PlatformInfrastructureTest {

    @Test
    fun pkceChallenge_matchesTheExampleInRfc7636() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", Pkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
        assertNotEquals(Pkce.randomToken(), Pkce.randomToken())
    }

    @Test
    fun redirectParams_decodesTheQuery_andIgnoresTheFragment() {
        val params = redirectParams("creatora://oauth?code=ab%20c&state=s1&error_description=bad+thing#frag")
        assertEquals(mapOf("code" to "ab c", "state" to "s1", "error_description" to "bad thing"), params)
        assertTrue(redirectParams("creatora://oauth").isEmpty())
        assertEquals("a=1&b=x+y", formEncode(linkedMapOf("a" to "1", "b" to "x y")))
    }

    @Test
    fun oauthFlow_returnsTheCode_whenTheStateMatches() = runTest {
        val browser = FakeBrowser { url -> "creatora://oauth?code=abc%23_&state=${FakeBrowser.stateOf(url)}" }
        val result = OAuthFlow(browser).authorize { state -> "https://platform.example/auth?state=$state" }
        assertEquals(AuthResult.Code("abc"), result) // the "#_" Instagram appends is removed
    }

    @Test
    fun oauthFlow_saysWhyThereIsNoCode() = runTest {
        suspend fun run(answer: (String) -> String?) = OAuthFlow(FakeBrowser(answer)).authorize { "https://platform.example/auth?state=$it" }

        assertEquals(AuthResult.Cancelled, run { null })
        assertEquals(AuthResult.Cancelled, run { "creatora://oauth?error=access_denied" })
        assertEquals(AuthResult.Denied("Invalid scope"), run { "creatora://oauth?error=invalid_scope&error_description=Invalid+scope" })
        assertTrue(run { "creatora://oauth?code=abc&state=someone-elses" } is AuthResult.Denied)
        assertTrue(run { url -> "creatora://oauth?state=${FakeBrowser.stateOf(url)}" } is AuthResult.Denied)
    }

    @Test
    fun platformError_usesThePlatformsOwnCodesAndWords() {
        assertEquals(AdapterError.NotAuthorized, platformError(400, """{"error":{"message":"Session expired","code":190}}"""))
        assertEquals(AdapterError.PermissionMissing, platformError(403, """{"error":{"message":"No pages_manage_posts","code":200}}"""))
        assertEquals(AdapterError.QuotaExceeded, platformError(400, """{"error":{"message":"Too many calls","code":4}}"""))
        assertEquals(AdapterError.NotAuthorized, platformError(401, """{"error":{"code":"access_token_invalid","message":"expired"}}"""))
        assertEquals(AdapterError.PermissionMissing, platformError(403, """{"error":{"code":"scope_not_authorized","message":"no scope"}}"""))
        assertEquals(AdapterError.QuotaExceeded, platformError(429, """{"error":{"code":"rate_limit_exceeded","message":"slow down"}}"""))
        assertEquals(AdapterError.NotAvailable("Invalid authorization code"), platformError(400, """{"error_type":"OAuthException","code":400,"error_message":"Invalid authorization code"}"""))
        assertEquals(AdapterError.NotAvailable("The code expired"), platformError(400, """{"error":"invalid_grant","error_description":"The code expired"}"""))
        assertEquals(AdapterError.NotAvailable("HTTP 500"), platformError(500, "not json"))
    }

    @Test
    fun missingSettings_namesExactlyWhatToAdd() {
        val none = PlatformKeys("", "")
        assertEquals(listOf("INSTAGRAM_APP_ID", "INSTAGRAM_APP_SECRET", "OAUTH_REDIRECT_URL"), missingSettings(ConnectPlatform.INSTAGRAM, none, ""))
        assertEquals(listOf("TIKTOK_CLIENT_SECRET"), missingSettings(ConnectPlatform.TIKTOK, PlatformKeys("key", ""), "https://x"))
        assertTrue(missingSettings(ConnectPlatform.FACEBOOK, PlatformKeys("id", "secret"), "https://x").isEmpty()) // the configuration id is optional
        assertTrue(missingSettings(ConnectPlatform.YOUTUBE, none, "").isEmpty())
    }

    @Test
    fun metaDates_andHashtags_areReadFromRealCaptions() {
        assertEquals("2026-11-01T10:00:00Z", metaTimeToIso("2026-11-01T10:00:00+0000"))
        assertEquals("2026-11-01T05:00:00Z", metaTimeToIso("2026-11-01T10:30:00+0530"))
        assertEquals(null, metaTimeToIso("yesterday"))
        assertEquals(listOf("coding", "हिंदी"), hashtagsOf("Learn #Coding fast #coding #हिंदी"))
        assertEquals("First line", firstLine("\n First line \nsecond"))
    }
}
