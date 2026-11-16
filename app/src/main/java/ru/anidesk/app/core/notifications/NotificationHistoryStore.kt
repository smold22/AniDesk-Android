package ru.anidesk.app.core.notifications

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.notificationHistoryDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "anidesk_notification_history",
)

@Serializable
data class StoredNotification(
    /** Stable key, e.g. "episode:<serverId>" or "episode-local:<releaseId>:<pos>" */
    val key: String,
    val type: String, // "episode" | "release"
    val title: String,
    val episodeName: String? = null,
    val releaseId: Int = 0,
    val image: String? = null,
    val timestamp: Long = 0L,
    val isNew: Boolean = false,
)

class NotificationHistoryStore(private val context: Context) {

    private val ITEMS = stringPreferencesKey("items")
    private val json = Json { ignoreUnknownKeys = true }

    val notifications: Flow<List<StoredNotification>> =
        context.notificationHistoryDataStore.data.map { prefs ->
            decode(prefs[ITEMS]).sortedByDescending { it.timestamp }
        }

    suspend fun getAll(): List<StoredNotification> = notifications.first()

    suspend fun upsertAll(newItems: List<StoredNotification>) {
        if (newItems.isEmpty()) return
        context.notificationHistoryDataStore.edit { prefs ->
            val current = decode(prefs[ITEMS]).associateBy { it.key }.toMutableMap()
            newItems.forEach { item ->
                val existing = current[item.key]
                // Keep newer timestamp/title details, preserve false once read locally if needed.
                current[item.key] = if (existing == null) item else existing.copy(
                    type = item.type.ifBlank { existing.type },
                    title = item.title.ifBlank { existing.title },
                    episodeName = item.episodeName ?: existing.episodeName,
                    releaseId = if (item.releaseId > 0) item.releaseId else existing.releaseId,
                    image = item.image ?: existing.image,
                    timestamp = maxOf(existing.timestamp, item.timestamp),
                    isNew = existing.isNew || item.isNew,
                )
            }
            val merged = current.values.sortedByDescending { it.timestamp }.take(MAX_ITEMS)
            prefs[ITEMS] = json.encodeToString(merged)
        }
    }

    suspend fun clear() {
        context.notificationHistoryDataStore.edit { prefs ->
            prefs.remove(ITEMS)
        }
    }

    suspend fun markAllRead() {
        context.notificationHistoryDataStore.edit { prefs ->
            val updated = decode(prefs[ITEMS]).map { it.copy(isNew = false) }
            prefs[ITEMS] = json.encodeToString(updated)
        }
    }

    private fun decode(raw: String?): List<StoredNotification> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<StoredNotification>>(raw) }.getOrDefault(emptyList())
    }

    companion object {
        private const val MAX_ITEMS = 300
    }
}
