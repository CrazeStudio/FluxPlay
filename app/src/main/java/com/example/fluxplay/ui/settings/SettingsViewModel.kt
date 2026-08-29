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
import com.example.fluxplay.data.download.DownloadRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class SettingsViewModel(
    application: Application,
    private val settingsRepository: SettingsRepository,
    private val mediaRepository: MediaRepository,
    private val downloadRepository: DownloadRepository
) : AndroidViewModel(application) {
    val settings: StateFlow<PlayerSettings> = settingsRepository.settings

    private val _cacheSizeFormatted = MutableStateFlow("0 MB")
    val cacheSizeFormatted: StateFlow<String> = _cacheSizeFormatted.asStateFlow()

    private val _downloadsSizeFormatted = MutableStateFlow("0 MB")
    val downloadsSizeFormatted: StateFlow<String> = _downloadsSizeFormatted.asStateFlow()

    private val _isCleaningCache = MutableStateFlow(false)
    val isCleaningCache: StateFlow<Boolean> = _isCleaningCache.asStateFlow()

    private val _cacheCleanMessage = MutableStateFlow<String?>(null)
    val cacheCleanMessage: StateFlow<String?> = _cacheCleanMessage.asStateFlow()

    init {
        refreshCacheSize()
        refreshDownloadsSize()
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
            val cacheSize = withContext(Dispatchers.IO) {
                getDirSize(getApplication<Application>().cacheDir)
            }
            _cacheSizeFormatted.value = formatSize(cacheSize)
        }
    }

    fun refreshDownloadsSize() {
        viewModelScope.launch {
            val downloadBytes = withContext(Dispatchers.IO) {
                downloadRepository.getTotalDownloadsSizeBytes()
            }
            _downloadsSizeFormatted.value = downloadRepository.formatSize(downloadBytes)
        }
    }

    fun clearAllDownloads(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                downloadRepository.deleteAllDownloads()
            }
            refreshDownloadsSize()
            onComplete()
        }
    }

    fun cleanCache(onComplete: (String) -> Unit) {
        viewModelScope.launch {
            _isCleaningCache.value = true
            _cacheCleanMessage.value = null
            
            val freed = withContext(Dispatchers.IO) {
                val cacheDir = getApplication<Application>().cacheDir
                val before = getDirSize(cacheDir)
                deleteDirContents(cacheDir)
                val after = getDirSize(cacheDir)
                formatSize((before - after).coerceAtLeast(0L))
            }
            
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
        viewModelScope.launch {
            mediaRepository.clearHistory()
        }
    }

    fun clearBookmarks() {
        viewModelScope.launch {
            mediaRepository.clearBookmarks()
        }
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

    private fun deleteDirContents(dir: java.io.File?) {
        if (dir != null && dir.isDirectory) {
            val files = dir.listFiles()
            if (files != null) {
                for (file in files) {
                    if (file.isDirectory) {
                        deleteDirContents(file)
                        file.delete()
                    } else {
                        file.delete()
                    }
                }
            }
        }
    }

    private fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}
