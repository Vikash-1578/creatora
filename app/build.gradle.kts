import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Firebase is configured by google-services.json (a GitHub secret in CI, never committed). Without the
// file the app still builds; cloud features then say they are not set up.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

// Platform sign-in settings (Instagram, Facebook, TikTok). Read from local.properties or an environment variable
// (a GitHub Actions secret in CI); never committed. A missing value just leaves that platform's sign-in unset.
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.reader().use { load(it) }
}
fun platformSetting(name: String): String =
    (providers.environmentVariable(name).orNull ?: localProps.getProperty(name, "")).trim().replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "com.creatora.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.creatora.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Dev fallback: use the user-entered AI keys (M7 providers) instead of Firebase AI Logic.
        // Off by default. Turn on with -PdevAiKeys=true.
        listOf(
            "OAUTH_REDIRECT_URL", "INSTAGRAM_APP_ID", "INSTAGRAM_APP_SECRET", "FACEBOOK_APP_ID", "FACEBOOK_APP_SECRET",
            "FACEBOOK_CONFIG_ID", "TIKTOK_CLIENT_KEY", "TIKTOK_CLIENT_SECRET",
        ).forEach { buildConfigField("String", it, "\"${platformSetting(it)}\"") }

        buildConfigField("boolean", "DEV_AI_KEYS", providers.gradleProperty("devAiKeys").orElse("false").get())
    }

    // A fixed debug keystore (debug only, not a secret) keeps the signing SHA-1 stable for Firebase and Google sign-in.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        // Only the Phase A strings have Hindi and Hinglish so far; the rest falls back to English until the
        // full translation pass.
        disable += "MissingTranslation"
    }

    testOptions {
        // Robolectric needs merged Android resources for Room/DataStore tests on the JVM.
        unitTests.isIncludeAndroidResources = true
        // Print failures in full so they can be read from the CI log.
        unitTests.all {
            it.testLogging {
                events("failed")
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            }
        }
    }
}

ksp {
    // Committed Room schemas: CI fails if a schema change is not committed (needed for migrations).
    arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.coil.compose)
    // Coil 3 loads http images only with a network module; without it channel pictures stay empty circles.
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.effect)
    implementation(libs.androidx.media3.common)
    implementation(libs.coil.video)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.ai)
    implementation(libs.firebase.appcheck.playintegrity)
    implementation(libs.firebase.crashlytics)
    debugImplementation(libs.firebase.appcheck.debug)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.google.id)
    implementation(libs.play.services.auth)
    implementation(libs.play.billing)
    implementation(libs.androidx.work.runtime)
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
