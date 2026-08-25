package com.example.fluxplay.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fluxplay.data.model.AppSettings
import com.example.fluxplay.data.model.BackupData
import com.example.fluxplay.data.repository.MediaRepository
import com.example.fluxplay.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val mediaRepository: MediaRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppSettings()
        )

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun updateTheme(theme: String) {
        settingsRepository.updateTheme(theme)
    }

    fun updatePrimaryColor(colorHex: String) {
        settingsRepository.updatePrimaryColor(colorHex)
    }

    fun updateAccentColor(colorHex: String) {
        settingsRepository.updateAccentColor(colorHex)
    }

    fun updateTmdbKey(key: String) {
        settingsRepository.updateTmdbKey(key)
    }

    fun updateLetterboxd(username: String, clientId: String, clientSecret: String) {
        settingsRepository.updateLetterboxdConfig(username, clientId, clientSecret)
    }

    suspend fun createBackupJson(): String = withContext(Dispatchers.IO) {
        val history = mediaRepository.watchHistory.first()
        val bookmarks = mediaRepository.bookmarks.first()
        val currentSettings = settingsRepository.settings.value

        val backup = BackupData(
            history = history,
            bookmarks = bookmarks,
            settings = currentSettings
        )
        json.encodeToString(backup)
    }

    fun restoreBackup(context: Context, uri: Uri, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri) ?: throw Exception("Cannot open file")
                val reader = BufferedReader(InputStreamReader(inputStream))
                val content = reader.readText()
                reader.close()

                val backup = json.decodeFromString<BackupData>(content)
                
                // Restore settings
                settingsRepository.updateSettings(backup.settings)

                // Restore database
                val allItems = (backup.history + backup.bookmarks).distinctBy { it.url }
                if (allItems.isNotEmpty()) {
                    mediaRepository.insertAll(allItems)
                }

                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e.localizedMessage ?: "Failed to restore backup")
                }
            }
        }
    }

    fun clearAllData(onComplete: () -> Unit) {
        viewModelScope.launch {
            mediaRepository.clearAll()
            settingsRepository.updateSettings(AppSettings())
            onComplete()
        }
    }
}
