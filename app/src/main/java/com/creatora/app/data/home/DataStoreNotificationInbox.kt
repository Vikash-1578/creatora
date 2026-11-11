package com.creatora.app.data.home

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.creatora.app.domain.account.NotificationCategory
import com.creatora.app.domain.repository.InboxEntry
import com.creatora.app.domain.repository.NotificationInboxRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/** The last 50 notifications, kept on the phone as one JSON string. */
class DataStoreNotificationInbox @Inject constructor(private val dataStore: DataStore<Preferences>) : NotificationInboxRepository {

    override val entries: Flow<List<InboxEntry>> = dataStore.data.map { decode(it[INBOX]) }

    override suspend fun add(category: NotificationCategory, title: String, text: String, at: Long) {
        dataStore.edit { prefs ->
            val next = listOf(InboxEntry(at, category, title, text, at)) + decode(prefs[INBOX])
            prefs[INBOX] = encode(next.take(LIMIT))
        }
    }

    override suspend fun clear() {
        dataStore.edit { it.remove(INBOX) }
    }

    private fun encode(list: List<InboxEntry>): String = JSONArray().also { array ->
        list.forEach { array.put(JSONObject().put("id", it.id).put("c", it.category.name).put("t", it.title).put("x", it.text).put("a", it.at)) }
    }.toString()

    private fun decode(json: String?): List<InboxEntry> = runCatching {
        val array = JSONArray(json ?: return emptyList())
        (0 until array.length()).mapNotNull { i ->
            val o = array.getJSONObject(i)
            val category = NotificationCategory.entries.firstOrNull { it.name == o.getString("c") } ?: return@mapNotNull null
            InboxEntry(o.getLong("id"), category, o.getString("t"), o.getString("x"), o.getLong("a"))
        }
    }.getOrDefault(emptyList())

    private companion object {
        val INBOX = stringPreferencesKey("notification_inbox")
        const val LIMIT = 50
    }
}
