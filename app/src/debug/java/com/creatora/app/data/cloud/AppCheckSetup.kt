package com.creatora.app.data.cloud

import android.util.Log
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug builds use the App Check debug provider. Asking for a token right away makes the provider print its debug
 * secret to logcat (tag `DebugAppCheckProvider`, line "Enter this debug secret into the allow list..."). Register that
 * secret in Firebase console > App Check > Apps > Manage debug tokens, or Firebase AI calls from debug builds are rejected.
 */
object AppCheckSetup {
    private const val TAG = "CreatoraAppCheck"

    fun install() {
        val appCheck = FirebaseAppCheck.getInstance()
        appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
        appCheck.getAppCheckToken(false).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.i(TAG, "App Check: debug token accepted.")
            } else {
                Log.w(TAG, "App Check: debug token not accepted yet. Register the debug secret printed under tag DebugAppCheckProvider in the Firebase console.")
            }
        }
    }
}
