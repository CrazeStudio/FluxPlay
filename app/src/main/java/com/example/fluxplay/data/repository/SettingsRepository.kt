package com.example.fluxplay.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.fluxplay.data.model.AppThemeMode
import com.example.fluxplay.data.model.PlayerEngine
import com.example.fluxplay.data.model.PlayerSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("fluxplay_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<PlayerSettings> = _settings.asStateFlow()

    private fun loadSettings(): PlayerSettings {
        val engineId = prefs.getString("selected_engine", PlayerEngine.EXOPLAYER.id) ?: PlayerEngine.EXOPLAYER.id
        val engine = PlayerEngine.values().find { it.id == engineId } ?: PlayerEngine.EXOPLAYER
        val themeId = prefs.getString("selected_theme", AppThemeMode.AMOLED_MIDNIGHT.id) ?: AppThemeMode.AMOLED_MIDNIGHT.id
        val theme = AppThemeMode.values().find { it.id == themeId } ?: AppThemeMode.AMOLED_MIDNIGHT
        val backgroundPlay = prefs.getBoolean("background_play", true)
        val notifications = prefs.getBoolean("media_notifications", true)
        val hwAccel = prefs.getBoolean("hardware_accel", true)
        val autoResume = prefs.getBoolean("auto_resume", true)
        val audioTrack = prefs.getString("default_audio_track", "Default") ?: "Default"
        val subtitleTrack = prefs.getString("default_subtitle_track", "Auto") ?: "Auto"
        val bufferMb = prefs.getInt("buffer_size_mb", 32)
        val aspectMode = prefs.getString("video_aspect_mode", "Fit Screen") ?: "Fit Screen"
        val showControls = prefs.getBoolean("show_controls_overlay", true)

        return PlayerSettings(
            selectedEngine = engine,
            selectedTheme = theme,
            backgroundPlayEnabled = backgroundPlay,
            notificationsEnabled = notifications,
            hardwareAcceleration = hwAccel,
            autoResume = autoResume,
            defaultAudioTrack = audioTrack,
            defaultSubtitleTrack = subtitleTrack,
            bufferSizeMb = bufferMb,
            videoAspectMode = aspectMode,
            showMediaControlsOverlay = showControls
        )
    }

    fun updateEngine(engine: PlayerEngine) {
        prefs.edit().putString("selected_engine", engine.id).apply()
        _settings.value = _settings.value.copy(selectedEngine = engine)
    }

    fun updateTheme(theme: AppThemeMode) {
        prefs.edit().putString("selected_theme", theme.id).apply()
        _settings.value = _settings.value.copy(selectedTheme = theme)
    }

    fun updateBackgroundPlay(enabled: Boolean) {
        prefs.edit().putBoolean("background_play", enabled).apply()
        _settings.value = _settings.value.copy(backgroundPlayEnabled = enabled)
    }

    fun updateNotifications(enabled: Boolean) {
        prefs.edit().putBoolean("media_notifications", enabled).apply()
        _settings.value = _settings.value.copy(notificationsEnabled = enabled)
    }

    fun updateHardwareAcceleration(enabled: Boolean) {
        prefs.edit().putBoolean("hardware_accel", enabled).apply()
        _settings.value = _settings.value.copy(hardwareAcceleration = enabled)
    }

    fun updateAutoResume(enabled: Boolean) {
        prefs.edit().putBoolean("auto_resume", enabled).apply()
        _settings.value = _settings.value.copy(autoResume = enabled)
    }

    fun updateAspectMode(mode: String) {
        prefs.edit().putString("video_aspect_mode", mode).apply()
        _settings.value = _settings.value.copy(videoAspectMode = mode)
    }

    fun updateBufferSize(mb: Int) {
        prefs.edit().putInt("buffer_size_mb", mb).apply()
        _settings.value = _settings.value.copy(bufferSizeMb = mb)
    }
}
