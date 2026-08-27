package com.example.fluxplay.ui.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.fluxplay.data.model.AppThemeMode
import com.example.fluxplay.data.model.PlayerEngine
import com.example.fluxplay.data.model.PlayerSettings
import com.example.fluxplay.data.repository.SettingsRepository
import com.example.fluxplay.data.repository.MediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class SettingsViewModel(
    application: Application,
    private val settingsRepository: SettingsRepository,
    private val mediaRepository: MediaRepository
) : AndroidViewModel(application) {
    val settings: StateFlow<PlayerSettings> = settingsRepository.settings

    private val _cacheSizeFormatted = MutableStateFlow("0 MB")
    val cacheSizeFormatted: StateFlow<String> = _cacheSizeFormatted.asStateFlow()

    private val _isCleaningCache = MutableStateFlow(false)
    val isCleaningCache: StateFlow<Boolean> = _isCleaningCache.asStateFlow()

    private val _cacheCleanMessage = MutableStateFlow<String?>(null)
    val cacheCleanMessage: StateFlow<String?> = _cacheCleanMessage.asStateFlow()

    init {
        refreshCacheSize()
    }

    fun setTheme(theme: AppThemeMode) {
        viewModelScope.launch {
            settingsRepository.updateTheme(theme)
        }
    }

    fun setEngine(engine: PlayerEngine) {
        viewModelScope.launch {
            settingsRepository.updateEngine(engine)
        }
    }

    fun setBackgroundPlay(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateBackgroundPlay(enabled)
        }
    }

    fun setNotifications(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateNotifications(enabled)
        }
    }

    fun setHardwareAcceleration(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateHardwareAcceleration(enabled)
        }
    }

    fun setAutoResume(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateAutoResume(enabled)
        }
    }

    fun refreshCacheSize() {
        viewModelScope.launch {
            val cacheSize = getDirSize(getApplication<Application>().cacheDir)
            _cacheSizeFormatted.value = formatSize(cacheSize)
        }
    }

    fun cleanCache(onComplete: (String) -> Unit) {
        viewModelScope.launch {
            _isCleaningCache.value = true
            _cacheCleanMessage.value = null
            
            val cacheDir = getApplication<Application>().cacheDir
            val before = getDirSize(cacheDir)
            deleteDir(cacheDir)
            val after = getDirSize(cacheDir)
            
            val freed = formatSize(before - after)
            
            delay(500) // fake delay for UI
            refreshCacheSize()
            
            _isCleaningCache.value = false
            _cacheCleanMessage.value = "Cache cleared successfully"
            onComplete(freed)
        }
    }

    fun dismissCacheMessage() {
        _cacheCleanMessage.value = null
    }

    fun clearHistory() {
        // Implementation for clearing history
    }

    fun clearBookmarks() {
        // Implementation for clearing bookmarks
    }

    private fun getDirSize(dir: java.io.File?): Long {
        var size: Long = 0
        if (dir != null && dir.isDirectory) {
            val files = dir.listFiles()
            if (files != null) {
                for (file in files) {
                    size += if (file.isFile) file.length() else getDirSize(file)
                }
            }
        } else if (dir != null && dir.isFile) {
            size = dir.length()
        }
        return size
    }

    private fun deleteDir(dir: java.io.File?): Boolean {
        if (dir != null && dir.isDirectory) {
            val children = dir.list()
            if (children != null) {
                for (i in children.indices) {
                    val success = deleteDir(java.io.File(dir, children[i]))
                    if (!success) {
                        return false
                    }
                }
            }
        }
        return dir?.delete() ?: false
    }

    private fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}
