package com.creatora.app.data.home

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.creatora.app.domain.home.StreakUnit
import com.creatora.app.domain.repository.HomePrefsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class DataStoreHomePrefs @Inject constructor(private val dataStore: DataStore<Preferences>) : HomePrefsRepository {

    override val selectedKey: Flow<String?> = dataStore.data.map { it[SELECTED] }.distinctUntilChanged()
    override suspend fun setSelectedKey(key: String) {
        dataStore.edit { it[SELECTED] = key }
    }

    override val hideAmounts: Flow<Boolean> = dataStore.data.map { it[HIDE_AMOUNTS] ?: false }.distinctUntilChanged()
    override suspend fun setHideAmounts(hidden: Boolean) {
        dataStore.edit { it[HIDE_AMOUNTS] = hidden }
    }

    override val streakUnit: Flow<StreakUnit> =
        dataStore.data.map { prefs -> StreakUnit.entries.firstOrNull { it.name == prefs[STREAK_UNIT] } ?: StreakUnit.WEEKS }.distinctUntilChanged()
    override suspend fun setStreakUnit(unit: StreakUnit) {
        dataStore.edit { it[STREAK_UNIT] = unit.name }
    }

    override val region: Flow<String?> = dataStore.data.map { it[REGION] }.distinctUntilChanged()
    override suspend fun setRegion(code: String) {
        dataStore.edit { it[REGION] = code.uppercase() }
    }

    private companion object {
        val SELECTED = stringPreferencesKey("home_selected_account")
        val HIDE_AMOUNTS = booleanPreferencesKey("home_hide_amounts")
        val STREAK_UNIT = stringPreferencesKey("home_streak_unit")
        val REGION = stringPreferencesKey("home_region")
    }
}
