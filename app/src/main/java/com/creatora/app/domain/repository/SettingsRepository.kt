package com.creatora.app.domain.repository

import com.creatora.app.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

/** Persistent user settings. */
interface SettingsRepository {
    /** Emits the saved theme mode, or [ThemeMode.Default] if none was saved yet. */
    val themeMode: Flow<ThemeMode>

    suspend fun setThemeMode(mode: ThemeMode)

    /** The name shown on the Profile screen; empty until the user sets one. */
    val displayName: Flow<String>

    suspend fun setDisplayName(name: String)

    /** The language the user chose; null until they pick one. */
    val language: Flow<com.creatora.app.domain.account.AppLanguage?>
    suspend fun setLanguage(language: com.creatora.app.domain.account.AppLanguage)

    val onboardingDone: Flow<Boolean>
    suspend fun setOnboardingDone(done: Boolean)

    /** How the AI should sound: friendly, professional or funny. */
    val aiTone: Flow<AiTone>
    suspend fun setAiTone(tone: AiTone)

    /** Whether the AI may use the creator profile and channel numbers in its answers. */
    val personalization: Flow<Boolean>
    suspend fun setPersonalization(on: Boolean)

    /** The month (yyyyMM) for which the 90% usage dialog was already shown. */
    val usageDialogMonth: Flow<String?>
    suspend fun setUsageDialogMonth(month: String)
}

enum class AiTone { FRIENDLY, PROFESSIONAL, FUNNY }
