package com.creatora.app.testutil

import com.creatora.app.domain.account.Plan
import com.creatora.app.domain.account.PlanResolver
import com.creatora.app.domain.create.ImageGenerator
import com.creatora.app.domain.create.ImageResult
import com.creatora.app.domain.create.LinkReader
import com.creatora.app.domain.create.LinkResult
import com.creatora.app.domain.repository.UserDoc

class FakeImageGenerator : ImageGenerator {
    var result: ImageResult = ImageResult.Ok("file:///ai.png")
    val prompts = mutableListOf<String>()
    override suspend fun generate(prompt: String): ImageResult {
        prompts += prompt
        return result
    }
}

class FakeLinkReader(var result: LinkResult = LinkResult.Invalid) : LinkReader {
    val urls = mutableListOf<String>()
    override suspend fun read(url: String): LinkResult {
        urls += url
        return result
    }
}

/** A signed-in user on [plan], with the daily and monthly counters, for ViewModels that read limits. */
class PlanKit(plan: Plan = Plan.PRO, signedIn: Boolean = true) {
    val auth = FakeAuthService(if (signedIn) TestUser else null)
    val users = FakeUserRepository().apply { put(UserDoc("uid1", "Asha", "a@x.com", null, plan, "en", 1)) }
    val billing = FakeBillingRepository()
    val plans = PlanResolver(users, billing)
    val daily = FakeDailyUsage()
    val usage = FakeUsageRepository()
    val generator = FakeImageGenerator()
}
