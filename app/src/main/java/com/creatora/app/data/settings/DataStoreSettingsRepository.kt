package com.creatora.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
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

    private companion object {
        val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
    }
}
