package com.example.fluxplay.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fluxplay.data.model.MediaItemEntity
import com.example.fluxplay.data.repository.MediaRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(private val mediaRepository: MediaRepository) : ViewModel() {

    val historyItems: StateFlow<List<MediaItemEntity>> = mediaRepository.history
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bookmarkItems: StateFlow<List<MediaItemEntity>> = mediaRepository.bookmarks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteItem(media: MediaItemEntity) {
        viewModelScope.launch {
            mediaRepository.deleteMedia(media)
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

    fun toggleBookmark(media: MediaItemEntity) {
        viewModelScope.launch {
            mediaRepository.toggleBookmark(media)
        }
    }
}
