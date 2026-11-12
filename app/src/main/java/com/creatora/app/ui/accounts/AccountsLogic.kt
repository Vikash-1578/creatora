package com.creatora.app.ui.accounts

import com.creatora.app.domain.account.AccountConnector
import com.creatora.app.domain.account.AdapterError
import com.creatora.app.domain.account.ChannelPreview
import com.creatora.app.domain.account.ConnectOutcome
import com.creatora.app.domain.account.ConnectPlatform
import com.creatora.app.domain.account.ConnectedAccount
import com.creatora.app.domain.account.LookupResult
import com.creatora.app.domain.platform.LoginOutcome

/** Why something on the connect screens did not work, as a message the screen can show. */
enum class ConnectProblem { NOT_AUTHORIZED, NETWORK, QUOTA, NOT_FOUND, NOT_AVAILABLE, UNRECOGNISED, SAVE_FAILED, NOT_CONFIGURED, LOGIN_FAILED }

fun AdapterError.toProblem() = when (this) {
    AdapterError.NotAuthorized, AdapterError.PermissionMissing -> ConnectProblem.NOT_AUTHORIZED
    AdapterError.Network -> ConnectProblem.NETWORK
    AdapterError.QuotaExceeded -> ConnectProblem.QUOTA
    AdapterError.NotFound -> ConnectProblem.NOT_FOUND
    is AdapterError.NotAvailable -> ConnectProblem.NOT_AVAILABLE
}

/** What the add-account panel shows after a platform sign-in: nothing to say, or the real reason it did not work. */
fun LoginOutcome.toLookup(): LookupUi = when (this) {
    is LoginOutcome.Signed, LoginOutcome.Cancelled -> LookupUi()
    is LoginOutcome.NotConfigured -> LookupUi(problem = ConnectProblem.NOT_CONFIGURED, detail = missing.joinToString(", "))
    is LoginOutcome.Failed -> LookupUi(problem = ConnectProblem.LOGIN_FAILED, detail = reason)
}

/** The result of pasting a link or id: what the "add an account" panel shows. */
data class LookupUi(
    val busy: Boolean = false,
    val problem: ConnectProblem? = null,
    /** The names of the missing build settings (NOT_CONFIGURED) or the platform's own words (LOGIN_FAILED). */
    val detail: String? = null,
    val linkSaved: Boolean = false,
    /** A channel found by the lookup, waiting for "Is this you?". */
    val pasted: ChannelPreview? = null,
    /** The plan's account limit was hit. */
    val planLimitMax: Int? = null,
)

/**
 * Looks up [input]. A link-only platform is saved straight away (only the link is kept) and returned as the second
 * value. Needs the signed-in [uid] for that; without one the link is not saved.
 */
suspend fun AccountConnector.lookupForPanel(uid: String?, input: String, platform: ConnectPlatform?): Pair<LookupUi, ConnectedAccount?> =
    when (val result = lookup(input, platform)) {
        is LookupResult.Found -> LookupUi(pasted = result.preview) to null
        LookupResult.Unrecognised -> LookupUi(problem = ConnectProblem.UNRECOGNISED) to null
        is LookupResult.Failed -> LookupUi(problem = result.error.toProblem()) to null
        is LookupResult.LinkOnly -> {
            val ref = result.ref
            val account = ConnectedAccount(
                platform = ref.platform,
                id = ref.handleOrId.ifEmpty { ref.url.orEmpty() },
                title = ref.handleOrId.ifEmpty { ref.platform.name },
                link = ref.url,
            )
            if (uid == null) {
                LookupUi() to null
            } else when (val outcome = connect(uid, account)) {
                is ConnectOutcome.PlanLimit -> LookupUi(planLimitMax = outcome.max) to null
                else -> LookupUi(linkSaved = true) to account
            }
        }
    }
