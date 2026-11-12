package com.creatora.app.data.cloud

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/** Release builds prove they are the real app with Play Integrity. */
object AppCheckSetup {
    /** Only debug builds have a debug secret. */
    @Suppress("UNUSED_PARAMETER")
    fun debugSecret(context: android.content.Context): String? = null

    fun install() {
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
    }
}
