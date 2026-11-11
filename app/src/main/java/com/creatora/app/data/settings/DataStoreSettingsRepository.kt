package com.creatora.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.creatora.app.domain.account.AppLanguage
import com.creatora.app.domain.repository.AiTone
import com.creatora.app.domain.model.ThemeMode
import com.creatora.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject

/** [SettingsRepository] backed by Jetpack Preferences DataStore. */
class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val themeMode: Flow<ThemeMode> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs ->
            prefs[KEY_THEME_MODE]
                ?.let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } }
                ?: ThemeMode.Default
        }
        .distinctUntilChanged()

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs -> prefs[KEY_THEME_MODE] = mode.name }
    }

    override val displayName: Flow<String> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs -> prefs[KEY_DISPLAY_NAME].orEmpty() }
        .distinctUntilChanged()

    override suspend fun setDisplayName(name: String) {
        dataStore.edit { prefs -> prefs[KEY_DISPLAY_NAME] = name.trim() }
    }

    override val language: Flow<AppLanguage?> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs -> AppLanguage.fromTag(prefs[KEY_LANGUAGE]) }
        .distinctUntilChanged()

    override suspend fun setLanguage(language: AppLanguage) {
        dataStore.edit { prefs -> prefs[KEY_LANGUAGE] = language.tag }
    }

    override val onboardingDone: Flow<Boolean> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs -> prefs[KEY_ONBOARDING_DONE] ?: false }
        .distinctUntilChanged()

    override suspend fun setOnboardingDone(done: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_ONBOARDING_DONE] = done }
    }

    override val aiTone: Flow<AiTone> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs -> AiTone.entries.firstOrNull { it.name == prefs[KEY_AI_TONE] } ?: AiTone.FRIENDLY }
        .distinctUntilChanged()

    override suspend fun setAiTone(tone: AiTone) {
        dataStore.edit { prefs -> prefs[KEY_AI_TONE] = tone.name }
    }

    override val personalization: Flow<Boolean> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs -> prefs[KEY_PERSONALIZATION] ?: true }
        .distinctUntilChanged()

    override suspend fun setPersonalization(on: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_PERSONALIZATION] = on }
    }

    override val usageDialogMonth: Flow<String?> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs -> prefs[KEY_USAGE_DIALOG_MONTH] }
        .distinctUntilChanged()

    override suspend fun setUsageDialogMonth(month: String) {
        dataStore.edit { prefs -> prefs[KEY_USAGE_DIALOG_MONTH] = month }
    }

    private companion object {
        val KEY_LANGUAGE = stringPreferencesKey("language")
        val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val KEY_AI_TONE = stringPreferencesKey("ai_tone")
        val KEY_PERSONALIZATION = booleanPreferencesKey("ai_personalization")
        val KEY_USAGE_DIALOG_MONTH = stringPreferencesKey("usage_dialog_month")
        val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
    }
}
