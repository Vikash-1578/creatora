package com.creatora.app.data.grow

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.creatora.app.domain.grow.MyChannel
import com.creatora.app.domain.repository.MyChannelRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreMyChannelRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : MyChannelRepository {

    override val channel: Flow<MyChannel?> = dataStore.data.map { prefs ->
        val id = prefs[ID]?.takeIf { it.isNotEmpty() } ?: return@map null
        MyChannel(id, prefs[TITLE].orEmpty())
    }

    override suspend fun set(channel: MyChannel) {
        dataStore.edit {
            it[ID] = channel.id
            it[TITLE] = channel.title
        }
    }

    override suspend fun clear() {
        dataStore.edit {
            it.remove(ID)
            it.remove(TITLE)
        }
    }

    private companion object {
        val ID = stringPreferencesKey("my_channel_id")
        val TITLE = stringPreferencesKey("my_channel_title")
    }
}
