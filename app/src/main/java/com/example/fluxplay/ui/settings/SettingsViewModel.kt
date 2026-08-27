package com.example.fluxplay.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.Coil
import com.example.fluxplay.data.model.AppThemeMode
import com.example.fluxplay.data.model.BackupData
import com.example.fluxplay.data.model.PlayerEngine
import com.example.fluxplay.data.model.PlayerSettings
import com.example.fluxplay.data.repository.MediaRepository
import com.example.fluxplay.data.repository.SettingsRepository
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SettingsViewModel(
    application: Application,
    private val settingsRepository: SettingsRepository,
    private val mediaRepository: MediaRepository
) : AndroidViewModel(application) {

    val settings: StateFlow<PlayerSettings> = settingsRepository.settings
    private val gson = Gson()

    private val _cacheSizeBytes = MutableStateFlow(0L)
    val cacheSizeBytes: StateFlow<Long> = _cacheSizeBytes.asStateFlow()

    private val _cacheSizeFormatted = MutableStateFlow("Calculating...")
    val cacheSizeFormatted: StateFlow<String> = _cacheSizeFormatted.asStateFlow()

    private val _isCleaningCache = MutableStateFlow(false)
    val isCleaningCache: StateFlow<Boolean> = _isCleaningCache.asStateFlow()

    private val _cacheCleanMessage = MutableStateFlow<String?>(null)
    val cacheCleanMessage: StateFlow<String?> = _cacheCleanMessage.asStateFlow()

    init {
        refreshCacheSize()
    }

    fun refreshCacheSize() {
        viewModelScope.launch(Dispatchers.IO) {
            val totalBytes = calculateTotalCacheBytes()
            _cacheSizeBytes.value = totalBytes
            _cacheSizeFormatted.value = formatBytes(totalBytes)
        }
    }

    private fun calculateTotalCacheBytes(): Long {
        val app = getApplication<Application>()
        var total = 0L
        try {
            app.cacheDir?.let { total += getDirSize(it) }
            app.codeCacheDir?.let { total += getDirSize(it) }
            app.externalCacheDir?.let { total += getDirSize(it) }
        } catch (_: Exception) {}
        return total
    }

    private fun getDirSize(dir: File): Long {
        var size = 0L
        try {
            val files = dir.listFiles() ?: return 0L
            for (file in files) {
                size += if (file.isDirectory) getDirSize(file) else file.length()
            }
        } catch (_: Exception) {}
        return size
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes.toDouble() / (1024 * 1024 * 1024))
            bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes.toDouble() / (1024 * 1024))
            bytes >= 1024 -> String.format("%.2f KB", bytes.toDouble() / 1024)
            else -> "$bytes B"
        }
    }

    fun cleanCache(onComplete: ((freedFormatted: String) -> Unit)? = null) {
        if (_isCleaningCache.value) return
        _isCleaningCache.value = true

        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val initialBytes = calculateTotalCacheBytes()

            try {
                // Clear Coil memory and disk caches
                try {
                    val imageLoader = Coil.imageLoader(app)
                    imageLoader.memoryCache?.clear()
                    imageLoader.diskCache?.clear()
                } catch (_: Exception) {}

                // Delete cache directory contents
                app.cacheDir?.listFiles()?.forEach { file ->
                    try {
                        if (file.isDirectory) file.deleteRecursively() else file.delete()
                    } catch (_: Exception) {}
                }

                app.externalCacheDir?.listFiles()?.forEach { file ->
                    try {
                        if (file.isDirectory) file.deleteRecursively() else file.delete()
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}

            val remainingBytes = calculateTotalCacheBytes()
            val freedBytes = (initialBytes - remainingBytes).coerceAtLeast(0L)
            val freedFormatted = formatBytes(if (freedBytes > 0) freedBytes else initialBytes)

            _cacheSizeBytes.value = remainingBytes
            _cacheSizeFormatted.value = formatBytes(remainingBytes)
            _isCleaningCache.value = false
            _cacheCleanMessage.value = "Successfully cleaned $freedFormatted of cache"

            withContext(Dispatchers.Main) {
                onComplete?.invoke(freedFormatted)
            }
        }
    }

    fun dismissCacheMessage() {
        _cacheCleanMessage.value = null
    }

    fun setEngine(engine: PlayerEngine) {
        settingsRepository.updateEngine(engine)
    }

    fun setTheme(theme: AppThemeMode) {
        settingsRepository.updateTheme(theme)
    }

    fun setBackgroundPlay(enabled: Boolean) {
        settingsRepository.updateBackgroundPlay(enabled)
    }

    fun setNotifications(enabled: Boolean) {
        settingsRepository.updateNotifications(enabled)
    }

    fun setHardwareAcceleration(enabled: Boolean) {
        settingsRepository.updateHardwareAcceleration(enabled)
    }

    fun setAutoResume(enabled: Boolean) {
        settingsRepository.updateAutoResume(enabled)
    }

    fun setAspectMode(mode: String) {
        settingsRepository.updateAspectMode(mode)
    }

    fun setBufferSize(mb: Int) {
        settingsRepository.updateBufferSize(mb)
    }

    suspend fun createBackupJson(): String {
        val history = mediaRepository.getWatchHistory().first()
        val bookmarks = mediaRepository.getBookmarks().first()
        val combined = (history + bookmarks).distinctBy { it.url }
        val backup = BackupData(items = combined)
        return gson.toJson(backup)
    }

    fun restoreBackup(json: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val backup = gson.fromJson(json, BackupData::class.java)
                if (backup?.items != null) {
                    for (item in backup.items) {
                        mediaRepository.saveOrUpdateMedia(item)
                    }
                    onComplete(true)
                } else {
                    onComplete(false)
                }
            } catch (e: Exception) {
                onComplete(false)
            }
        }
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

    fun clearAllData() {
        viewModelScope.launch {
            mediaRepository.clearAll()
        }
    }
}
