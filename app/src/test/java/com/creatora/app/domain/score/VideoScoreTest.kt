package com.creatora.app.domain.score

import com.creatora.app.domain.account.Plan
import com.creatora.app.domain.model.Project
import com.creatora.app.domain.model.VideoFormat
import com.creatora.app.domain.model.WizardStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoScoreTest {
    private fun project(title: String = "Dal in ten minutes", hook: String = "Can you cook dal in 10 minutes?", body: String = "Boil the lentils. Temper the spices.", format: VideoFormat = VideoFormat.SHORTS_REELS) =
        Project(title = title, format = format, hook = hook, structure = body, callToAction = "Follow for more.")

    private fun check(score: VideoScore, id: CheckId) = score.checks.single { it.id == id }

    @Test
    fun aGoodShort_passesEveryCheckThatCanRun() {
        val score = videoScore(project(), Plan.FREE, importedAssets = 2, licensedAssets = 2)

        assertEquals(CheckStatus.PASS, check(score, CheckId.TITLE).status)
        assertEquals(CheckStatus.PASS, check(score, CheckId.HOOK).status)
        assertEquals(CheckStatus.PASS, check(score, CheckId.KEYWORD).status) // "minutes" is in the opening
        assertEquals(CheckStatus.PASS, check(score, CheckId.LICENCES).status)
        assertEquals(CheckStatus.NOT_CHECKED, check(score, CheckId.THUMBNAIL).status) // never guessed
        assertTrue(score.percent!! >= 80)
    }

    @Test
    fun theTitle_isCheckedForLength_andCapitals() {
        assertEquals(CheckStatus.FAIL, check(videoScore(project(title = "Dal"), Plan.PRO, 0, 0), CheckId.TITLE).status)
        assertEquals(Reason.TitleLength(3), check(videoScore(project(title = "Dal"), Plan.PRO, 0, 0), CheckId.TITLE).reason)
        assertEquals(CheckStatus.WARN, check(videoScore(project(title = "d".repeat(80)), Plan.PRO, 0, 0), CheckId.TITLE).status)
        assertEquals(CheckStatus.FAIL, check(videoScore(project(title = "d".repeat(101)), Plan.PRO, 0, 0), CheckId.TITLE).status)
        assertEquals(Reason.TitleCaps, check(videoScore(project(title = "BEST DAL RECIPE"), Plan.PRO, 0, 0), CheckId.TITLE).reason)
        assertEquals(WizardStep.BRIEF, check(videoScore(project(title = "Dal"), Plan.PRO, 0, 0), CheckId.TITLE).fix)
    }

    @Test
    fun theHook_mustBeShort_andMakeAPromiseQuestionOrNumber() {
        val long = "word ".repeat(30).trim() + "."
        assertEquals(CheckStatus.WARN, check(videoScore(project(hook = long), Plan.PRO, 0, 0), CheckId.HOOK).status)
        val flat = check(videoScore(project(hook = "This is a recipe. It is for dal."), Plan.PRO, 0, 0), CheckId.HOOK)
        assertEquals(Reason.HookNoPromise, flat.reason)
        assertEquals(WizardStep.SCRIPT, flat.fix)
        assertEquals(CheckStatus.PASS, check(videoScore(project(hook = "Here are 3 tricks. Watch."), Plan.PRO, 0, 0), CheckId.HOOK).status)
    }

    @Test
    fun length_isCheckedAgainstThePlanAndTheFormatGuide() {
        val long = "word ".repeat(100).trim() + "." // 100 words = 40 seconds
        val free = check(videoScore(project(body = long), Plan.FREE, 0, 0), CheckId.LENGTH)
        assertEquals(CheckStatus.FAIL, free.status)
        assertTrue(free.reason is Reason.LengthOverPlan)
        assertEquals(CheckStatus.PASS, check(videoScore(project(body = long), Plan.PRO, 0, 0), CheckId.LENGTH).status) // 44 seconds is inside the Shorts guide and Pro's limit
        val tinyLong = check(videoScore(project(body = "Short.", format = VideoFormat.LONG_VIDEO), Plan.PRO, 0, 0), CheckId.LENGTH)
        assertEquals(CheckStatus.WARN, tinyLong.status) // a long video of a few seconds is outside its guide
    }

    @Test
    fun theMainWordOfTheTitle_shouldAppearInTheOpening() {
        assertEquals("lentils", mainWord("Why the lentils matter"))
        val missing = check(videoScore(project(title = "Perfect lentils tonight", hook = "Can you cook dal in 10 minutes?"), Plan.PRO, 0, 0), CheckId.KEYWORD)
        assertEquals(Reason.KeywordMissing("perfect"), missing.reason) // the longest word is "perfect" here
        assertEquals(CheckStatus.WARN, missing.status)
    }

    @Test
    fun unlicensedFootage_isFlagged_onlyWhenLicensedFootageIsRequired() {
        val flagged = check(videoScore(project(), Plan.PRO, importedAssets = 3, licensedAssets = 1), CheckId.LICENCES)
        assertEquals(Reason.LicencesMissing(2), flagged.reason)
        assertEquals(WizardStep.FOOTAGE, flagged.fix)
        assertEquals(CheckStatus.PASS, check(videoScore(project().copy(licensedFootageOnly = false), Plan.PRO, 3, 1), CheckId.LICENCES).status)
    }

    @Test
    fun anEmptyProject_hasNoScore_ratherThanAMadeUpOne() {
        val score = videoScore(Project(title = "", format = VideoFormat.SHORTS_REELS), Plan.FREE, 0, 0)

        assertEquals(CheckStatus.NOT_CHECKED, check(score, CheckId.HOOK).status)
        assertEquals(CheckStatus.NOT_CHECKED, check(score, CheckId.LENGTH).status)
        assertTrue(score.percent != null) // the title and licences still count
    }

    @Test
    fun noCheckCanRun_meansNoPercent() {
        assertNull(VideoScore(emptyList(), null).percent)
    }
}
