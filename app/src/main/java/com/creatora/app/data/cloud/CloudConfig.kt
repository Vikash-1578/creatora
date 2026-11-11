package com.creatora.app.data.cloud

import android.content.Context
import com.google.firebase.FirebaseApp
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Whether this build has a Firebase project behind it (google-services.json was present at build time). */
@Singleton
class CloudConfig @Inject constructor(@ApplicationContext private val context: Context) {
    val isConfigured: Boolean get() = FirebaseApp.getApps(context).isNotEmpty()

    /** The OAuth web client ID the Google Services plugin generates. Read by name so the build works without the plugin. */
    val webClientId: String?
        get() = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            .takeIf { it != 0 }
            ?.let(context::getString)
}
