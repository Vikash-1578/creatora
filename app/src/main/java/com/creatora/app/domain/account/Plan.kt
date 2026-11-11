package com.creatora.app.domain.account

import java.util.Calendar
import java.util.TimeZone

/** The user's plan. The limits are placeholders for the manager to confirm. */
enum class Plan(val aiLimit: Int, val maxAccounts: Int) {
    FREE(aiLimit = 50, maxAccounts = 1),
    PRO(aiLimit = 1000, maxAccounts = 5);

    companion object {
        fun fromName(name: String?): Plan = entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: FREE
    }
}

enum class UsageLevel { OK, BANNER, BLOCKED }

/** [percent] is rounded down, so 44 of 50 is 88% and 45 of 50 is 90%. */
data class UsageStatus(val used: Int, val limit: Int, val percent: Int) {
    /** From 91% to 99% a banner stays on screen. */
    val level: UsageLevel
        get() = when {
            percent >= 100 -> UsageLevel.BLOCKED
            percent > DIALOG_PERCENT -> UsageLevel.BANNER
            else -> UsageLevel.OK
        }

    /** At 90% and above (until the limit) the user gets a one-time dialog each month. */
    val warnsWithDialog: Boolean get() = percent in DIALOG_PERCENT until 100

    companion object {
        const val DIALOG_PERCENT = 90
    }
}

fun usageStatus(used: Int, limit: Int): UsageStatus {
    val percent = if (limit <= 0) 100 else (used.coerceAtLeast(0) * 100L / limit).toInt().coerceAtMost(100)
    return UsageStatus(used.coerceAtLeast(0), limit, percent)
}

/** The month usage is counted in, as yyyyMM in UTC so the phone and the server agree. */
fun monthKey(nowMillis: Long): String {
    val c = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = nowMillis }
    return "%04d%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
}
