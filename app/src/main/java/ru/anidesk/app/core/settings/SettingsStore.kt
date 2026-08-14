package ru.anidesk.app.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "anidesk_settings")

class SettingsStore(private val context: Context) {

    private val THEME = intPreferencesKey("theme")
    private val DEFAULT_QUALITY = intPreferencesKey("default_quality")
    private val ORIENTATION_LOCK = booleanPreferencesKey("player_only_horizontal_orientation")
    private val AUTO_PLAY = booleanPreferencesKey("auto_play")
    private val VIEW_TYPE = intPreferencesKey("view_type")
    private val PLAYBACK_SPEED = intPreferencesKey("playback_speed")
    private val REWIND_TIME = intPreferencesKey("rewind_time")
    private val PLAYBACK_POSITIONS = stringSetPreferencesKey("playback_positions")
    private val DUBBER_SOURCES = stringSetPreferencesKey("dubber_sources")
    private val API_ENDPOINT = stringPreferencesKey("api_endpoint")

    /** 0 = системная, 1 = светлая, 2 = тёмная */
    val theme: Flow<Int> = context.settingsDataStore.data.map { it[THEME] ?: 0 }

    /** 0 = авто, 1080, 720, 480, 360 */
    val defaultQuality: Flow<Int> = context.settingsDataStore.data.map { it[DEFAULT_QUALITY] ?: 0 }

    /** Только горизонтальная ориентация плеера */
    val orientationLock: Flow<Boolean> = context.settingsDataStore.data.map { it[ORIENTATION_LOCK] ?: false }

    /** Автовоспроизведение следующей серии */
    val autoPlay: Flow<Boolean> = context.settingsDataStore.data.map { it[AUTO_PLAY] ?: false }

    /** 0 = сетка, 1 = список */
    val viewType: Flow<Int> = context.settingsDataStore.data.map { it[VIEW_TYPE] ?: 0 }

    /** Индекс скорости воспроизведения в SPEED_OPTIONS (по умолчанию 3 = 1.0x) */
    val playbackSpeed: Flow<Int> = context.settingsDataStore.data.map { it[PLAYBACK_SPEED] ?: 3 }

    /** Шаг перемотки в секундах */
    val rewindTime: Flow<Int> = context.settingsDataStore.data.map { it[REWIND_TIME] ?: 10 }

    /** Выбранный хост API Anixart */
    val apiEndpoint: Flow<String> = context.settingsDataStore.data.map { it[API_ENDPOINT] ?: DEFAULT_API_ENDPOINT }

    suspend fun setTheme(value: Int) {
        context.settingsDataStore.edit { it[THEME] = value }
    }

    suspend fun setDefaultQuality(value: Int) {
        context.settingsDataStore.edit { it[DEFAULT_QUALITY] = value }
    }

    suspend fun setOrientationLock(value: Boolean) {
        context.settingsDataStore.edit { it[ORIENTATION_LOCK] = value }
    }

    suspend fun setAutoPlay(value: Boolean) {
        context.settingsDataStore.edit { it[AUTO_PLAY] = value }
    }

    suspend fun setViewType(value: Int) {
        context.settingsDataStore.edit { it[VIEW_TYPE] = value }
    }

    suspend fun setPlaybackSpeed(value: Int) {
        context.settingsDataStore.edit { it[PLAYBACK_SPEED] = value }
    }

    suspend fun setRewindTime(value: Int) {
        context.settingsDataStore.edit { it[REWIND_TIME] = value }
    }

    suspend fun setApiEndpoint(value: String) {
        context.settingsDataStore.edit { it[API_ENDPOINT] = value }
    }

    suspend fun getPlaybackPosition(key: String): Long {
        val data = context.settingsDataStore.data.first()
        return data[PLAYBACK_POSITIONS]
            ?.firstOrNull { it.startsWith("$key=") }
            ?.substringAfter('=')
            ?.toLongOrNull()
            ?: 0L
    }

    suspend fun setPlaybackPosition(key: String, ms: Long) {
        context.settingsDataStore.edit { prefs ->
            val entries = prefs[PLAYBACK_POSITIONS]?.toMutableSet() ?: mutableSetOf()
            entries.removeAll { it.startsWith("$key=") }
            entries.add("$key=$ms")
            prefs[PLAYBACK_POSITIONS] = entries
        }
    }

    suspend fun clearPlaybackPosition(key: String) {
        context.settingsDataStore.edit { prefs ->
            val entries = prefs[PLAYBACK_POSITIONS]?.toMutableSet() ?: return@edit
            entries.removeAll { it.startsWith("$key=") }
            prefs[PLAYBACK_POSITIONS] = entries
        }
    }

    suspend fun getDubberSource(releaseId: Int): Pair<Int?, Int?> {
        val data = context.settingsDataStore.data.first()
        val entries = data[DUBBER_SOURCES] ?: return null to null
        val dubber = entries.firstOrNull { it.startsWith("$releaseId:dubber=") }
            ?.substringAfter('=')?.toIntOrNull()
        val source = entries.firstOrNull { it.startsWith("$releaseId:source=") }
            ?.substringAfter('=')?.toIntOrNull()
        return dubber to source
    }

    suspend fun setDubberSource(releaseId: Int, dubberId: Int, sourceId: Int) {
        context.settingsDataStore.edit { prefs ->
            val entries = prefs[DUBBER_SOURCES]?.toMutableSet() ?: mutableSetOf()
            entries.removeAll { it.startsWith("$releaseId:") }
            entries.add("$releaseId:dubber=$dubberId")
            entries.add("$releaseId:source=$sourceId")
            prefs[DUBBER_SOURCES] = entries
        }
    }

    companion object {
        const val DEFAULT_API_ENDPOINT = "api-s.anixsekai.com"

        /** Доступные эндпоинты API Anixart (host -> подпись) */
        val API_ENDPOINTS = listOf(
            "api-s.anixsekai.com" to "api-s.anixsekai.com (основной)",
            "api.anixart.app" to "api.anixart.app",
            "api.anixart.tv" to "api.anixart.tv (заблокирован в РФ)",
            "api.anixsekai.com" to "api.anixsekai.com",
            "baproxy-demo.ds1nc.ru" to "baproxy-demo.ds1nc.ru (прокси)",
        )

        fun apiBaseUrl(host: String): String = "https://$host"
    }
}
