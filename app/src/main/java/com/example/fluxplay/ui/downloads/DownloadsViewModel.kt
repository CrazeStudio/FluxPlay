package com.example.fluxplay.ui.downloads

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.fluxplay.data.download.DownloadRepository
import com.example.fluxplay.data.model.DownloadItemEntity
import com.example.fluxplay.data.model.MediaItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DownloadsViewModel(
    application: Application,
    private val downloadRepository: DownloadRepository
) : AndroidViewModel(application) {

    val allDownloads: StateFlow<List<DownloadItemEntity>> = downloadRepository.allDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredDownloads: StateFlow<List<DownloadItemEntity>> = combine(allDownloads, _searchQuery) { list, query ->
        if (query.isBlank()) {
            list
        } else {
            val q = query.trim().lowercase()
            list.filter { it.title.lowercase().contains(q) || it.url.lowercase().contains(q) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _totalStorageFormatted = MutableStateFlow("0 MB")
    val totalStorageFormatted: StateFlow<String> = _totalStorageFormatted.asStateFlow()

    init {
        refreshStorage()
        viewModelScope.launch {
            allDownloads.collect {
                refreshStorage()
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun startDownload(url: String, title: String? = null, poster: String = "") {
        if (url.isBlank()) return
        downloadRepository.startDownload(url, title, poster)
        refreshStorage()
    }

    fun cancelDownload(id: String) {
        downloadRepository.cancelDownload(id)
        refreshStorage()
    }

    fun deleteDownload(item: DownloadItemEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                downloadRepository.deleteDownload(item)
            }
            refreshStorage()
        }
    }

    fun deleteAllDownloads() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                downloadRepository.deleteAllDownloads()
            }
            refreshStorage()
        }
    }

    fun refreshStorage() {
        viewModelScope.launch {
            val totalBytes = withContext(Dispatchers.IO) {
                downloadRepository.getTotalDownloadsSizeBytes()
            }
            _totalStorageFormatted.value = downloadRepository.formatSize(totalBytes)
        }
    }

    fun mapToMediaItem(download: DownloadItemEntity): MediaItemEntity {
        return MediaItemEntity(
            url = download.filePath,
            title = download.title,
            poster = download.poster,
            year = "Offline",
            type = "Offline Download",
            source = "Downloaded File",
            provider = "local",
            providerId = "download_${download.id}",
            synopsis = "Downloaded offline video (${downloadRepository.formatSize(download.totalBytes)})"
        )
    }
}
