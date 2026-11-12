package com.creatora.app.data.cloud

import com.creatora.app.domain.repository.ErrorReporter
import com.google.firebase.crashlytics.FirebaseCrashlytics
import javax.inject.Inject

/** Sends handled errors to Firebase Crashlytics as non-fatal reports. Does nothing when Firebase is not set up in this build. */
class CrashlyticsErrorReporter @Inject constructor() : ErrorReporter {
    override fun record(error: Throwable) {
        runCatching { FirebaseCrashlytics.getInstance().recordException(error) }
    }
}
