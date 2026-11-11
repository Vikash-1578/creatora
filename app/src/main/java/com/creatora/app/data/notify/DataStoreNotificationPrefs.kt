package com.creatora.app.data.notify

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.creatora.app.domain.account.NotificationCategory
import com.creatora.app.domain.account.NotificationPrefs
import com.creatora.app.domain.repository.NotificationPrefsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class DataStoreNotificationPrefs @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : NotificationPrefsRepository {

    override val prefs: Flow<NotificationPrefs> = dataStore.data.map { stored ->
        NotificationPrefs(
            enabled = NotificationCategory.entries.filter { stored[enabledKey(it)] ?: true }.toSet(),
            milestoneViews = stored[MILESTONE] ?: NotificationPrefs.DEFAULT_MILESTONE,
        )
    }

    override suspend fun setEnabled(category: NotificationCategory, enabled: Boolean) {
        dataStore.edit { it[enabledKey(category)] = enabled }
    }

    override suspend fun setMilestone(views: Long) {
        dataStore.edit { it[MILESTONE] = views.coerceAtLeast(1) }
    }

    override suspend fun lastValue(key: String): Long? = dataStore.data.first()[longPreferencesKey("last_$key")]

    override suspend fun setLastValue(key: String, value: Long) {
        dataStore.edit { it[longPreferencesKey("last_$key")] = value }
    }

    private companion object {
        val MILESTONE = longPreferencesKey("notify_milestone_views")
        fun enabledKey(category: NotificationCategory) = booleanPreferencesKey("notify_${category.channelId}")
    }
}
