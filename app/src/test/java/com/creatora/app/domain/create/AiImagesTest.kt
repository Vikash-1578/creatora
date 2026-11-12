package com.creatora.app.domain.create

import com.creatora.app.domain.account.Plan
import com.creatora.app.domain.account.monthKey
import com.creatora.app.domain.home.dayKey
import com.creatora.app.domain.model.AssetType
import com.creatora.app.testutil.FakeAssetRepository
import com.creatora.app.testutil.FakeLicenceRepository
import com.creatora.app.testutil.PlanKit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiImagesTest {
    private val assets = FakeAssetRepository()
    private val licences = FakeLicenceRepository()
    private val now = System.currentTimeMillis()
    private fun images(kit: PlanKit) = AiImages(kit.generator, assets, licences, kit.usage, kit.daily, kit.plans, kit.auth) { now }

    @Test
    fun aPicture_isSavedWithAnAiLicence_andCountedByDayAndByCredits() = runTest {
        val kit = PlanKit(Plan.FREE)

        val outcome = images(kit).make(projectId = 7, prompt = "A pot of dal")

        val assetId = (outcome as ImageOutcome.Ok).assetId
        val asset = assets.items.value.single()
        assertEquals(AssetType.IMAGE, asset.type)
        assertEquals(7L, asset.projectId)
        assertEquals("file:///ai.png", asset.uri)
        val licence = licences.items.value.single()
        assertEquals(assetId, licence.assetId)
        assertEquals("AI generated", licence.licence)
        assertEquals(1, kit.daily.observe("uid1", dayKey(now)).first().aiImages)
        assertEquals(3, kit.usage.observe("uid1", monthKey(now)).first()) // an image costs 3 credits
        assertEquals(listOf("A pot of dal"), kit.generator.prompts)
    }

    @Test
    fun theFreePlan_makesFivePerDay() = runTest {
        val kit = PlanKit(Plan.FREE)
        val service = images(kit)
        repeat(5) { assertTrue(service.make(1, "p$it") is ImageOutcome.Ok) }

        assertEquals(ImageOutcome.DailyLimit(5), service.make(1, "sixth"))
        assertEquals(5, kit.generator.prompts.size) // the sixth never reached the server
        assertEquals(5, assets.items.value.size)
    }

    @Test
    fun withoutCredits_nothingIsMade() = runTest {
        val kit = PlanKit(Plan.FREE)
        kit.usage.set("uid1", monthKey(now), 49) // 1 credit left of 50

        assertEquals(ImageOutcome.CreditsUsedUp, images(kit).make(1, "p"))
        assertTrue(kit.generator.prompts.isEmpty())
    }

    @Test
    fun aFailedPicture_isNotCounted_andSignedOutNeedsSignIn() = runTest {
        val kit = PlanKit(Plan.PRO)
        kit.generator.result = ImageResult.Failed

        assertEquals(ImageOutcome.Failed, images(kit).make(1, "p"))
        assertEquals(0, kit.daily.observe("uid1", dayKey(now)).first().aiImages)
        assertEquals(0, kit.usage.observe("uid1", monthKey(now)).first())
        assertTrue(assets.items.value.isEmpty())

        assertEquals(ImageOutcome.SignInRequired, images(PlanKit(signedIn = false)).make(1, "p"))
    }

    @Test
    fun pro_getsMorePerDay() = runTest {
        val kit = PlanKit(Plan.PRO)
        kit.daily.add("uid1", dayKey(now), aiImages = 5)

        assertTrue(images(kit).make(1, "p") is ImageOutcome.Ok)
    }
}
