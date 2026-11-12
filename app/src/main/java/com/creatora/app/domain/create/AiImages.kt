package com.creatora.app.domain.create

import com.creatora.app.domain.account.PlanLimits
import com.creatora.app.domain.account.PlanResolver
import com.creatora.app.domain.account.monthKey
import com.creatora.app.domain.home.dayKey
import com.creatora.app.domain.model.Asset
import com.creatora.app.domain.model.AssetType
import com.creatora.app.domain.model.LicenceEntry
import com.creatora.app.domain.repository.AssetRepository
import com.creatora.app.domain.repository.AuthService
import com.creatora.app.domain.repository.DailyUsageRepository
import com.creatora.app.domain.repository.LicenceRepository
import com.creatora.app.domain.repository.UsageRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

sealed interface ImageResult {
    /** [uri] is a file in app storage. */
    data class Ok(val uri: String) : ImageResult
    data object Failed : ImageResult
}

/** Makes one picture from a prompt on a server (the key never leaves it). */
fun interface ImageGenerator {
    suspend fun generate(prompt: String): ImageResult
}

sealed interface ImageOutcome {
    data class Ok(val assetId: Long) : ImageOutcome
    data class DailyLimit(val max: Int) : ImageOutcome
    data object CreditsUsedUp : ImageOutcome
    data object SignInRequired : ImageOutcome
    data object Failed : ImageOutcome
}

/**
 * AI images for a project. The plan's daily allowance and the monthly credits are checked first; a picture is saved as an
 * asset of the project with a licence entry "AI generated", and only a successful picture is counted.
 */
class AiImages @Inject constructor(
    private val generator: ImageGenerator,
    private val assets: AssetRepository,
    private val licences: LicenceRepository,
    private val usage: UsageRepository,
    private val daily: DailyUsageRepository,
    private val plans: PlanResolver,
    private val auth: AuthService,
    private val now: () -> Long,
) {
    @Inject
    constructor(
        generator: ImageGenerator, assets: AssetRepository, licences: LicenceRepository, usage: UsageRepository,
        daily: DailyUsageRepository, plans: PlanResolver, auth: AuthService,
    ) : this(generator, assets, licences, usage, daily, plans, auth, System::currentTimeMillis)

    suspend fun make(projectId: Long, prompt: String): ImageOutcome {
        val uid = auth.user.first()?.uid ?: return ImageOutcome.SignInRequired
        val plan = plans.plan(uid)
        val day = dayKey(now())
        val month = monthKey(now())
        val creditsLeft = plan.aiLimit - usage.observe(uid, month).first()
        when (val gate = imageGate(plan, daily.observe(uid, day).first().aiImages, creditsLeft)) {
            ImageGate.Allowed -> Unit
            is ImageGate.DailyLimit -> return ImageOutcome.DailyLimit(gate.max)
            ImageGate.CreditsUsedUp -> return ImageOutcome.CreditsUsedUp
        }
        val image = generator.generate(prompt) as? ImageResult.Ok ?: return ImageOutcome.Failed
        val assetId = assets.save(Asset(uri = image.uri, type = AssetType.IMAGE, name = "AI image", mimeType = "image/png", projectId = projectId))
        licences.save(LicenceEntry(assetId = assetId, source = "Creatora AI", licence = "AI generated"))
        usage.increment(uid, month, PlanLimits.costs.image)
        daily.add(uid, day, aiImages = 1)
        return ImageOutcome.Ok(assetId)
    }
}
