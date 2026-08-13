package ru.anidesk.app.core.network

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "anidesk_session")

class SessionStore(private val context: Context) {

    private val TOKEN = stringPreferencesKey("token")
    private val PROFILE_ID = intPreferencesKey("profile_id")

    val token: Flow<String?> = context.sessionDataStore.data.map { it[TOKEN] }

    val profileId: Flow<Int?> = context.sessionDataStore.data.map { it[PROFILE_ID] }

    suspend fun save(profileId: Int, token: String) {
        context.sessionDataStore.edit {
            it[PROFILE_ID] = profileId
            it[TOKEN] = token
        }
    }

    suspend fun clear() {
        context.sessionDataStore.edit { it.clear() }
    }
}
