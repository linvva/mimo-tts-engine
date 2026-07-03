package io.github.linvva.mimottsengine.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "mimo_tts_settings")

class SettingsRepository(context: Context) {
    private val dataStore = context.applicationContext.settingsDataStore

    val settings: Flow<TtsSettings> = dataStore.data.map { preferences ->
        val savedVoice = preferences[Keys.VOICE]
        val voice = VoicePresets.firstOrNull { it.id == savedVoice }?.id ?: VoicePresets.first().id

        TtsSettings(
            apiKey = preferences[Keys.API_KEY].orEmpty(),
            voice = voice,
            speed = preferences[Keys.SPEED] ?: 1.0f,
            stylePrompt = preferences[Keys.STYLE_PROMPT] ?: DEFAULT_STYLE_PROMPT,
        )
    }

    suspend fun updateApiKey(value: String) {
        dataStore.edit { it[Keys.API_KEY] = value.trim() }
    }

    suspend fun updateVoice(value: String) {
        dataStore.edit { it[Keys.VOICE] = value }
    }

    suspend fun updateSpeed(value: Float) {
        dataStore.edit { it[Keys.SPEED] = value.coerceIn(0.6f, 1.4f) }
    }

    suspend fun updateStylePrompt(value: String) {
        dataStore.edit { it[Keys.STYLE_PROMPT] = value }
    }

    private object Keys {
        val API_KEY = stringPreferencesKey("api_key")
        val VOICE = stringPreferencesKey("voice")
        val SPEED = floatPreferencesKey("speed")
        val STYLE_PROMPT = stringPreferencesKey("style_prompt")
    }
}
