package com.example.fluxplay.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fluxplay.data.model.MediaItemEntity
import com.example.fluxplay.data.repository.MediaRepository
import com.example.fluxplay.util.MediaTitleFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class DiscoverUiState(
    val allStreams: List<MediaItemEntity> = emptyList(),
    val filteredStreams: List<MediaItemEntity> = emptyList(),
    val searchQuery: String = "",
    val selectedFilter: String = "all", // "all", "hls", "direct", "local", "iptv"
    val isLoading: Boolean = false,
    val importMessage: String? = null
)

class DiscoverViewModel(
    private val mediaRepository: MediaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscoverUiState())
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    private val httpClient = OkHttpClient.Builder().build()

    init {
        observeStreams()
    }

    private fun observeStreams() {
        viewModelScope.launch {
            mediaRepository.getAllStreams().collect { streams ->
                _uiState.value = _uiState.value.copy(allStreams = streams)
                applyFilterAndSearch()
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        applyFilterAndSearch()
    }

    fun onFilterChanged(filter: String) {
        _uiState.value = _uiState.value.copy(selectedFilter = filter)
        applyFilterAndSearch()
    }

    private fun applyFilterAndSearch() {
        val query = _uiState.value.searchQuery.trim().lowercase()
        val filter = _uiState.value.selectedFilter

        val filtered = _uiState.value.allStreams.filter { item ->
            val matchesQuery = query.isBlank() ||
                item.title.lowercase().contains(query) ||
                item.url.lowercase().contains(query) ||
                item.type.lowercase().contains(query)

            val matchesFilter = when (filter) {
                "hls" -> item.url.contains(".m3u8", ignoreCase = true) || item.type.contains("HLS", ignoreCase = true)
                "direct" -> item.type.contains("Direct", ignoreCase = true) || item.url.endsWith(".mp4", ignoreCase = true) || item.url.endsWith(".mkv", ignoreCase = true)
                "local" -> item.provider == "local" || item.url.startsWith("content://") || item.url.startsWith("file://")
                "iptv" -> item.provider == "playlist" || item.source == "Playlist"
                else -> true
            }

            matchesQuery && matchesFilter
        }
        _uiState.value = _uiState.value.copy(filteredStreams = filtered)
    }

    fun addStream(url: String, title: String) {
        if (url.isBlank()) return
        val trimmed = url.trim()
        val streamTitle = MediaTitleFormatter.extractCleanTitle(title, trimmed)
        val item = MediaItemEntity(
            url = trimmed,
            title = streamTitle,
            poster = "",
            year = "Stream",
            type = if (trimmed.contains(".m3u8")) "HLS Stream" else if (trimmed.contains(".mpd")) "DASH Stream" else "Direct Video",
            rating = "HD",
            source = "Saved Stream",
            provider = "custom",
            providerId = "custom_${System.currentTimeMillis()}",
            synopsis = "Saved stream: $streamTitle"
        )
        viewModelScope.launch {
            mediaRepository.saveOrUpdateMedia(item)
        }
    }

    fun importM3uUrl(m3uUrl: String) {
        if (m3uUrl.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, importMessage = null)
            try {
                val count = withContext(Dispatchers.IO) {
                    val request = Request.Builder().url(m3uUrl.trim()).build()
                    val response = httpClient.newCall(request).execute()
                    val body = response.body?.string() ?: ""
                    mediaRepository.importM3uPlaylist(body)
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    importMessage = "Successfully imported $count streams!"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    importMessage = "Import failed: ${e.localizedMessage ?: "Invalid URL"}"
                )
            }
        }
    }

    fun importM3uText(content: String) {
        if (content.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, importMessage = null)
            val count = mediaRepository.importM3uPlaylist(content)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                importMessage = "Successfully imported $count streams!"
            )
        }
    }

    fun deleteStream(item: MediaItemEntity) {
        viewModelScope.launch {
            mediaRepository.deleteMedia(item.url)
        }
    }

    fun toggleBookmark(item: MediaItemEntity) {
        viewModelScope.launch {
            mediaRepository.toggleBookmark(item)
        }
    }

    fun clearImportMessage() {
        _uiState.value = _uiState.value.copy(importMessage = null)
    }
}
