package com.example.fluxplay.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.fluxplay.data.model.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("fluxplay_settings", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun loadSettings(): AppSettings {
        return AppSettings(
            theme = prefs.getString(KEY_THEME, "dark") ?: "dark",
            primaryColorHex = prefs.getString(KEY_PRIMARY_COLOR, "#A78BFA") ?: "#A78BFA",
            accentColorHex = prefs.getString(KEY_ACCENT_COLOR, "#F43F5E") ?: "#F43F5E",
            tmdbKey = prefs.getString(KEY_TMDB_KEY, "") ?: "",
            lbxUsername = prefs.getString(KEY_LBX_USERNAME, "") ?: "",
            lbxClientId = prefs.getString(KEY_LBX_CLIENT_ID, "") ?: "",
            lbxClientSecret = prefs.getString(KEY_LBX_CLIENT_SECRET, "") ?: ""
        )
    }

    fun updateSettings(newSettings: AppSettings) {
        prefs.edit()
            .putString(KEY_THEME, newSettings.theme)
            .putString(KEY_PRIMARY_COLOR, newSettings.primaryColorHex)
            .putString(KEY_ACCENT_COLOR, newSettings.accentColorHex)
            .putString(KEY_TMDB_KEY, newSettings.tmdbKey)
            .putString(KEY_LBX_USERNAME, newSettings.lbxUsername)
            .putString(KEY_LBX_CLIENT_ID, newSettings.lbxClientId)
            .putString(KEY_LBX_CLIENT_SECRET, newSettings.lbxClientSecret)
            .apply()
        _settings.value = newSettings
    }

    fun updateTheme(theme: String) {
        updateSettings(_settings.value.copy(theme = theme))
    }

    fun updatePrimaryColor(colorHex: String) {
        updateSettings(_settings.value.copy(primaryColorHex = colorHex))
    }

    fun updateAccentColor(colorHex: String) {
        updateSettings(_settings.value.copy(accentColorHex = colorHex))
    }

    fun updateTmdbKey(key: String) {
        updateSettings(_settings.value.copy(tmdbKey = key.trim()))
    }

    fun updateLetterboxdConfig(username: String, clientId: String, clientSecret: String) {
        updateSettings(
            _settings.value.copy(
                lbxUsername = username.trim(),
                lbxClientId = clientId.trim(),
                lbxClientSecret = clientSecret.trim()
            )
        )
    }

    companion object {
        private const val KEY_THEME = "theme"
        private const val KEY_PRIMARY_COLOR = "primary_color"
        private const val KEY_ACCENT_COLOR = "accent_color"
        private const val KEY_TMDB_KEY = "tmdb_key"
        private const val KEY_LBX_USERNAME = "lbx_username"
        private const val KEY_LBX_CLIENT_ID = "lbx_client_id"
        private const val KEY_LBX_CLIENT_SECRET = "lbx_client_secret"
    }
}
