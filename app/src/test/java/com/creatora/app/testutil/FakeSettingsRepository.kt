package com.creatora.app.testutil

import com.creatora.app.domain.model.ThemeMode
import com.creatora.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSettingsRepository(initial: ThemeMode = ThemeMode.Default) : SettingsRepository {
    override val themeMode = MutableStateFlow(initial)
    override suspend fun setThemeMode(mode: ThemeMode) {
        themeMode.value = mode
    }

    override val displayName = MutableStateFlow("")
    override suspend fun setDisplayName(name: String) {
        displayName.value = name.trim()
    }

    override val language = MutableStateFlow<com.creatora.app.domain.account.AppLanguage?>(null)
    override suspend fun setLanguage(language: com.creatora.app.domain.account.AppLanguage) {
        this.language.value = language
    }

    override val onboardingDone = MutableStateFlow(false)
    override suspend fun setOnboardingDone(done: Boolean) {
        onboardingDone.value = done
    }

    override val onboardingStep = MutableStateFlow<String?>(null)
    override suspend fun setOnboardingStep(step: String) {
        onboardingStep.value = step
    }

    override val aiTone = MutableStateFlow(com.creatora.app.domain.repository.AiTone.FRIENDLY)
    override suspend fun setAiTone(tone: com.creatora.app.domain.repository.AiTone) {
        aiTone.value = tone
    }

    override val personalization = MutableStateFlow(true)
    override suspend fun setPersonalization(on: Boolean) {
        personalization.value = on
    }

    override val usageDialogMonth = MutableStateFlow<String?>(null)
    override suspend fun setUsageDialogMonth(month: String) {
        usageDialogMonth.value = month
    }
}
