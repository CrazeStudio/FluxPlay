package com.example.fluxplay.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fluxplay.data.model.MediaItemEntity
import com.example.fluxplay.data.model.MediaType
import com.example.fluxplay.data.repository.MediaRepository
import com.example.fluxplay.data.repository.MetadataRepository
import com.example.fluxplay.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class DiscoverUiState(
    val streamList: List<MediaItemEntity> = emptyList(),
    val categories: List<String> = listOf("All"),
    val selectedCategory: String = "All",
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val customUrlInput: String = "",
    val errorMessage: String? = null
)

class DiscoverViewModel(
    private val metadataRepository: MetadataRepository,
    private val mediaRepository: MediaRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscoverUiState())
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    init {
        loadInitialStreams()
    }

    private fun loadInitialStreams() {
        val samples = metadataRepository.defaultSampleStreams
        val cats = listOf("All") + samples.mapNotNull { it.groupTitle }.distinct()
        _uiState.update {
            it.copy(
                streamList = samples,
                categories = cats
            )
        }
    }

    fun selectCategory(category: String) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onCustomUrlChanged(url: String) {
        _uiState.update { it.copy(customUrlInput = url) }
    }

    fun importM3uPlaylist(url: String) {
        if (url.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val parsed = metadataRepository.parseM3uPlaylist(url)
            if (parsed.isNotEmpty()) {
                val combined = (_uiState.value.streamList + parsed).distinctBy { it.uri }
                val cats = listOf("All") + combined.mapNotNull { it.groupTitle }.distinct()
                _uiState.update {
                    it.copy(
                        streamList = combined,
                        categories = cats,
                        isLoading = false,
                        customUrlInput = ""
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Could not parse playlist. Please verify the URL."
                    )
                }
            }
        }
    }

    fun playDirectUrl(url: String, onPlay: (MediaItemEntity) -> Unit) {
        if (url.isBlank()) return
        val mediaType = when {
            url.endsWith(".m3u8", ignoreCase = true) -> MediaType.HLS_STREAM
            url.endsWith(".mpd", ignoreCase = true) -> MediaType.DASH_STREAM
            else -> MediaType.DIRECT_URL
        }
        val media = MediaItemEntity(
            id = UUID.randomUUID().toString(),
            title = "Custom Stream (${url.takeLast(25)})",
            uri = url,
            mediaType = mediaType,
            groupTitle = "Direct Stream"
        )
        onPlay(media)
    }

    fun toggleBookmark(media: MediaItemEntity) {
        viewModelScope.launch {
            mediaRepository.toggleBookmark(media)
        }
    }
}
