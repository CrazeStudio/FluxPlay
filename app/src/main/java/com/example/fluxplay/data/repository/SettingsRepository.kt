package com.example.fluxplay.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.fluxplay.data.model.AppSettings
import com.example.fluxplay.data.model.PlayerEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("fluxplay_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun loadSettings(): AppSettings {
        val engineStr = prefs.getString("default_engine", PlayerEngine.EXOPLAYER.name) ?: PlayerEngine.EXOPLAYER.name
        val engine = try { PlayerEngine.valueOf(engineStr) } catch (e: Exception) { PlayerEngine.EXOPLAYER }

        return AppSettings(
            defaultEngine = engine,
            hardwareAcceleration = prefs.getBoolean("hw_accel", true),
            autoPlayNext = prefs.getBoolean("auto_play_next", true),
            backgroundPlay = prefs.getBoolean("bg_play", false),
            selectedTheme = prefs.getString("selected_theme", "Dark") ?: "Dark",
            defaultBufferMs = prefs.getInt("buffer_ms", 50000),
            rememberLastPosition = prefs.getBoolean("remember_pos", true),
            gestureBrightness = prefs.getBoolean("gesture_brightness", true),
            gestureVolume = prefs.getBoolean("gesture_volume", true),
            gestureSeek = prefs.getBoolean("gesture_seek", true),
            doubleTapSeekSeconds = prefs.getInt("double_tap_seek", 10),
            customM3uUrl = prefs.getString("custom_m3u_url", "") ?: ""
        )
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_settings.value)
        _settings.update { updated }
        prefs.edit().apply {
            putString("default_engine", updated.defaultEngine.name)
            putBoolean("hw_accel", updated.hardwareAcceleration)
            putBoolean("auto_play_next", updated.autoPlayNext)
            putBoolean("bg_play", updated.backgroundPlay)
            putString("selected_theme", updated.selectedTheme)
            putInt("buffer_ms", updated.defaultBufferMs)
            putBoolean("remember_pos", updated.rememberLastPosition)
            putBoolean("gesture_brightness", updated.gestureBrightness)
            putBoolean("gesture_volume", updated.gestureVolume)
            putBoolean("gesture_seek", updated.gestureSeek)
            putInt("double_tap_seek", updated.doubleTapSeekSeconds)
            putString("custom_m3u_url", updated.customM3uUrl)
            apply()
        }
    }
}
