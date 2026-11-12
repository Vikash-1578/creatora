package com.creatora.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Starts the real app on a fresh install and walks the first onboarding steps. It catches a crash on launch, in Hilt wiring
 * or in the first screens before a change is merged. Signing in with Google needs a person, so the walk stops at the sign-in
 * button; the screens after it are covered by the ViewModel tests.
 */
@RunWith(AndroidJUnit4::class)
class OnboardingSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test
    fun aFreshInstall_showsTheLanguageStep_thenTheWelcomeStepWithGoogleSignIn() {
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithTextCount(text(R.string.onboarding_language_title)) > 0 }
        compose.onNodeWithText(text(R.string.onboarding_language_title)).assertExists()

        compose.onNodeWithText(text(R.string.action_continue)).performClick()

        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithTextCount(text(R.string.signin_google)) > 0 }
        compose.onNodeWithText(text(R.string.signin_google)).assertExists()
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onAllNodesWithTextCount(value: String): Int =
        onAllNodes(androidx.compose.ui.test.hasText(value)).fetchSemanticsNodes().size

    private companion object {
        const val TIMEOUT_MS = 15_000L
    }
}
