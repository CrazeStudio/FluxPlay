package com.example.fluxplay.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fluxplay.data.model.DiscoverItem
import com.example.fluxplay.data.model.DiscoverSection
import com.example.fluxplay.data.model.MediaItemEntity
import com.example.fluxplay.data.repository.MediaRepository
import com.example.fluxplay.data.repository.MetadataRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DiscoverUiState(
    val homeSections: List<DiscoverSection> = emptyList(),
    val isLoadingHome: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<DiscoverItem> = emptyList(),
    val isSearching: Boolean = false,
    val searchMovies: Boolean = true,
    val searchAnime: Boolean = true,
    val searchTv: Boolean = true,
    val searchLetterboxd: Boolean = true,
    val selectedItem: DiscoverItem? = null,
    val isLoadingDetails: Boolean = false,
    val isSelectedBookmarked: Boolean = false,
    val error: String? = null
)

class DiscoverViewModel(
    private val metadataRepository: MetadataRepository,
    private val mediaRepository: MediaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscoverUiState())
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    init {
        loadHomeFeed()
    }

    fun loadHomeFeed() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingHome = true, error = null)
            try {
                val sections = metadataRepository.getHomeSections()
                _uiState.value = _uiState.value.copy(
                    homeSections = sections,
                    isLoadingHome = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingHome = false,
                    error = "Failed to load home feed: ${e.message}"
                )
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(searchResults = emptyList())
        }
    }

    fun toggleFilter(filter: String) {
        when (filter) {
            "movies" -> _uiState.value = _uiState.value.copy(searchMovies = !_uiState.value.searchMovies)
            "anime" -> _uiState.value = _uiState.value.copy(searchAnime = !_uiState.value.searchAnime)
            "tv" -> _uiState.value = _uiState.value.copy(searchTv = !_uiState.value.searchTv)
            "letterboxd" -> _uiState.value = _uiState.value.copy(searchLetterboxd = !_uiState.value.searchLetterboxd)
        }
    }

    fun performSearch() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(searchResults = emptyList())
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearching = true, error = null)
            try {
                val results = metadataRepository.searchAll(
                    query = query,
                    searchMovies = _uiState.value.searchMovies,
                    searchAnime = _uiState.value.searchAnime,
                    searchTv = _uiState.value.searchTv,
                    searchLbx = _uiState.value.searchLetterboxd
                )
                _uiState.value = _uiState.value.copy(
                    searchResults = results,
                    isSearching = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    error = "Search failed: ${e.message}"
                )
            }
        }
    }

    fun selectItem(item: DiscoverItem) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                selectedItem = item,
                isLoadingDetails = true
            )

            // Check bookmark status
            val checkUrl = item.trailerUrl.ifBlank { item.sourceUrl.ifBlank { "${item.provider}:${item.id}" } }
            val existing = mediaRepository.getMediaDirect(checkUrl)
            _uiState.value = _uiState.value.copy(isSelectedBookmarked = existing?.isBookmarked == true)

            val fullDetails = metadataRepository.getItemDetails(item.provider, item.id) ?: item
            _uiState.value = _uiState.value.copy(
                selectedItem = fullDetails,
                isLoadingDetails = false
            )
        }
    }

    fun dismissDetailSheet() {
        _uiState.value = _uiState.value.copy(selectedItem = null)
    }

    fun toggleBookmarkSelected() {
        val item = _uiState.value.selectedItem ?: return
        viewModelScope.launch {
            val url = item.trailerUrl.ifBlank { item.sourceUrl.ifBlank { "${item.provider}:${item.id}" } }
            val entity = MediaItemEntity(
                url = url,
                title = item.title,
                poster = item.poster,
                year = item.year,
                type = item.type,
                rating = item.rating,
                source = item.source,
                provider = item.provider,
                providerId = item.id,
                synopsis = item.synopsis,
                duration = item.duration,
                genres = item.genres,
                cast = item.characters,
                studios = item.studios,
                sourceUrl = item.sourceUrl,
                trailerUrl = item.trailerUrl
            )
            mediaRepository.toggleBookmark(entity)
            val updated = mediaRepository.getMediaDirect(url)
            _uiState.value = _uiState.value.copy(isSelectedBookmarked = updated?.isBookmarked == true)
        }
    }
}
