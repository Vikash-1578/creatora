package com.creatora.app.domain.create

import com.creatora.app.domain.account.Plan
import com.creatora.app.domain.account.PlanLimits
import com.creatora.app.domain.model.StepState
import com.creatora.app.domain.model.VideoFormat
import com.creatora.app.domain.model.WizardStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateLogicTest {
    @Test
    fun planLimits_holdEveryFreeAndProNumberInOnePlace() {
        val free = Plan.FREE.limits
        assertEquals(setOf(VideoFormat.SHORTS_REELS), free.formats)
        assertEquals(1, free.videosPerDay)
        assertEquals(30, free.maxVideoSeconds)
        assertEquals(5, free.aiImagesPerDay)
        assertFalse(free.aiVideoClips)
        assertFalse(free.premiumVoices)
        assertEquals(1, free.publishesPerDay)
        assertEquals(1, free.platformsPerPost)

        val pro = Plan.PRO.limits
        assertTrue(pro.premiumVoices && pro.aiVideoClips)
        assertEquals(VideoFormat.entries.size - 1, pro.formats.size) // everything but the avatar video
        assertFalse(VideoFormat.AVATAR_VIDEO in pro.formats)
        assertTrue(pro.maxVideoSeconds > free.maxVideoSeconds && pro.videosPerDay > free.videosPerDay)

        // The older Plan properties read the same table.
        assertEquals(free.aiActionsPerMonth, Plan.FREE.aiLimit)
        assertEquals(pro.competitors, Plan.PRO.maxCompetitors)
        assertEquals(3, PlanLimits.costs.image)
    }

    @Test
    fun formats_areOpen_lockedBehindPro_orComingLater() {
        assertEquals(FormatAccess.OPEN, formatAccess(Plan.FREE, VideoFormat.SHORTS_REELS))
        VideoFormat.entries.filter { it != VideoFormat.SHORTS_REELS && it != VideoFormat.AVATAR_VIDEO }.forEach {
            assertEquals(FormatAccess.PRO, formatAccess(Plan.FREE, it))
            assertEquals(FormatAccess.OPEN, formatAccess(Plan.PRO, it))
        }
        assertEquals(FormatAccess.COMING_LATER, formatAccess(Plan.FREE, VideoFormat.AVATAR_VIDEO))
        assertEquals(FormatAccess.COMING_LATER, formatAccess(Plan.PRO, VideoFormat.AVATAR_VIDEO))
    }

    @Test
    fun renderGate_checksFormat_length_andTheDailyLimit() {
        assertEquals(RenderGate.Allowed, renderGate(Plan.FREE, VideoFormat.SHORTS_REELS, 30, videosToday = 0, alreadyCounted = false))
        assertEquals(RenderGate.FormatLocked, renderGate(Plan.FREE, VideoFormat.LONG_VIDEO, 20, 0, false))
        assertEquals(RenderGate.TooLong(31, 30), renderGate(Plan.FREE, VideoFormat.SHORTS_REELS, 31, 0, false))
        assertEquals(RenderGate.DailyLimit(1), renderGate(Plan.FREE, VideoFormat.SHORTS_REELS, 20, 1, false))
        assertEquals(RenderGate.Allowed, renderGate(Plan.FREE, VideoFormat.SHORTS_REELS, 20, 1, alreadyCounted = true)) // a re-render
        assertEquals(RenderGate.TooLong(31, 30), renderGate(Plan.FREE, VideoFormat.SHORTS_REELS, 31, 1, alreadyCounted = true))
        assertEquals(RenderGate.Allowed, renderGate(Plan.PRO, VideoFormat.LONG_VIDEO, 500, 9, false))
        assertEquals(RenderGate.DailyLimit(10), renderGate(Plan.PRO, VideoFormat.LONG_VIDEO, 500, 10, false))
    }

    @Test
    fun imageGate_isPerDay_andNeedsCredits() {
        assertEquals(ImageGate.Allowed, imageGate(Plan.FREE, imagesToday = 4, creditsLeft = 3))
        assertEquals(ImageGate.DailyLimit(5), imageGate(Plan.FREE, 5, 40))
        assertEquals(ImageGate.CreditsUsedUp, imageGate(Plan.FREE, 0, 2))
        assertEquals(ImageGate.Allowed, imageGate(Plan.PRO, 5, 100))
    }

    @Test
    fun autoPlan_showsTheCost_andShrinksToWhatIsLeft() {
        assertEquals(AutoPlan(2, 3, 11), autoPlan(Plan.FREE, imagesToday = 0, creditsLeft = 50)) // 2 text + 3 images x 3 credits
        assertEquals(2, autoPlan(Plan.FREE, imagesToday = 3, creditsLeft = 50).images) // 2 of today's 5 are left
        assertEquals(0, autoPlan(Plan.FREE, imagesToday = 5, creditsLeft = 50).images)
        assertEquals(1, autoPlan(Plan.FREE, imagesToday = 0, creditsLeft = 5).images) // 5 - 2 text = 3 credits = one picture
    }

    @Test
    fun editingAStep_marksLaterFinishedStepsOutOfDate_withoutTouchingOthers() {
        val states = mapOf(
            WizardStep.BRIEF to StepState.APPROVED, WizardStep.SCRIPT to StepState.APPROVED,
            WizardStep.CAST to StepState.EDITED, WizardStep.VOICE to StepState.AI_DRAFT,
        )

        val changed = stepChanged(states, WizardStep.BRIEF, byAi = false)

        assertEquals(StepState.EDITED, changed[WizardStep.BRIEF])
        assertEquals(StepState.OUT_OF_DATE, changed[WizardStep.SCRIPT])
        assertEquals(StepState.OUT_OF_DATE, changed[WizardStep.CAST])
        assertEquals(StepState.AI_DRAFT, changed[WizardStep.VOICE]) // an unapproved draft is not "out of date"
        assertEquals(StepState.AI_DRAFT, stepChanged(emptyMap(), WizardStep.SCRIPT, byAi = true)[WizardStep.SCRIPT])
    }

    @Test
    fun approving_keepsAnEditedStepEdited() {
        assertEquals(StepState.APPROVED, stepApproved(emptyMap(), WizardStep.BRIEF)[WizardStep.BRIEF])
        assertEquals(StepState.APPROVED, stepApproved(mapOf(WizardStep.BRIEF to StepState.AI_DRAFT), WizardStep.BRIEF)[WizardStep.BRIEF])
        assertEquals(StepState.EDITED, stepApproved(mapOf(WizardStep.BRIEF to StepState.EDITED), WizardStep.BRIEF)[WizardStep.BRIEF])
    }

    @Test
    fun stepStates_roundTripAndIgnoreGarbage() {
        val states = mapOf(WizardStep.BRIEF to StepState.APPROVED, WizardStep.VOICE to StepState.OUT_OF_DATE)

        assertEquals(states, decodeStates(encodeStates(states)))
        assertEquals(emptyMap<WizardStep, StepState>(), decodeStates(null))
        assertEquals(mapOf(WizardStep.BRIEF to StepState.EDITED), decodeStates("BRIEF=EDITED,NOPE=EDITED,SCRIPT=WHAT,junk"))
    }
}
