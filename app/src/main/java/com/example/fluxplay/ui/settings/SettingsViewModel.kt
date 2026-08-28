package com.example.fluxplay.ui.settings

import androidx.lifecycle.ViewModel
import com.example.fluxplay.data.model.AppSettings
import com.example.fluxplay.data.model.PlayerEngine
import com.example.fluxplay.data.repository.SettingsRepository
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings

    fun setDefaultEngine(engine: PlayerEngine) {
        settingsRepository.updateSettings { it.copy(defaultEngine = engine) }
    }

    fun setHardwareAcceleration(enabled: Boolean) {
        settingsRepository.updateSettings { it.copy(hardwareAcceleration = enabled) }
    }

    fun setBackgroundPlay(enabled: Boolean) {
        settingsRepository.updateSettings { it.copy(backgroundPlay = enabled) }
    }

    fun setTheme(theme: String) {
        settingsRepository.updateSettings { it.copy(selectedTheme = theme) }
    }

    fun setRememberPosition(enabled: Boolean) {
        settingsRepository.updateSettings { it.copy(rememberLastPosition = enabled) }
    }

    fun setGestureBrightness(enabled: Boolean) {
        settingsRepository.updateSettings { it.copy(gestureBrightness = enabled) }
    }

    fun setGestureVolume(enabled: Boolean) {
        settingsRepository.updateSettings { it.copy(gestureVolume = enabled) }
    }

    fun setGestureSeek(enabled: Boolean) {
        settingsRepository.updateSettings { it.copy(gestureSeek = enabled) }
    }

    fun setDoubleTapSeekSeconds(seconds: Int) {
        settingsRepository.updateSettings { it.copy(doubleTapSeekSeconds = seconds) }
    }
}
