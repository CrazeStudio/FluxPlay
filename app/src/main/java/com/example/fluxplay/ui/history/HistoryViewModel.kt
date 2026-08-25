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

    val historyItems: StateFlow<List<MediaItemEntity>> = mediaRepository.watchHistory
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val bookmarkItems: StateFlow<List<MediaItemEntity>> = mediaRepository.bookmarks
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun deleteItem(url: String) {
        viewModelScope.launch {
            mediaRepository.deleteMedia(url)
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
}
