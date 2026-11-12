package com.creatora.app.domain.account

import com.creatora.app.domain.repository.BillingRepository
import com.creatora.app.domain.repository.UserRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** The plan in force: Pro when Play says so on this phone, otherwise what the user's document says (Free by default). */
class PlanResolver @Inject constructor(private val users: UserRepository, private val billing: BillingRepository) {
    suspend fun plan(uid: String): Plan =
        if (billing.state.first().hasPro) Plan.PRO else users.observe(uid).first()?.plan ?: Plan.FREE
}
