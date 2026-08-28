package com.example.fluxplay.data.repository

import com.example.fluxplay.data.db.MediaDao
import com.example.fluxplay.data.model.MediaItemEntity
import kotlinx.coroutines.flow.Flow

class MediaRepository(private val mediaDao: MediaDao) {
    val history: Flow<List<MediaItemEntity>> = mediaDao.getAllHistory()
    val bookmarks: Flow<List<MediaItemEntity>> = mediaDao.getAllBookmarks()

    suspend fun getMediaById(id: String): MediaItemEntity? = mediaDao.getMediaById(id)

    suspend fun saveMedia(media: MediaItemEntity) {
        mediaDao.insertOrUpdate(media)
    }

    suspend fun updatePosition(id: String, positionMs: Long) {
        mediaDao.updatePlaybackPosition(id, positionMs)
    }

    suspend fun toggleBookmark(media: MediaItemEntity) {
        val newBookmarkState = !media.isBookmark
        mediaDao.setBookmark(media.id, newBookmarkState)
    }

    suspend fun deleteMedia(media: MediaItemEntity) {
        mediaDao.delete(media)
    }

    suspend fun clearHistory() {
        mediaDao.clearHistory()
    }

    suspend fun clearBookmarks() {
        mediaDao.clearBookmarks()
    }
}
